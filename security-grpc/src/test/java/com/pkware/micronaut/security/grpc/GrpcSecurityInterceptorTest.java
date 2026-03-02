package com.pkware.micronaut.security.grpc;

import io.grpc.BindableService;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.server.util.locale.HttpLocaleResolver;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.token.validator.TokenValidator;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link GrpcSecurityInterceptor}.
 *
 * <p>Uses real Micronaut beans with {@code @Secured} annotations, Micronaut Security's
 * real {@code TokenAuthenticationFetcher} → {@code BearerTokenReader} → {@code TokenResolver}
 * pipeline, and a test {@link TokenValidator}. The companion {@code security-grpc-processor}
 * on the test annotation processor classpath generates the {@code ExecutableMethod}
 * metadata that {@link GrpcSecuredMethodRegistry} reads at startup.
 *
 * <p>Authorization is delegated to Micronaut Security's {@code SecurityRule} chain
 * (including {@code SecuredAnnotationRule}).
 */
@MicronautTest(startApplication = false)
@Property(name = "micronaut.security.grpc.unauthenticated-services[0]", value = "grpc.health.v1.Health")
class GrpcSecurityInterceptorTest {

  @Inject
  GrpcSecurityInterceptor interceptor;

  @Inject
  MeterRegistry meterRegistry;

  @Test
  void unauthenticatedServicePassesThrough() {
    var call = new FakeServerCall<>(HEALTH_CHECK_METHOD);
    var handler = new CapturingCallHandler<byte[], byte[]>();

    interceptor.interceptCall(call, new Metadata(), handler);

    assertTrue(handler.wasCalled, "Health check should pass through without auth");
    assertNull(call.closedStatus);
  }

  @Test
  void unregisteredMethodDenied() {
    var call = new FakeServerCall<>(UNKNOWN_METHOD);

    interceptor.interceptCall(call, new Metadata(), FAILING_HANDLER);

    assertEquals(Status.Code.PERMISSION_DENIED, call.closedStatus.getCode());
    assertTrue(call.closedStatus.getDescription().contains("Method not allowed"));
    assertCounter(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_METHOD_NOT_ALLOWED);
  }

  @Test
  void anonymousMethodPassesWithoutToken() {
    var call = new FakeServerCall<>(ANONYMOUS_METHOD);
    var handler = new CapturingCallHandler<byte[], byte[]>();

    interceptor.interceptCall(call, new Metadata(), handler);

    assertTrue(handler.wasCalled, "IS_ANONYMOUS method should pass through");
    assertNull(call.closedStatus);
  }

  @Test
  void missingAuthHeaderReturnsUnauthenticated() {
    var call = new FakeServerCall<>(SECURED_METHOD);

    interceptor.interceptCall(call, new Metadata(), FAILING_HANDLER);

    assertEquals(Status.Code.UNAUTHENTICATED, call.closedStatus.getCode());
    assertCounter(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_REJECTED);
  }

  @Test
  void invalidTokenReturnsUnauthenticated() {
    var call = new FakeServerCall<>(SECURED_METHOD);
    var headers = bearerHeaders("invalid-token");

    interceptor.interceptCall(call, headers, FAILING_HANDLER);

    assertEquals(Status.Code.UNAUTHENTICATED, call.closedStatus.getCode());
    assertCounter(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_REJECTED);
  }

  @Test
  void wrongScopeReturnsDenied() {
    var call = new FakeServerCall<>(SECURED_METHOD);
    var headers = bearerHeaders("valid-wrong-scope");

    interceptor.interceptCall(call, headers, FAILING_HANDLER);

    assertEquals(Status.Code.PERMISSION_DENIED, call.closedStatus.getCode());
    assertTrue(call.closedStatus.getDescription().contains("Insufficient scope"));
    assertCounter(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_REJECTED);
  }

  @Test
  void correctScopeSetsAuthenticationInContext() {
    var call = new FakeServerCall<>(SECURED_METHOD);
    var headers = bearerHeaders("valid-worker");
    var handler = new CapturingCallHandler<byte[], byte[]>();

    interceptor.interceptCall(call, headers, handler);

    assertTrue(handler.wasCalled, "Should pass through with correct scope");
    assertNull(call.closedStatus);
    assertNotNull(handler.capturedAuthentication, "Authentication should be in context");
    assertEquals("test-client", handler.capturedAuthentication.getName());
    assertCounter(GrpcSecurityMetrics.OUTCOME_SUCCESS, GrpcSecurityMetrics.REASON_NONE);
  }

  @Test
  void isAuthenticatedSkipsScopeCheck() {
    var call = new FakeServerCall<>(AUTHENTICATED_METHOD);
    var headers = bearerHeaders("valid-wrong-scope");
    var handler = new CapturingCallHandler<byte[], byte[]>();

    interceptor.interceptCall(call, headers, handler);

    assertTrue(handler.wasCalled, "IS_AUTHENTICATED should accept any valid token");
    assertNull(call.closedStatus);
  }

  @Test
  void authenticationTimerRecorded() {
    var call = new FakeServerCall<>(SECURED_METHOD);
    var headers = bearerHeaders("invalid-token");

    interceptor.interceptCall(call, headers, FAILING_HANDLER);

    var timer = meterRegistry.find(GrpcSecurityMetrics.AUTHENTICATION_DURATION).timer();
    assertNotNull(timer, "Authentication timer should be recorded");
    assertTrue(timer.count() >= 1, "Timer should have been recorded at least once");
    assertTrue(timer.totalTime(TimeUnit.NANOSECONDS) > 0);
  }

  private void assertCounter(String outcome, String reason) {
    var counter = meterRegistry.find(GrpcSecurityMetrics.AUTH_ATTEMPTS)
        .tag(GrpcSecurityMetrics.TAG_OUTCOME, outcome)
        .tag(GrpcSecurityMetrics.TAG_REASON, reason)
        .counter();
    assertNotNull(counter, "Counter should exist for outcome=" + outcome + " reason=" + reason);
    assertTrue(counter.count() >= 1.0, "Counter should have been incremented");
  }

  private static Metadata bearerHeaders(String token) {
    var headers = new Metadata();
    headers.put(AUTHORIZATION_KEY, "Bearer " + token);
    return headers;
  }

  /**
   * Accepts tokens starting with {@code "valid-"}. Returns {@code "data_center:worker"}
   * role for {@code "valid-worker"}, otherwise {@code "wrong:scope"}.
   *
   * <p>Micronaut auto-wires this into the real {@code TokenAuthenticationFetcher} →
   * {@code BearerTokenReader} → {@code TokenResolver} pipeline.
   */
  @Singleton
  static class TestTokenValidator implements TokenValidator<HttpRequest<?>> {
    @Override
    public Publisher<Authentication> validateToken(String token, @Nullable HttpRequest<?> request) {
      if (token.startsWith("valid-")) {
        List<String> roles = token.equals("valid-worker")
            ? List.of("data_center:worker")
            : List.of("wrong:scope");
        return Mono.just(Authentication.build("test-client", roles, Map.of()));
      }
      return Mono.empty();
    }
  }

  /** Provides a {@link SimpleMeterRegistry} for metrics recording in tests. */
  @Singleton
  static SimpleMeterRegistry testMeterRegistry() {
    return new SimpleMeterRegistry();
  }

  /** Stub so {@code TokenAuthenticationFetcher}'s {@code @Requires(beans = HttpHostResolver.class)} is satisfied. */
  @Singleton
  static HttpHostResolver testHttpHostResolver() {
    return request -> "localhost";
  }

  /** Stub so {@code TokenAuthenticationFetcher} can resolve locale for events. */
  @Singleton
  static HttpLocaleResolver testHttpLocaleResolver() {
    return new HttpLocaleResolver() {
      @Override
      public @NonNull Optional<Locale> resolve(@NonNull HttpRequest<?> context) {
        return Optional.of(Locale.getDefault());
      }

      @Override
      public @NonNull Locale resolveOrDefault(@NonNull HttpRequest<?> context) {
        return Locale.getDefault();
      }
    };
  }

  /**
   * gRPC service with a secured method, an anonymous method, and an authenticated method.
   *
   * <p>Method names must match the gRPC method names in the {@link ServiceDescriptor}.
   */
  @Singleton
  static class TestService implements BindableService {

    @Secured("data_center:worker")
    public void SecuredWork() {}

    @Secured(SecurityRule.IS_ANONYMOUS)
    public void AnonWork() {}

    @Secured(SecurityRule.IS_AUTHENTICATED)
    public void AuthenticatedWork() {}

    @Override
    public ServerServiceDefinition bindService() {
      var secured = method("test.Service", "SecuredWork");
      var anon = method("test.Service", "AnonWork");
      var authenticated = method("test.Service", "AuthenticatedWork");

      var descriptor = ServiceDescriptor.newBuilder("test.Service")
          .addMethod(secured)
          .addMethod(anon)
          .addMethod(authenticated)
          .build();

      return ServerServiceDefinition.builder(descriptor)
          .addMethod(secured, (call, headers) -> { throw new UnsupportedOperationException(); })
          .addMethod(anon, (call, headers) -> { throw new UnsupportedOperationException(); })
          .addMethod(authenticated, (call, headers) -> { throw new UnsupportedOperationException(); })
          .build();
    }
  }

  /** Records the {@link Status} passed to {@link ServerCall#close}. */
  private static class FakeServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {
    private final MethodDescriptor<ReqT, RespT> methodDescriptor;
    Status closedStatus;

    FakeServerCall(MethodDescriptor<ReqT, RespT> methodDescriptor) {
      this.methodDescriptor = methodDescriptor;
    }

    @Override public MethodDescriptor<ReqT, RespT> getMethodDescriptor() { return methodDescriptor; }
    @Override public void request(int numMessages) {}
    @Override public void sendHeaders(Metadata headers) {}
    @Override public void sendMessage(RespT message) {}
    @Override public boolean isCancelled() { return false; }
    @Override public void close(Status status, Metadata trailers) { closedStatus = status; }
  }

  /**
   * Captures whether the handler was called and reads the {@link Authentication}
   * from the gRPC {@link Context}.
   */
  private static class CapturingCallHandler<ReqT, RespT> implements ServerCallHandler<ReqT, RespT> {
    boolean wasCalled;
    Authentication capturedAuthentication;

    @Override
    public ServerCall.Listener<ReqT> startCall(ServerCall<ReqT, RespT> call, Metadata headers) {
      wasCalled = true;
      capturedAuthentication = GrpcSecurityContext.AUTHENTICATION.get();
      return new ServerCall.Listener<>() {};
    }
  }

  private static final MethodDescriptor.Marshaller<byte[]> BYTES_MARSHALLER =
      new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(byte[] value) { return new ByteArrayInputStream(value); }

        @Override
        public byte[] parse(InputStream stream) { return new byte[0]; }
      };

  private static MethodDescriptor<byte[], byte[]> method(String service, String methodName) {
    return MethodDescriptor.<byte[], byte[]>newBuilder()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(MethodDescriptor.generateFullMethodName(service, methodName))
        .setRequestMarshaller(BYTES_MARSHALLER)
        .setResponseMarshaller(BYTES_MARSHALLER)
        .build();
  }

  private static final Metadata.Key<String> AUTHORIZATION_KEY =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

  /** Method descriptor for the test service's secured method. */
  private static final MethodDescriptor<byte[], byte[]> SECURED_METHOD =
      method("test.Service", "SecuredWork");

  /** Method descriptor for the test service's anonymous method. */
  private static final MethodDescriptor<byte[], byte[]> ANONYMOUS_METHOD =
      method("test.Service", "AnonWork");

  /** Method descriptor for the test service's authenticated method. */
  private static final MethodDescriptor<byte[], byte[]> AUTHENTICATED_METHOD =
      method("test.Service", "AuthenticatedWork");

  /** Method descriptor for the health check service (configured as unauthenticated). */
  private static final MethodDescriptor<byte[], byte[]> HEALTH_CHECK_METHOD =
      method("grpc.health.v1.Health", "Check");

  /** Method descriptor for a service not in the registry. */
  private static final MethodDescriptor<byte[], byte[]> UNKNOWN_METHOD =
      method("unknown.Service", "Unknown");

  private static final ServerCallHandler<byte[], byte[]> FAILING_HANDLER =
      (call, headers) -> { throw new AssertionError("Handler should not be called"); };
}
