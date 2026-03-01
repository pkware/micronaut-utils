plugins {
  `java-conventions`
  `publish-conventions`
  alias(libs.plugins.micronaut.library)
}

dependencies {
  api(libs.jspecify)
  api(mn.micronaut.core.processor) {
    because("SecuredAnnotationMapper implements NamedAnnotationMapper from core-processor")
  }

  testAnnotationProcessor(mn.micronaut.inject.java)
  // Annotation processor must discover SecuredAnnotationMapper SPI during test compilation.
  testAnnotationProcessor(sourceSets.main.get().output)
  testImplementation(mn.micronaut.test.junit5)
  testImplementation(mn.micronaut.security)
}
