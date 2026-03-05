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
import io.micronaut.core.order.Ordered;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
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
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * <p>The intercept-url-map tests use {@link UnconfiguredService}, which has no
 * {@code @Secured} annotations and represents a third-party service (like
 * {@code grpc.health.v1.Health}) that relies on configuration-driven access rules.
 *
 * <p>intercept-url-map patterns are configured at the class level (not per-test) because
 * {@code ConfigurationInterceptUrlMapRule} reads patterns at construction time and is not
 * {@code @Refreshable}. Using per-method patterns for different access rules: each method
 * name in {@link UnconfiguredService} corresponds to one access rule in the map.
 */
@MicronautTest(startApplication = false)
// These patterns cover the UnconfiguredService methods used in intercept-url-map tests.
// The pattern format is "/" + fullMethodName, matching ConfigurationInterceptUrlMapRule's
// path matching against the synthetic request URI that GrpcSecurityInterceptor builds.
@Property(name = "micronaut.security.intercept-url-map[0].pattern", value = "/unconfigured.Service/AnonMethod")
@Property(name = "micronaut.security.intercept-url-map[0].access[0]", value = "isAnonymous()")
@Property(name = "micronaut.security.intercept-url-map[1].pattern", value = "/unconfigured.Service/AuthMethod")
@Property(name = "micronaut.security.intercept-url-map[1].access[0]", value = "isAuthenticated()")
@Property(name = "micronaut.security.intercept-url-map[2].pattern", value = "/unconfigured.Service/RoleMethod")
@Property(name = "micronaut.security.intercept-url-map[2].access[0]", value = "data_center:worker")
class GrpcSecurityInterceptorTest {

  @Inject
  GrpcSecurityInterceptor interceptor;

  @Inject
  MeterRegistry meterRegistry;

  // ── @Secured annotation tests ─────────────────────────────────────────────

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

  // ── intercept-url-map tests ───────────────────────────────────────────────
  //
  // These tests verify that micronaut.security.intercept-url-map works for gRPC methods
  // that have no @Secured annotation — the common case for third-party services like
  // grpc.health.v1.Health. Each method in UnconfiguredService has a dedicated intercept-
  // url-map pattern (configured at the class level) with a different access rule.

  @Test
  void interceptUrlMapWithIsAnonymousAllowsUnauthenticatedCalls() {
    var call = new FakeServerCall<>(UNCONFIGURED_ANON_METHOD);
    var handler = new CapturingCallHandler<byte[], byte[]>();

    interceptor.interceptCall(call, new Metadata(), handler);

    // No credentials needed — the method is configured as publicly accessible.
    assertTrue(handler.wasCalled);
    assertNull(call.closedStatus);
  }

  @Test
  void interceptUrlMapWithIsAuthenticatedPassesWithValidToken() {
    var call = new FakeServerCall<>(UNCONFIGURED_AUTH_METHOD);
    var handler = new CapturingCallHandler<byte[], byte[]>();

    interceptor.interceptCall(call, bearerHeaders("valid-worker"), handler);

    // Any valid authentication is sufficient — the method requires login but no specific role.
    assertTrue(handler.wasCalled);
    assertNull(call.closedStatus);
  }

  @Test
  void interceptUrlMapWithIsAuthenticatedDeniesUnauthenticatedCalls() {
    var call = new FakeServerCall<>(UNCONFIGURED_AUTH_METHOD);

    interceptor.interceptCall(call, new Metadata(), FAILING_HANDLER);

    // The rule explicitly rejected the caller — status is UNAUTHENTICATED, not method_not_allowed.
    assertEquals(Status.Code.UNAUTHENTICATED, call.closedStatus.getCode());
    assertCounter(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_REJECTED);
  }

  @Test
  void interceptUrlMapWithSpecificRoleAllowsMatchingToken() {
    var call = new FakeServerCall<>(UNCONFIGURED_ROLE_METHOD);
    var handler = new CapturingCallHandler<byte[], byte[]>();

    interceptor.interceptCall(call, bearerHeaders("valid-worker"), handler);

    assertTrue(handler.wasCalled);
    assertNull(call.closedStatus);
  }

  @Test
  void interceptUrlMapWithSpecificRoleDeniesWrongRole() {
    var call = new FakeServerCall<>(UNCONFIGURED_ROLE_METHOD);

    interceptor.interceptCall(call, bearerHeaders("valid-wrong-scope"), FAILING_HANDLER);

    // The rule explicitly rejected the caller — status is PERMISSION_DENIED "Insufficient scope".
    assertEquals(Status.Code.PERMISSION_DENIED, call.closedStatus.getCode());
    assertTrue(call.closedStatus.getDescription().contains("Insufficient scope"));
    assertCounter(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_REJECTED);
  }

  @Test
  void unAnnotatedMethodWithoutInterceptUrlMapPatternDenied() {
    // UNKNOWN_METHOD has no @Secured and no intercept-url-map pattern.
    var call = new FakeServerCall<>(UNKNOWN_METHOD);

    interceptor.interceptCall(call, new Metadata(), FAILING_HANDLER);

    // No rule made an explicit decision — this is the deny-by-default path.
    assertEquals(Status.Code.PERMISSION_DENIED, call.closedStatus.getCode());
    assertTrue(call.closedStatus.getDescription().contains("Method not allowed"));
    assertCounter(GrpcSecurityMetrics.OUTCOME_FAILED, GrpcSecurityMetrics.REASON_METHOD_NOT_ALLOWED);
  }

  // ── Synthetic HttpRequest tests ──────────────────────────────────────────
  //
  // These tests verify properties of the synthetic HttpRequest that
  // GrpcSecurityInterceptor builds from gRPC Metadata. The
  // RequestCapturingFetcher bean captures the request so we can inspect it.

  @Inject
  RequestCapturingRule requestCapturingRule;

  @Test
  void syntheticRequestSupportsCookieAccess() {
    // Reproduces the production bug: CookieTokenReader calls getCookies() on the
    // synthetic request. If the HttpRequest implementation doesn't support cookies,
    // this would throw UnsupportedOperationException inside the auth pipeline.
    var call = new FakeServerCall<>(SECURED_METHOD);
    var headers = bearerHeaders("valid-worker");
    var handler = new CapturingCallHandler<byte[], byte[]>();

    interceptor.interceptCall(call, headers, handler);

    // The RequestCapturingRule called getCookies() without throwing.
    assertTrue(handler.wasCalled, "Auth should succeed despite cookie access in pipeline");
    assertNull(call.closedStatus);
    var capture = requestCapturingRule.findByMethod("test.Service/SecuredWork");
    assertNotNull(capture, "RequestCapturingRule should have been invoked");
    assertTrue(capture.cookiesAccessSucceeded(),
        "getCookies() should not throw on the synthetic request (class: "
            + capture.request().getClass().getName() + ")");
  }

  @Test
  void syntheticRequestExcludesBinaryHeaders() {
    var call = new FakeServerCall<>(SECURED_METHOD);
    var headers = bearerHeaders("valid-worker");
    // gRPC binary headers end with "-bin" and carry non-ASCII data.
    headers.put(
        Metadata.Key.of("custom-data-bin", Metadata.BINARY_BYTE_MARSHALLER),
        new byte[]{1, 2, 3});
    headers.put(
        Metadata.Key.of("x-custom-text", Metadata.ASCII_STRING_MARSHALLER),
        "hello");
    var handler = new CapturingCallHandler<byte[], byte[]>();

    interceptor.interceptCall(call, headers, handler);

    assertTrue(handler.wasCalled);
    HttpRequest<?> captured = findCaptureWithHeader("x-custom-text");
    assertNotNull(captured, "Should find a captured request with x-custom-text header");
    assertNull(captured.getHeaders().get("custom-data-bin"),
        "Binary headers should be excluded from the synthetic request");
    assertEquals("hello", captured.getHeaders().get("x-custom-text"),
        "ASCII headers should be copied to the synthetic request");
  }

  @Test
  void syntheticRequestCopiesMultipleAsciiHeaders() {
    var call = new FakeServerCall<>(SECURED_METHOD);
    var headers = bearerHeaders("valid-worker");
    headers.put(
        Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER),
        "req-123");
    headers.put(
        Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER),
        "tenant-456");
    var handler = new CapturingCallHandler<byte[], byte[]>();

    interceptor.interceptCall(call, headers, handler);

    assertTrue(handler.wasCalled);
    HttpRequest<?> captured = findCaptureWithHeader("x-request-id");
    assertNotNull(captured, "Should find a captured request with x-request-id header");
    assertEquals("req-123", captured.getHeaders().get("x-request-id"));
    assertEquals("tenant-456", captured.getHeaders().get("x-tenant-id"));
  }

  /** Finds a captured request that contains the given header. */
  private HttpRequest<?> findCaptureWithHeader(String headerName) {
    for (var capture : requestCapturingRule.captures) {
      if (capture.request().getHeaders().get(headerName) != null) {
        return capture.request();
      }
    }
    return null;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

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

  /**
   * A {@link SecurityRule} that runs before all other rules and captures every synthetic
   * {@link HttpRequest} that {@link GrpcSecurityInterceptor} builds from gRPC metadata.
   * Also exercises {@code getCookies()} to detect the production bug where
   * {@code CookieTokenReader} crashes on gRPC requests.
   *
   * <p>Returns {@link SecurityRuleResult#UNKNOWN} so it never interferes with the real
   * security rule chain ({@code SecuredAnnotationRule}, {@code ConfigurationInterceptUrlMapRule}).
   *
   * <p>Uses the {@link SecurityRule} chain instead of {@code AuthenticationFetcher}
   * because rules run via {@code concatMap} (sequential, guaranteed execution), whereas
   * authentication fetchers run via {@code flatMap} + {@code next()} which may cancel
   * remaining fetchers after the first emission.
   *
   * <p>Captures are stored in a thread-safe queue so tests running in parallel
   * can each find their own request by matching the URI path.
   */
  @Singleton
  static class RequestCapturingRule implements SecurityRule<HttpRequest<?>>, Ordered {
    final ConcurrentLinkedQueue<CapturedRequest> captures = new ConcurrentLinkedQueue<>();

    @Override
    public int getOrder() {
      return HIGHEST_PRECEDENCE;
    }

    @Override
    public Publisher<SecurityRuleResult> check(HttpRequest<?> request, @Nullable Authentication authentication) {
      boolean cookiesOk;
      try {
        request.getCookies();
        cookiesOk = true;
      } catch (UnsupportedOperationException e) {
        cookiesOk = false;
      }
      captures.add(new CapturedRequest(request, cookiesOk));
      return Mono.just(SecurityRuleResult.UNKNOWN);
    }

    /** Finds the first capture whose URI path contains the given method name. */
    CapturedRequest findByMethod(String fullMethodName) {
      for (var capture : captures) {
        if (capture.request.getPath().contains(fullMethodName)) {
          return capture;
        }
      }
      return null;
    }
  }

  record CapturedRequest(HttpRequest<?> request, boolean cookiesAccessSucceeded) {}

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

  /**
   * Simulates a third-party gRPC service (e.g. grpc.health.v1.Health) that cannot carry
   * {@code @Secured} annotations. Access is configured exclusively via intercept-url-map.
   * Each method corresponds to a different class-level intercept-url-map pattern so that
   * all access-rule variants can be tested within a single application context.
   */
  @Singleton
  static class UnconfiguredService implements BindableService {

    @Override
    public ServerServiceDefinition bindService() {
      var anonMethod = method("unconfigured.Service", "AnonMethod");
      var authMethod = method("unconfigured.Service", "AuthMethod");
      var roleMethod = method("unconfigured.Service", "RoleMethod");

      var descriptor = ServiceDescriptor.newBuilder("unconfigured.Service")
          .addMethod(anonMethod)
          .addMethod(authMethod)
          .addMethod(roleMethod)
          .build();

      return ServerServiceDefinition.builder(descriptor)
          .addMethod(anonMethod, (call, headers) -> { throw new UnsupportedOperationException(); })
          .addMethod(authMethod, (call, headers) -> { throw new UnsupportedOperationException(); })
          .addMethod(roleMethod, (call, headers) -> { throw new UnsupportedOperationException(); })
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

  private static final MethodDescriptor<byte[], byte[]> SECURED_METHOD =
      method("test.Service", "SecuredWork");
  private static final MethodDescriptor<byte[], byte[]> ANONYMOUS_METHOD =
      method("test.Service", "AnonWork");
  private static final MethodDescriptor<byte[], byte[]> AUTHENTICATED_METHOD =
      method("test.Service", "AuthenticatedWork");
  private static final MethodDescriptor<byte[], byte[]> UNKNOWN_METHOD =
      method("unknown.Service", "Unknown");
  // Each UNCONFIGURED_* constant corresponds to a class-level intercept-url-map pattern.
  private static final MethodDescriptor<byte[], byte[]> UNCONFIGURED_ANON_METHOD =
      method("unconfigured.Service", "AnonMethod");
  private static final MethodDescriptor<byte[], byte[]> UNCONFIGURED_AUTH_METHOD =
      method("unconfigured.Service", "AuthMethod");
  private static final MethodDescriptor<byte[], byte[]> UNCONFIGURED_ROLE_METHOD =
      method("unconfigured.Service", "RoleMethod");

  private static final ServerCallHandler<byte[], byte[]> FAILING_HANDLER =
      (call, headers) -> { throw new AssertionError("Handler should not be called"); };
}
