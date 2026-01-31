package com.pkware.micronaut.junit

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.mockito.junit.jupiter.MockitoExtension

/**
 * JUnit5 extension that enforces `@TestInstance(PER_CLASS)` for test classes that require it.
 *
 * A test class requires PER_CLASS lifecycle when:
 * - It's annotated with [MicronautTest] (or meta-annotations containing it)
 * - AND it either implements [TestPropertyProvider] OR uses [MockitoExtension]
 *
 * These tests need PER_CLASS because:
 * - **TestPropertyProvider**: `getProperties()` is called during test context initialization, which
 *   happens once per test class. PER_CLASS ensures `@BeforeAll` methods run before this initialization.
 * - **MockitoExtension with Micronaut**: Mock lifecycle needs to align with Micronaut's test context
 *   lifecycle to avoid mock state pollution between tests.
 *
 * Usage:
 * ```kotlin
 * @MicronautTest
 * @TestInstance(TestInstance.Lifecycle.PER_CLASS)  // Required!
 * class MyHealthIndicatorTest : TestPropertyProvider {
 *   // ...
 * }
 * ```
 *
 * @see TestPropertyProvider
 * @see MockitoExtension
 */
internal class TestInstanceLifecycleValidator : BeforeAllCallback {

  override fun beforeAll(context: ExtensionContext) {
    val testClass = context.requiredTestClass

    // Check if test class is annotated with @MicronautTest (directly or via meta-annotation)
    val hasMicronautTest = hasAnnotationRecursively(testClass, MicronautTest::class.java)

    // Check if test class implements TestPropertyProvider
    val implementsTestPropertyProvider = TestPropertyProvider::class.java.isAssignableFrom(testClass)

    // Check if test class uses @ExtendWith(MockitoExtension::class)
    // We check by class name to avoid requiring Mockito on the compilation classpath
    val usesMockitoExtension = testClass.annotations
      .filterIsInstance<ExtendWith>()
      .flatMap { it.value.toList() }
      .any { it.java.name == "org.mockito.junit.jupiter.MockitoExtension" }

    // If conditions are met, validate PER_CLASS is present
    if (hasMicronautTest && (implementsTestPropertyProvider || usesMockitoExtension)) {
      val testInstanceAnnotation = testClass.getAnnotation(TestInstance::class.java)
      val lifecycle = testInstanceAnnotation?.value

      if (lifecycle != TestInstance.Lifecycle.PER_CLASS) {
        val reason = when {
          implementsTestPropertyProvider && usesMockitoExtension ->
            "implements TestPropertyProvider and uses @ExtendWith(MockitoExtension::class)"
          implementsTestPropertyProvider ->
            "implements TestPropertyProvider"
          else ->
            "uses @ExtendWith(MockitoExtension::class)"
        }

        throw IllegalStateException(
          """
          Test class ${testClass.simpleName} is annotated with @MicronautTest and $reason,
          but is missing @TestInstance(TestInstance.Lifecycle.PER_CLASS).

          Add this annotation to the test class:

          @TestInstance(TestInstance.Lifecycle.PER_CLASS)
          class ${testClass.simpleName} {
            // ...
          }

          Why is this required?
          - TestPropertyProvider.getProperties() is called during test context initialization
          - @BeforeAll/@AfterAll methods need to run at the class level, not per test
          - PER_CLASS lifecycle ensures proper setup/teardown sequencing
          """.trimIndent(),
        )
      }
    }
  }

  /**
   * Recursively checks if a class has an annotation, including meta-annotations.
   *
   * This is necessary because @MicronautTest might be present via meta-annotations
   * like @MicronautUnitTest.
   */
  private fun hasAnnotationRecursively(clazz: Class<*>, annotationClass: Class<out Annotation>): Boolean {
    // Check direct annotations
    if (clazz.isAnnotationPresent(annotationClass)) {
      return true
    }

    // Check meta-annotations (annotations on annotations)
    return clazz.annotations.any { annotation ->
      annotation.annotationClass.java.isAnnotationPresent(annotationClass)
    }
  }
}
