package com.pkware.micronaut.grpc.authorization

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.grpc.BindableService
import io.grpc.MethodDescriptor
import io.grpc.ServerCallHandler
import io.grpc.ServerServiceDefinition
import io.grpc.ServiceDescriptor
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Integration tests for [GrpcScopeRegistry]. Uses real Micronaut beans with `@RolesAllowed`
 * methods; the security-grpc-processor on the test annotation processor classpath generates the
 * `ExecutableMethod` metadata the registry reads at startup. No `java.lang.reflect`.
 */
@MicronautTest(startApplication = false)
internal class GrpcScopeRegistryTest {
  @Inject
  lateinit var registry: GrpcScopeRegistry

  @Test
  fun annotatedMethodAppearsWithItsScopes() {
    assertThat(registry.requiredScopes("test.Alpha/securedWork")).isNotNull().containsOnly("deployment:worker")
  }

  @Test
  fun multipleScopesPreserved() {
    assertThat(registry.requiredScopes("test.Alpha/multiRole")).isNotNull().containsOnly("role:admin", "role:user")
  }

  @Test
  fun unannotatedMethodAbsent() {
    assertThat(registry.requiredScopes("test.Alpha/plainWork")).isNull()
  }

  @Test
  fun secondServiceMethodAppears() {
    assertThat(registry.requiredScopes("test.Beta/betaWork")).isNotNull().containsOnly("scope:beta")
  }

  @Test
  fun unknownMethodAbsent() {
    assertThat(registry.requiredScopes("unknown.Service/Unknown")).isNull()
  }

  @Singleton
  internal class AlphaService : BindableService {
    @RolesAllowed("deployment:worker")
    fun securedWork() {
      // No-op: bean carries @RolesAllowed metadata only.
    }

    @RolesAllowed("role:admin", "role:user")
    fun multiRole() {
      // No-op: bean carries @RolesAllowed metadata only.
    }

    fun plainWork() {
      // No-op: unannotated method, expected absent from the registry.
    }

    override fun bindService(): ServerServiceDefinition {
      val secured = method("test.Alpha", "securedWork")
      val multi = method("test.Alpha", "multiRole")
      val plain = method("test.Alpha", "plainWork")
      val descriptor = ServiceDescriptor.newBuilder("test.Alpha")
        .addMethod(secured).addMethod(multi).addMethod(plain).build()
      return ServerServiceDefinition.builder(descriptor)
        .addMethod(secured, UNSUPPORTED_HANDLER)
        .addMethod(multi, UNSUPPORTED_HANDLER)
        .addMethod(plain, UNSUPPORTED_HANDLER)
        .build()
    }
  }

  @Singleton
  internal class BetaService : BindableService {
    @RolesAllowed("scope:beta")
    fun betaWork() {
      // No-op: bean carries @RolesAllowed metadata only.
    }

    override fun bindService(): ServerServiceDefinition {
      val beta = method("test.Beta", "betaWork")
      val descriptor = ServiceDescriptor.newBuilder("test.Beta").addMethod(beta).build()
      return ServerServiceDefinition.builder(descriptor)
        .addMethod(beta, UNSUPPORTED_HANDLER)
        .build()
    }
  }

  companion object {
    private val BYTES: MethodDescriptor.Marshaller<ByteArray> = object : MethodDescriptor.Marshaller<ByteArray> {
      override fun stream(value: ByteArray): InputStream = ByteArrayInputStream(value)

      override fun parse(stream: InputStream): ByteArray = ByteArray(0)
    }

    private val UNSUPPORTED_HANDLER =
      ServerCallHandler<ByteArray, ByteArray> { _, _ -> throw UnsupportedOperationException() }

    private fun method(service: String, name: String): MethodDescriptor<ByteArray, ByteArray> =
      MethodDescriptor.newBuilder<ByteArray, ByteArray>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(MethodDescriptor.generateFullMethodName(service, name))
        .setRequestMarshaller(BYTES).setResponseMarshaller(BYTES).build()
  }
}
