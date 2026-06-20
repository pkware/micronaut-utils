plugins {
  `kotlin-conventions`
  `publish-conventions`
  alias(libs.plugins.ksp)
  alias(libs.plugins.micronaut.library)
  alias(libs.plugins.kotlin.allOpen)
}

dependencies {
  api(libs.jspecify)
  api(mn.micronaut.aop)
  api(mn.micronaut.context)

  // JSON
  api(libs.moshi)
  implementation(mn.micronaut.json.core)

  ksp(mn.micronaut.inject.kotlin)

  testImplementation(mn.micronaut.test.junit5)

  kspTest(mn.micronaut.inject.kotlin)
  // Generates reflection-free Moshi adapters for the @JsonClass-annotated test fixtures.
  kspTest(libs.moshi.codegen)
}

allOpen {
  preset("micronaut")
}

micronaut {
  testRuntime("junit5")
}

// All Micronaut beans in this module are Kotlin and are processed by KSP. The Micronaut Gradle
// plugin also wires its Java annotation processor onto compileJava (which runs only because of
// package-info.java), which would regenerate the same bean definitions and produce duplicate
// class entries in the jar. Disable annotation processing for Java compilation to avoid that.
// This module has no annotation-processed Java sources (only package-info.java); revisit if an
// annotation-processed Java bean is ever added.
tasks.named<JavaCompile>("compileJava") {
  options.compilerArgs.add("-proc:none")
}

// The public API of this module is entirely Kotlin; the only Java source is package-info.java,
// which declares no public/protected types. The Java `javadoc` task (pulled in by
// publish-conventions via withJavadocJar()) therefore fails with "No public or protected classes
// found to document". Exclude package-info.java so the task has no Java sources to process and is
// skipped (NO-SOURCE), producing an empty javadoc jar consistent with a Kotlin-only public API.
tasks.named<Javadoc>("javadoc") {
  exclude("**/package-info.java")
}
