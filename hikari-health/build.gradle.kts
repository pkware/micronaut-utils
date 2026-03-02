plugins {
  `kotlin-conventions`
  `publish-conventions`
  alias(libs.plugins.micronaut.library)
}

dependencies {
  api(mn.micronaut.management)
  api(mn.micronaut.jdbc)

  implementation(mn.micronaut.jdbc.hikari)
  implementation(mn.reactor)

  testImplementation(libs.bundles.mockito)
  testRuntimeOnly(mn.h2)
}
