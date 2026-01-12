plugins {
    `java-conventions`
    alias(libs.plugins.micronaut.library)
}

dependencies {
    // Consume assisted-inject as a JAR dependency (the scenario we're testing)
    implementation(projects.assistedInject)

    // Annotation processing - must process @Introduction from the JAR
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronaut {
  testRuntime("junit5")
}
