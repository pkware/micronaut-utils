plugins {
  `kotlin-conventions`
  `publish-conventions`
  alias(libs.plugins.ksp)
  alias(libs.plugins.micronaut.library)
  alias(libs.plugins.kotlin.allOpen)
}

dependencies {
  api(mn.micronaut.management)
  api(mn.micronaut.jdbc)

  implementation(mn.micronaut.jdbc.hikari)
  implementation(mn.reactor)

  ksp(mn.micronaut.inject.kotlin)

  testImplementation(libs.bundles.mockito)
  testImplementation(mn.micronaut.test.junit5)
  testRuntimeOnly(mn.h2)

  kspTest(mn.micronaut.inject.kotlin)
}

allOpen {
  preset("micronaut")
}

micronaut {
  testRuntime("junit5")
}
