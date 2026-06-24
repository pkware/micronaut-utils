package com.pkware.micronaut.grpc.authorization;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * gRPC {@link ServerInterceptor} that enforces per-method scope authorization from
 * {@code @RolesAllowed}, with identity supplied by an application {@link GrpcPrincipalResolver}.
 * No Micronaut Security, no reactive runtime, no {@code java.lang.reflect}.
 *
 * <p>Check order (matching {@code security-grpc}'s precedence, where "method not allowed" wins over
 * "unauthenticated"):
 * <ol>
 *   <li>Anonymous-allowlisted service → proceed without a principal.</li>
 *   <li>Method has no required scopes ({@link GrpcScopeRegistry} returns {@code null}) →
 *       {@link Status#PERMISSION_DENIED} "Method not allowed" (deny-by-default), regardless of credentials.</li>
 *   <li>No principal → {@link Status#UNAUTHENTICATED} "Authentication required".</li>
 *   <li>Principal lacks a required scope → {@link Status#PERMISSION_DENIED} "Insufficient scope".</li>
 *   <li>Otherwise → publish the principal in {@link GrpcCallerContext} and continue.</li>
 * </ol>
 *
 * <p>Ordered at {@link #ORDER}: gRPC {@link ServerInterceptor} beans are sorted by {@link Ordered},
 * outermost (lowest) first. The interceptor must run after any interceptor that injects identity
 * headers (those use a lower order) and before business interceptors.
 */
@Singleton
public final class GrpcAuthorizationInterceptor implements ServerInterceptor, Ordered {

  /**
   * Interceptor order. {@code 39000} is the position Micronaut reserves for the
   * authentication/authorization stage (its {@code ServerFilterPhase.SECURITY} sits at 39000, with a
   * 10000 gap between stages). Inlined as a plain {@link Ordered} value so this gRPC module carries no
   * {@code micronaut-http} dependency. Interceptors that must run first (e.g. a dev header injector)
   * use a lower order; interceptors that must run later use a higher one.
   */
  public static final int ORDER = 39000;

  @SuppressWarnings("rawtypes")
  private static final ServerCall.Listener NO_OP_LISTENER = new ServerCall.Listener<>() {};

  private final GrpcPrincipalResolver resolver;
  private final GrpcScopeRegistry registry;
  private final Set<String> anonymousServices;
  private final MeterRegistry meterRegistry;

  public GrpcAuthorizationInterceptor(
      GrpcPrincipalResolver resolver,
      GrpcScopeRegistry registry,
      GrpcAuthorizationConfiguration configuration,
      MeterRegistry meterRegistry) {
    this.resolver = Objects.requireNonNull(resolver);
    this.registry = Objects.requireNonNull(registry);
    List<String> anonymous = configuration.getAnonymousServices();
    this.anonymousServices = anonymous == null ? Set.of() : Set.copyOf(anonymous);
    this.meterRegistry = Objects.requireNonNull(meterRegistry);
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    String fullMethodName = call.getMethodDescriptor().getFullMethodName();

    if (anonymousServices.contains(serviceName(fullMethodName))) {
      recordAttempt(GrpcAuthorizationMetrics.OUTCOME_SUCCESS, GrpcAuthorizationMetrics.REASON_NONE);
      return next.startCall(call, headers);
    }

    Set<String> required = registry.requiredScopes(fullMethodName);
    if (required == null) {
      call.close(Status.PERMISSION_DENIED.withDescription("Method not allowed"), headers);
      recordAttempt(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_METHOD_NOT_ALLOWED);
      return (ServerCall.Listener<ReqT>) NO_OP_LISTENER;
    }

    GrpcPrincipal principal = resolve(headers);
    if (principal == null) {
      call.close(Status.UNAUTHENTICATED.withDescription("Authentication required"), headers);
      recordAttempt(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_REJECTED);
      return (ServerCall.Listener<ReqT>) NO_OP_LISTENER;
    }

    if (!principal.getScopes().containsAll(required)) {
      call.close(Status.PERMISSION_DENIED.withDescription("Insufficient scope"), headers);
      recordAttempt(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_REJECTED);
      return (ServerCall.Listener<ReqT>) NO_OP_LISTENER;
    }

    Context context = Context.current().withValue(GrpcCallerContext.PRINCIPAL, principal);
    recordAttempt(GrpcAuthorizationMetrics.OUTCOME_SUCCESS, GrpcAuthorizationMetrics.REASON_NONE);
    return Contexts.interceptCall(context, call, headers, next);
  }

  private @Nullable GrpcPrincipal resolve(Metadata headers) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      return resolver.resolve(headers);
    } finally {
      sample.stop(meterRegistry.timer(GrpcAuthorizationMetrics.AUTHENTICATION_DURATION));
    }
  }

  private void recordAttempt(String outcome, String reason) {
    meterRegistry.counter(
        GrpcAuthorizationMetrics.AUTH_ATTEMPTS,
        GrpcAuthorizationMetrics.TAG_OUTCOME, outcome,
        GrpcAuthorizationMetrics.TAG_REASON, reason).increment();
  }

  private static String serviceName(String fullMethodName) {
    int slash = fullMethodName.indexOf('/');
    return slash >= 0 ? fullMethodName.substring(0, slash) : fullMethodName;
  }
}
