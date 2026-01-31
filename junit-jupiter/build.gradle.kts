plugins {
  `kotlin-conventions`
  `publish-conventions`
  alias(libs.plugins.micronaut.library)
}

dependencies {
  implementation(libs.jspecify)
  implementation(mn.junit.jupiter.api)
  implementation(mn.mockito.junit.jupiter)
  implementation(mn.micronaut.test.junit5)

  testImplementation(libs.bundles.mockito)
}
