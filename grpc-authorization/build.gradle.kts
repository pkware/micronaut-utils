plugins {
  `kotlin-conventions`
  `publish-conventions`
  alias(libs.plugins.micronaut.library)
}

dependencies {
  api(libs.jspecify)
  api(libs.grpc.api) {
    because("ServerInterceptor, Metadata, and Context are in the public API")
  }
  api(mn.micronaut.context) {
    because("@Singleton beans and BeanContext-driven scope discovery")
  }
  implementation(mn.micronaut.micrometer.core) {
    because("Records authorization attempt counts and latency")
  }

  // The @RolesAllowed → @Executable mapper must be on the annotation processor classpath so that
  // @RolesAllowed on test service beans generates the @Executable metadata GrpcScopeRegistry reads.
  testAnnotationProcessor(mn.micronaut.inject.java)
  testAnnotationProcessor(projects.securityGrpcProcessor)
  testImplementation(mn.jakarta.annotation.api) {
    because("Test service beans carry @RolesAllowed")
  }
  testImplementation(mn.micronaut.test.junit5)
}
