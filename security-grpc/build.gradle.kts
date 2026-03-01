plugins {
  `java-conventions`
  `publish-conventions`
  alias(libs.plugins.micronaut.library)
}

dependencies {
  api(libs.jspecify)
  api(libs.grpc.api)
  api(mn.micronaut.context)

  testAnnotationProcessor(mn.micronaut.inject.java)
  // SecuredAnnotationMapper SPI must be on the annotation processor classpath so that
  // @Secured on test beans generates @Executable metadata for GrpcSecuredMethodRegistry.
  testAnnotationProcessor(projects.securityGrpcProcessor)
  testImplementation(mn.micronaut.test.junit5)
  testImplementation(mn.micronaut.security)
}
