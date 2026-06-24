plugins {
  `kotlin-conventions`
  `publish-conventions`
  alias(libs.plugins.ksp)
  alias(libs.plugins.micronaut.library)
  alias(libs.plugins.kotlin.allOpen)
}

dependencies {
  api(libs.grpc.api) {
    because("ServerInterceptor, Metadata, and Context are in the public API")
  }
  api(mn.micronaut.context) {
    because("@Singleton beans and BeanContext-driven scope discovery")
  }
  implementation(mn.micronaut.micrometer.core) {
    because("Records authorization attempt counts and latency")
  }

  ksp(mn.micronaut.inject.kotlin)

  kspTest(mn.micronaut.inject.kotlin)
  // The @RolesAllowed → @Executable mapper SPI must be on the KSP test classpath so that
  // @RolesAllowed on Kotlin test service beans generates the @Executable metadata GrpcScopeRegistry reads.
  kspTest(projects.securityGrpcProcessor)
  testImplementation(mn.jakarta.annotation.api) {
    because("Test service beans carry @RolesAllowed")
  }
  testImplementation(mn.micronaut.test.junit5)
}

allOpen {
  preset("micronaut")
}
