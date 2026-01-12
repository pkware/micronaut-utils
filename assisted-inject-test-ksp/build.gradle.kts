plugins {
  `kotlin-conventions`
  alias(libs.plugins.ksp)
  alias(libs.plugins.micronaut.library)
  alias(libs.plugins.kotlin.allOpen)
}

dependencies {
  // Consume assisted-inject as a JAR dependency (the scenario we're testing)
  implementation(projects.assistedInject)

  kspTest(mn.micronaut.inject.kotlin)
  testImplementation(mn.micronaut.test.junit5)
}

allOpen {
  preset("micronaut")
}

micronaut {
  testRuntime("junit5")
}
