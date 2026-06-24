package com.pkware.micronaut.grpc.authorization;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration tests for {@link GrpcScopeRegistry}. Uses real Micronaut beans with {@code @RolesAllowed}
 * methods; the security-grpc-processor on the test annotation processor classpath generates the
 * {@code ExecutableMethod} metadata the registry reads at startup. No {@code java.lang.reflect}.
 */
@MicronautTest(startApplication = false)
class GrpcScopeRegistryTest {

  @Inject
  GrpcScopeRegistry registry;

  @Test
  void annotatedMethodAppearsWithItsScopes() {
    assertEquals(Set.of("deployment:worker"), registry.requiredScopes("test.Alpha/SecuredWork"));
  }

  @Test
  void multipleScopesPreserved() {
    assertEquals(Set.of("role:admin", "role:user"), registry.requiredScopes("test.Alpha/MultiRole"));
  }

  @Test
  void unannotatedMethodAbsent() {
    assertNull(registry.requiredScopes("test.Alpha/PlainWork"));
  }

  @Test
  void secondServiceMethodAppears() {
    assertEquals(Set.of("scope:beta"), registry.requiredScopes("test.Beta/BetaWork"));
  }

  @Test
  void unknownMethodAbsent() {
    assertNull(registry.requiredScopes("unknown.Service/Unknown"));
  }

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

  @Singleton
  static class AlphaService implements BindableService {
    @RolesAllowed("deployment:worker") public void SecuredWork() {}
    @RolesAllowed({"role:admin", "role:user"}) public void MultiRole() {}
    public void PlainWork() {}

    @Override public ServerServiceDefinition bindService() {
      var secured = method("test.Alpha", "SecuredWork");
      var multi = method("test.Alpha", "MultiRole");
      var plain = method("test.Alpha", "PlainWork");
      var descriptor = ServiceDescriptor.newBuilder("test.Alpha")
          .addMethod(secured).addMethod(multi).addMethod(plain).build();
      return ServerServiceDefinition.builder(descriptor)
          .addMethod(secured, (c, h) -> { throw new UnsupportedOperationException(); })
          .addMethod(multi, (c, h) -> { throw new UnsupportedOperationException(); })
          .addMethod(plain, (c, h) -> { throw new UnsupportedOperationException(); })
          .build();
    }
  }

  @Singleton
  static class BetaService implements BindableService {
    @RolesAllowed("scope:beta") public void BetaWork() {}

    @Override public ServerServiceDefinition bindService() {
      var beta = method("test.Beta", "BetaWork");
      var descriptor = ServiceDescriptor.newBuilder("test.Beta").addMethod(beta).build();
      return ServerServiceDefinition.builder(descriptor)
          .addMethod(beta, (c, h) -> { throw new UnsupportedOperationException(); }).build();
    }
  }
}
