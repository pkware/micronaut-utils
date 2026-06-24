package com.pkware.micronaut.grpc.authorization

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.grpc.BindableService
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerServiceDefinition
import io.grpc.ServiceDescriptor
import io.grpc.Status
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Set

/**
 * Integration tests for [GrpcAuthorizationInterceptor]. A [Replaces] test resolver
 * builds a principal from an `x-subject` / `x-scopes` header pair, standing in for the
 * application's real header-trust resolver. The security-grpc-processor on the test annotation
 * processor classpath generates `@Executable` metadata for the `@RolesAllowed` test beans.
 */
@MicronautTest(startApplication = false)
@Property(name = "grpc.authorization.anonymous-services[0]", value = "grpc.health.v1.Health")
internal class GrpcAuthorizationInterceptorTest {
  @Inject
  lateinit var interceptor: GrpcAuthorizationInterceptor

  @Inject
  lateinit var meterRegistry: MeterRegistry

  @Test
  fun unregisteredMethodDeniedEvenWithoutCredentials() {
    val call = FakeServerCall(UNKNOWN_METHOD)
    interceptor.interceptCall<ByteArray?, ByteArray?>(call, Metadata(), FAILING_HANDLER)
    assertThat(call.closedStatus!!.code).isEqualTo(Status.Code.PERMISSION_DENIED)
    assertThat(call.closedStatus!!.description).isNotNull().contains("Method not allowed")
    assertCounter(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_METHOD_NOT_ALLOWED)
  }

  @Test
  fun unregisteredMethodDeniedEvenWithValidCredentials() {
    val call = FakeServerCall(UNKNOWN_METHOD)
    interceptor.interceptCall<ByteArray?, ByteArray?>(call, headers("svc", "deployment:worker"), FAILING_HANDLER)
    assertThat(call.closedStatus!!.code).isEqualTo(Status.Code.PERMISSION_DENIED)
    assertThat(call.closedStatus!!.description).isNotNull().contains("Method not allowed")
  }

  @Test
  fun anonymousServiceMethodPassesWithoutPrincipal() {
    val call = FakeServerCall(HEALTH_METHOD)
    val handler = CapturingCallHandler<ByteArray?, ByteArray?>()
    interceptor.interceptCall<ByteArray?, ByteArray?>(call, Metadata(), handler)
    assertThat(handler.wasCalled).isTrue()
    assertThat(call.closedStatus).isNull()
    assertThat(handler.capturedPrincipal, "anonymous method carries no principal").isNull()
  }

  @Test
  fun missingPrincipalOnRegisteredMethodIsUnauthenticated() {
    val call = FakeServerCall(SECURED_METHOD)
    interceptor.interceptCall<ByteArray?, ByteArray?>(call, Metadata(), FAILING_HANDLER)
    assertThat(call.closedStatus!!.code).isEqualTo(Status.Code.UNAUTHENTICATED)
    assertCounter(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_REJECTED)
  }

  @Test
  fun wrongScopeIsDenied() {
    val call = FakeServerCall(SECURED_METHOD)
    interceptor.interceptCall<ByteArray?, ByteArray?>(call, headers("svc", "wrong:scope"), FAILING_HANDLER)
    assertThat(call.closedStatus!!.code).isEqualTo(Status.Code.PERMISSION_DENIED)
    assertThat(call.closedStatus!!.description).isNotNull().contains("Insufficient scope")
    assertCounter(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_REJECTED)
  }

  @Test
  fun correctScopePassesAndPublishesPrincipal() {
    val call = FakeServerCall(SECURED_METHOD)
    val handler = CapturingCallHandler<ByteArray?, ByteArray?>()
    interceptor.interceptCall<ByteArray?, ByteArray?>(call, headers("svc", "deployment:worker"), handler)
    assertThat(handler.wasCalled).isTrue()
    assertThat(call.closedStatus).isNull()
    assertThat(handler.capturedPrincipal).isNotNull().prop(GrpcPrincipal::subject).isEqualTo("svc")
    assertCounter(GrpcAuthorizationMetrics.OUTCOME_SUCCESS, GrpcAuthorizationMetrics.REASON_NONE)
  }

  @Test
  fun resolveTimerRecorded() {
    val call = FakeServerCall(SECURED_METHOD)
    interceptor.interceptCall<ByteArray?, ByteArray?>(
      call,
      headers("svc", "deployment:worker"),
      CapturingCallHandler<ByteArray?, ByteArray?>(),
    )
    val timer = meterRegistry.find(GrpcAuthorizationMetrics.AUTHENTICATION_DURATION).timer()
    assertThat(timer).isNotNull().prop("count") { it.count() }.isGreaterThanOrEqualTo(1L)
  }

  private fun assertCounter(outcome: String, reason: String) {
    val counter = meterRegistry.find(GrpcAuthorizationMetrics.AUTH_ATTEMPTS)
      .tag(GrpcAuthorizationMetrics.TAG_OUTCOME, outcome)
      .tag(GrpcAuthorizationMetrics.TAG_REASON, reason).counter()
    assertThat(counter, "counter for outcome=$outcome reason=$reason")
      .isNotNull().prop("count") { it.count() }.isGreaterThanOrEqualTo(1.0)
  }

  // A header-based resolver standing in for the application's EnvoyPrincipalResolver.
  @Singleton
  @Replaces(GrpcPrincipalResolver::class)
  internal class HeaderResolver : GrpcPrincipalResolver {
    override fun resolve(headers: Metadata): GrpcPrincipal? {
      val subject = headers.get(SUBJECT_KEY)
      if (subject.isNullOrBlank()) return null
      val raw = headers.get(SCOPES_KEY)
      val scopes = if (raw.isNullOrBlank()) {
        mutableSetOf<String>()
      } else {
        Set.of<String>(*raw.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
      }
      return object : GrpcPrincipal {
        override val subject: String
          get() = subject
        override val scopes: MutableSet<String>
          get() = scopes
      }
    }
  }

  // anonymousServices configured via @MicronautTest properties below.
  @Singleton
  internal class WorkerService : BindableService {
    @RolesAllowed("deployment:worker")
    fun securedWork() {
      // No-op: bean carries @RolesAllowed metadata only.
    }

    override fun bindService(): ServerServiceDefinition {
      val secured = method("test.Worker", "securedWork")
      val descriptor = ServiceDescriptor.newBuilder("test.Worker").addMethod(secured).build()
      return ServerServiceDefinition.builder(descriptor)
        .addMethod(secured, UNSUPPORTED_HANDLER)
        .build()
    }
  }

  @Singleton
  internal class HealthService : BindableService {
    fun check() {
      // No-op: bean carries no @RolesAllowed; the service is anonymous by configuration.
    }

    override fun bindService(): ServerServiceDefinition {
      val check = method("grpc.health.v1.Health", "check")
      val descriptor = ServiceDescriptor.newBuilder("grpc.health.v1.Health").addMethod(check).build()
      return ServerServiceDefinition.builder(descriptor)
        .addMethod(check, UNSUPPORTED_HANDLER)
        .build()
    }
  }

  private class FakeServerCall<ReqT, RespT>(private val methodDescriptor: MethodDescriptor<ReqT?, RespT?>) :
    ServerCall<ReqT?, RespT?>() {
    var closedStatus: Status? = null

    override fun getMethodDescriptor(): MethodDescriptor<ReqT?, RespT?> = methodDescriptor

    override fun request(numMessages: Int) {}
    override fun sendHeaders(headers: Metadata) {}
    override fun sendMessage(message: RespT?) {}
    override fun isCancelled(): Boolean = false

    override fun close(status: Status, trailers: Metadata) {
      closedStatus = status
    }
  }

  private class CapturingCallHandler<ReqT, RespT> : ServerCallHandler<ReqT?, RespT?> {
    var wasCalled: Boolean = false
    var capturedPrincipal: GrpcPrincipal? = null
    override fun startCall(call: ServerCall<ReqT?, RespT?>, headers: Metadata): ServerCall.Listener<ReqT?> {
      wasCalled = true
      capturedPrincipal = principal()
      return object : ServerCall.Listener<ReqT?>() {}
    }
  }

  companion object {
    private val SUBJECT_KEY: Metadata.Key<String> =
      Metadata.Key.of<String>("x-subject", Metadata.ASCII_STRING_MARSHALLER)
    private val SCOPES_KEY: Metadata.Key<String> =
      Metadata.Key.of<String>("x-scopes", Metadata.ASCII_STRING_MARSHALLER)

    private val BYTES: MethodDescriptor.Marshaller<ByteArray> = object : MethodDescriptor.Marshaller<ByteArray> {
      override fun stream(value: ByteArray): InputStream = ByteArrayInputStream(value)

      override fun parse(stream: InputStream): ByteArray = ByteArray(0)
    }

    private val UNSUPPORTED_HANDLER =
      ServerCallHandler<ByteArray, ByteArray> { _, _ -> throw UnsupportedOperationException() }

    private val SECURED_METHOD = method("test.Worker", "securedWork")
    private val HEALTH_METHOD = method("grpc.health.v1.Health", "check")
    private val UNKNOWN_METHOD = method("unknown.Service", "Unknown")

    private val FAILING_HANDLER =
      ServerCallHandler { _: ServerCall<ByteArray, ByteArray>?, _: Metadata? ->
        throw AssertionError("handler should not be called")
      }

    private fun headers(subject: String, scopes: String): Metadata {
      val m = Metadata()
      m.put<String>(SUBJECT_KEY, subject)
      m.put<String>(SCOPES_KEY, scopes)
      return m
    }

    @Singleton
    fun testMeterRegistry(): SimpleMeterRegistry = SimpleMeterRegistry()

    private fun method(service: String, name: String): MethodDescriptor<ByteArray?, ByteArray?> =
      MethodDescriptor.newBuilder<ByteArray, ByteArray>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(MethodDescriptor.generateFullMethodName(service, name))
        .setRequestMarshaller(BYTES).setResponseMarshaller(BYTES).build()
  }
}
