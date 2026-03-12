plugins {
  `kotlin-conventions`
  `publish-conventions`
}

dependencies {
  api(mn.micronaut.test.resources.core)
  implementation(mn.micronaut.core)
  implementation(libs.mock.oauth2.server)

  testImplementation(libs.bundles.mockito)
}
