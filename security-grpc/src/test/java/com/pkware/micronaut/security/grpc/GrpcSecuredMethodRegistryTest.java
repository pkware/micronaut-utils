package com.pkware.micronaut.security.grpc;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.security.annotation.Secured;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration tests for {@link GrpcSecuredMethodRegistry}.
 *
 * <p>Uses real Micronaut beans with {@code @Secured} annotations. The companion
 * {@code security-grpc-processor} on the test annotation processor classpath generates
 * the {@code ExecutableMethod} metadata that the registry reads at startup.
 */
@MicronautTest(startApplication = false)
class GrpcSecuredMethodRegistryTest {

  @Inject
  GrpcSecuredMethodRegistry registry;

  @Test
  void securedMethodAppearsInRegistry() {
    ExecutableMethod<?, ?> method = registry.getExecutableMethod("test.Alpha/SecuredWork");
    assertNotNull(method);
    assertEquals("SecuredWork", method.getMethodName());
    assertEquals(List.of("data_center:worker"), List.of(method.stringValues("io.micronaut.security.annotation.Secured")));
  }

  @Test
  void multipleRolesPreserved() {
    ExecutableMethod<?, ?> method = registry.getExecutableMethod("test.Alpha/MultiRole");
    assertNotNull(method);
    assertEquals(List.of("role:admin", "role:user"), List.of(method.stringValues("io.micronaut.security.annotation.Secured")));
  }

  @Test
  void unsecuredMethodAbsentFromRegistry() {
    assertNull(registry.getExecutableMethod("test.Alpha/UnsecuredWork"));
  }

  @Test
  void secondServiceMethodAppearsInRegistry() {
    ExecutableMethod<?, ?> method = registry.getExecutableMethod("test.Beta/BetaWork");
    assertNotNull(method);
    assertEquals(List.of("scope:beta"), List.of(method.stringValues("io.micronaut.security.annotation.Secured")));
  }

  @Test
  void unknownMethodReturnsNull() {
    assertNull(registry.getExecutableMethod("unknown.Service/Unknown"));
  }

  private static final MethodDescriptor.Marshaller<byte[]> BYTES_MARSHALLER =
      new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(byte[] value) {
          return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
          return new byte[0];
        }
      };

  private static MethodDescriptor<byte[], byte[]> method(String service, String method) {
    return MethodDescriptor.<byte[], byte[]>newBuilder()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(MethodDescriptor.generateFullMethodName(service, method))
        .setRequestMarshaller(BYTES_MARSHALLER)
        .setResponseMarshaller(BYTES_MARSHALLER)
        .build();
  }

  /**
   * A gRPC service with both secured and unsecured methods.
   *
   * <p>The Java method names must match the gRPC method names in the
   * {@link ServiceDescriptor} so that {@link GrpcSecuredMethodRegistry} can correlate them.
   */
  @Singleton
  static class AlphaService implements BindableService {

    @Secured("data_center:worker")
    public void SecuredWork() {}

    @Secured({"role:admin", "role:user"})
    public void MultiRole() {}

    public void UnsecuredWork() {}

    @Override
    public ServerServiceDefinition bindService() {
      var securedWork = method("test.Alpha", "SecuredWork");
      var multiRole = method("test.Alpha", "MultiRole");
      var unsecuredWork = method("test.Alpha", "UnsecuredWork");

      var descriptor = ServiceDescriptor.newBuilder("test.Alpha")
          .addMethod(securedWork)
          .addMethod(multiRole)
          .addMethod(unsecuredWork)
          .build();

      return ServerServiceDefinition.builder(descriptor)
          .addMethod(securedWork, (call, headers) -> { throw new UnsupportedOperationException(); })
          .addMethod(multiRole, (call, headers) -> { throw new UnsupportedOperationException(); })
          .addMethod(unsecuredWork, (call, headers) -> { throw new UnsupportedOperationException(); })
          .build();
    }
  }

  /** A second service to verify multi-service registration. */
  @Singleton
  static class BetaService implements BindableService {

    @Secured("scope:beta")
    public void BetaWork() {}

    @Override
    public ServerServiceDefinition bindService() {
      var betaWork = method("test.Beta", "BetaWork");

      var descriptor = ServiceDescriptor.newBuilder("test.Beta")
          .addMethod(betaWork)
          .build();

      return ServerServiceDefinition.builder(descriptor)
          .addMethod(betaWork, (call, headers) -> { throw new UnsupportedOperationException(); })
          .build();
    }
  }
}
