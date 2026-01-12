plugins {
  `java-conventions`
  `publish-conventions`
  alias(libs.plugins.micronaut.library)
}

dependencies {
  api(libs.jspecify)
  api(mn.micronaut.aop)
  api(mn.micronaut.context)

  // Testing
  testAnnotationProcessor(mn.micronaut.inject.java)
  testAnnotationProcessor(mn.micronaut.graal)  // Brings in micronaut-core-processor for @Introduction support
  testImplementation(mn.micronaut.test.junit5)
}
