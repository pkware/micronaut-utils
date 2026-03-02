plugins {
  `java-conventions`
  `publish-conventions`
  alias(libs.plugins.micronaut.library)
}

dependencies {
  api(libs.jspecify)
  api(libs.grpc.api)
  api(mn.micronaut.context)
  api(mn.micronaut.http) {
    because("HttpRequest and ServerFilterPhase are in the public API")
  }
  api(mn.micronaut.security) {
    because("Authentication, SecurityRule, and AuthenticationFetcher are in the public API")
  }
  implementation(mn.micronaut.micrometer.core)
  implementation(mn.micronaut.router) {
    because("GrpcMethodRouteMatch adapts ExecutableMethod as MethodBasedRouteMatch for SecuredAnnotationRule")
  }
  implementation(mn.reactor)

  testAnnotationProcessor(mn.micronaut.inject.java)
  // SecuredAnnotationMapper SPI must be on the annotation processor classpath so that
  // @Secured on test beans generates @Executable metadata for GrpcSecuredMethodRegistry.
  testAnnotationProcessor(projects.securityGrpcProcessor)
  testImplementation(mn.micronaut.http.server)
  testImplementation(mn.micronaut.test.junit5)
}
