package com.pkware.micronaut.grpc.authorization;

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
import io.micronaut.context.annotation.Replaces;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link GrpcAuthorizationInterceptor}. A {@link Replaces} test resolver
 * builds a principal from an {@code x-subject} / {@code x-scopes} header pair, standing in for the
 * application's real header-trust resolver. The security-grpc-processor on the test annotation
 * processor classpath generates {@code @Executable} metadata for the {@code @RolesAllowed} test beans.
 */
@MicronautTest(startApplication = false)
@Property(name = "grpc.authorization.anonymous-services[0]", value = "grpc.health.v1.Health")
class GrpcAuthorizationInterceptorTest {

  @Inject GrpcAuthorizationInterceptor interceptor;
  @Inject MeterRegistry meterRegistry;

  @Test
  void unregisteredMethodDeniedEvenWithoutCredentials() {
    var call = new FakeServerCall<>(UNKNOWN_METHOD);
    interceptor.interceptCall(call, new Metadata(), FAILING_HANDLER);
    assertEquals(Status.Code.PERMISSION_DENIED, call.closedStatus.getCode());
    assertTrue(call.closedStatus.getDescription().contains("Method not allowed"));
    assertCounter(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_METHOD_NOT_ALLOWED);
  }

  @Test
  void unregisteredMethodDeniedEvenWithValidCredentials() {
    var call = new FakeServerCall<>(UNKNOWN_METHOD);
    interceptor.interceptCall(call, headers("svc", "deployment:worker"), FAILING_HANDLER);
    assertEquals(Status.Code.PERMISSION_DENIED, call.closedStatus.getCode());
    assertTrue(call.closedStatus.getDescription().contains("Method not allowed"));
  }

  @Test
  void anonymousServiceMethodPassesWithoutPrincipal() {
    var call = new FakeServerCall<>(HEALTH_METHOD);
    var handler = new CapturingCallHandler<byte[], byte[]>();
    interceptor.interceptCall(call, new Metadata(), handler);
    assertTrue(handler.wasCalled);
    assertNull(call.closedStatus);
    assertNull(handler.capturedPrincipal, "anonymous method carries no principal");
  }

  @Test
  void missingPrincipalOnRegisteredMethodIsUnauthenticated() {
    var call = new FakeServerCall<>(SECURED_METHOD);
    interceptor.interceptCall(call, new Metadata(), FAILING_HANDLER);
    assertEquals(Status.Code.UNAUTHENTICATED, call.closedStatus.getCode());
    assertCounter(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_REJECTED);
  }

  @Test
  void wrongScopeIsDenied() {
    var call = new FakeServerCall<>(SECURED_METHOD);
    interceptor.interceptCall(call, headers("svc", "wrong:scope"), FAILING_HANDLER);
    assertEquals(Status.Code.PERMISSION_DENIED, call.closedStatus.getCode());
    assertTrue(call.closedStatus.getDescription().contains("Insufficient scope"));
    assertCounter(GrpcAuthorizationMetrics.OUTCOME_FAILED, GrpcAuthorizationMetrics.REASON_REJECTED);
  }

  @Test
  void correctScopePassesAndPublishesPrincipal() {
    var call = new FakeServerCall<>(SECURED_METHOD);
    var handler = new CapturingCallHandler<byte[], byte[]>();
    interceptor.interceptCall(call, headers("svc", "deployment:worker"), handler);
    assertTrue(handler.wasCalled);
    assertNull(call.closedStatus);
    assertNotNull(handler.capturedPrincipal);
    assertEquals("svc", handler.capturedPrincipal.getSubject());
    assertCounter(GrpcAuthorizationMetrics.OUTCOME_SUCCESS, GrpcAuthorizationMetrics.REASON_NONE);
  }

  @Test
  void resolveTimerRecorded() {
    var call = new FakeServerCall<>(SECURED_METHOD);
    interceptor.interceptCall(call, headers("svc", "deployment:worker"), new CapturingCallHandler<>());
    var timer = meterRegistry.find(GrpcAuthorizationMetrics.AUTHENTICATION_DURATION).timer();
    assertNotNull(timer);
    assertTrue(timer.count() >= 1);
  }

  private void assertCounter(String outcome, String reason) {
    var counter = meterRegistry.find(GrpcAuthorizationMetrics.AUTH_ATTEMPTS)
        .tag(GrpcAuthorizationMetrics.TAG_OUTCOME, outcome)
        .tag(GrpcAuthorizationMetrics.TAG_REASON, reason).counter();
    assertNotNull(counter, "counter for outcome=" + outcome + " reason=" + reason);
    assertTrue(counter.count() >= 1.0);
  }

  private static Metadata headers(String subject, String scopes) {
    var m = new Metadata();
    m.put(SUBJECT_KEY, subject);
    m.put(SCOPES_KEY, scopes);
    return m;
  }

  // A header-based resolver standing in for the application's EnvoyPrincipalResolver.
  @Singleton
  @Replaces(GrpcPrincipalResolver.class)
  static class HeaderResolver implements GrpcPrincipalResolver {
    @Override public @Nullable GrpcPrincipal resolve(Metadata headers) {
      String subject = headers.get(SUBJECT_KEY);
      if (subject == null || subject.isBlank()) {
        return null;
      }
      String raw = headers.get(SCOPES_KEY);
      Set<String> scopes = raw == null || raw.isBlank() ? Set.of() : Set.of(raw.split("\\s+"));
      return new GrpcPrincipal() {
        @Override public String getSubject() { return subject; }
        @Override public Set<String> getScopes() { return scopes; }
      };
    }
  }

  @Singleton
  static SimpleMeterRegistry testMeterRegistry() { return new SimpleMeterRegistry(); }

  // anonymousServices configured via @MicronautTest properties below.
  @Singleton
  static class WorkerService implements BindableService {
    @RolesAllowed("deployment:worker") public void SecuredWork() {}
    @Override public ServerServiceDefinition bindService() {
      var secured = method("test.Worker", "SecuredWork");
      var descriptor = ServiceDescriptor.newBuilder("test.Worker").addMethod(secured).build();
      return ServerServiceDefinition.builder(descriptor)
          .addMethod(secured, (c, h) -> { throw new UnsupportedOperationException(); }).build();
    }
  }

  @Singleton
  static class HealthService implements BindableService {
    public void Check() {}
    @Override public ServerServiceDefinition bindService() {
      var check = method("grpc.health.v1.Health", "Check");
      var descriptor = ServiceDescriptor.newBuilder("grpc.health.v1.Health").addMethod(check).build();
      return ServerServiceDefinition.builder(descriptor)
          .addMethod(check, (c, h) -> { throw new UnsupportedOperationException(); }).build();
    }
  }

  private static final Metadata.Key<String> SUBJECT_KEY =
      Metadata.Key.of("x-subject", Metadata.ASCII_STRING_MARSHALLER);
  private static final Metadata.Key<String> SCOPES_KEY =
      Metadata.Key.of("x-scopes", Metadata.ASCII_STRING_MARSHALLER);

  private static final MethodDescriptor.Marshaller<byte[]> BYTES = new MethodDescriptor.Marshaller<>() {
    @Override public InputStream stream(byte[] value) { return new ByteArrayInputStream(value); }
    @Override public byte[] parse(InputStream stream) { return new byte[0]; }
  };

  private static MethodDescriptor<byte[], byte[]> method(String service, String name) {
    return MethodDescriptor.<byte[], byte[]>newBuilder()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(MethodDescriptor.generateFullMethodName(service, name))
        .setRequestMarshaller(BYTES).setResponseMarshaller(BYTES).build();
  }

  private static final MethodDescriptor<byte[], byte[]> SECURED_METHOD = method("test.Worker", "SecuredWork");
  private static final MethodDescriptor<byte[], byte[]> HEALTH_METHOD = method("grpc.health.v1.Health", "Check");
  private static final MethodDescriptor<byte[], byte[]> UNKNOWN_METHOD = method("unknown.Service", "Unknown");

  private static final ServerCallHandler<byte[], byte[]> FAILING_HANDLER =
      (call, headers) -> { throw new AssertionError("handler should not be called"); };

  private static final class FakeServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {
    private final MethodDescriptor<ReqT, RespT> methodDescriptor;
    Status closedStatus;
    FakeServerCall(MethodDescriptor<ReqT, RespT> methodDescriptor) { this.methodDescriptor = methodDescriptor; }
    @Override public MethodDescriptor<ReqT, RespT> getMethodDescriptor() { return methodDescriptor; }
    @Override public void request(int numMessages) {}
    @Override public void sendHeaders(Metadata headers) {}
    @Override public void sendMessage(RespT message) {}
    @Override public boolean isCancelled() { return false; }
    @Override public void close(Status status, Metadata trailers) { closedStatus = status; }
  }

  private static final class CapturingCallHandler<ReqT, RespT> implements ServerCallHandler<ReqT, RespT> {
    boolean wasCalled;
    @Nullable GrpcPrincipal capturedPrincipal;
    @Override public ServerCall.Listener<ReqT> startCall(ServerCall<ReqT, RespT> call, Metadata headers) {
      wasCalled = true;
      capturedPrincipal = GrpcCallerContext.current();
      return new ServerCall.Listener<>() {};
    }
  }
}
