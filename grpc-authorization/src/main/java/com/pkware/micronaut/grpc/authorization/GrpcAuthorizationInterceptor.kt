package com.pkware.micronaut.grpc.authorization

import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micronaut.core.order.Ordered
import jakarta.inject.Singleton

/**
 * gRPC [ServerInterceptor] that enforces per-method scope authorization from
 * `@RolesAllowed`, with identity supplied by an application [GrpcPrincipalResolver].
 * No Micronaut Security, no reactive runtime, no `java.lang.reflect`.
 *
 * Check order (matching `security-grpc`'s precedence, where "method not allowed" wins over
 * "unauthenticated"):
 *
 *  1. Anonymous-allowlisted service → proceed without a principal.
 *  1. Method has no required scopes ([GrpcScopeRegistry] returns `null`) →
 * [Status.PERMISSION_DENIED] "Method not allowed" (deny-by-default), regardless of credentials.
 *  1. No principal → [Status.UNAUTHENTICATED] "Authentication required".
 *  1. Principal lacks a required scope → [Status.PERMISSION_DENIED] "Insufficient scope".
 *  1. Otherwise → publish the principal in [PRINCIPAL] and continue.
 *
 * Ordered at [.ORDER]: gRPC [ServerInterceptor] beans are sorted by [Ordered],
 * outermost (lowest) first. The interceptor must run after any interceptor that injects identity
 * headers (those use a lower order) and before business interceptors.
 */
@Singleton
public class GrpcAuthorizationInterceptor(
  private val resolver: GrpcPrincipalResolver,
  private val registry: GrpcScopeRegistry,
  configuration: GrpcAuthorizationConfiguration,
  private val meterRegistry: MeterRegistry,
) : ServerInterceptor,
  Ordered {
  private val anonymousServices: Set<String>

  init {
    val anonymous = configuration.anonymousServices
    this.anonymousServices = if (anonymous == null) emptySet() else java.util.Set.copyOf(anonymous)
  }

  override fun getOrder(): Int = ORDER

  override fun <ReqT, RespT> interceptCall(
    call: ServerCall<ReqT?, RespT?>,
    headers: Metadata,
    next: ServerCallHandler<ReqT?, RespT?>,
  ): ServerCall.Listener<ReqT?> {
    val fullMethodName = call.getMethodDescriptor().fullMethodName

    if (anonymousServices.contains(serviceName(fullMethodName))) {
      recordAttempt(GrpcAuthorizationMetrics.OUTCOME_SUCCESS, GrpcAuthorizationMetrics.REASON_NONE)
      return next.startCall(call, headers)
    }

    val required = registry.requiredScopes(fullMethodName)
    if (required == null) {
      call.close(Status.PERMISSION_DENIED.withDescription("Method not allowed"), headers)
      recordAttempt(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_METHOD_NOT_ALLOWED)
      return NO_OP_LISTENER as ServerCall.Listener<ReqT?>
    }

    val principal = resolve(headers)
    if (principal == null) {
      call.close(Status.UNAUTHENTICATED.withDescription("Authentication required"), headers)
      recordAttempt(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_REJECTED)
      return NO_OP_LISTENER as ServerCall.Listener<ReqT?>
    }

    if (!principal.scopes.containsAll(required)) {
      call.close(Status.PERMISSION_DENIED.withDescription("Insufficient scope"), headers)
      recordAttempt(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_REJECTED)
      return NO_OP_LISTENER as ServerCall.Listener<ReqT?>
    }

    val context = Context.current().withValue(PRINCIPAL, principal)
    recordAttempt(GrpcAuthorizationMetrics.OUTCOME_SUCCESS, GrpcAuthorizationMetrics.REASON_NONE)
    return Contexts.interceptCall<ReqT?, RespT?>(context, call, headers, next)
  }

  private fun resolve(headers: Metadata): GrpcPrincipal? {
    val sample = Timer.start(meterRegistry)
    try {
      return resolver.resolve(headers)
    } finally {
      sample.stop(meterRegistry.timer(GrpcAuthorizationMetrics.AUTHENTICATION_DURATION))
    }
  }

  private fun recordAttempt(outcome: String, reason: String) {
    meterRegistry.counter(
      GrpcAuthorizationMetrics.AUTH_ATTEMPTS,
      GrpcAuthorizationMetrics.TAG_OUTCOME,
      outcome,
      GrpcAuthorizationMetrics.TAG_REASON,
      reason,
    ).increment()
  }

  private companion object {
    /**
     * Interceptor order. `39000` is the position Micronaut reserves for the
     * authentication/authorization stage (its `ServerFilterPhase.SECURITY` sits at 39000, with a
     * 10000 gap between stages). Inlined as a plain [Ordered] value so this gRPC module carries no
     * `micronaut-http` dependency. Interceptors that must run first (e.g. a dev header injector)
     * use a lower order; interceptors that must run later use a higher one.
     */
    private const val ORDER: Int = 39000

    private val NO_OP_LISTENER: ServerCall.Listener<*> = object : ServerCall.Listener<Any?>() {}

    private fun serviceName(fullMethodName: String): String {
      val slash = fullMethodName.indexOf('/')
      return if (slash >= 0) fullMethodName.substring(0, slash) else fullMethodName
    }
  }
}
