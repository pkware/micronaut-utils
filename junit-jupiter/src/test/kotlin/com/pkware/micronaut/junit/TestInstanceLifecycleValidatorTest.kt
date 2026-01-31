package com.pkware.micronaut.junit

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isNotNull
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever

/**
 * Tests for [TestInstanceLifecycleValidator].
 *
 * Verifies that the validator correctly enforces PER_CLASS lifecycle for tests that:
 * - Use @MicronautTest (or meta-annotations)
 * - AND implement TestPropertyProvider OR use MockitoExtension
 */
class TestInstanceLifecycleValidatorTest {

  private val validator = TestInstanceLifecycleValidator()

  @Test
  fun `allows test with MicronautTest and TestPropertyProvider when PER_CLASS is present`() {
    // Given: A test class with all required annotations
    @TestMicronautTest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ValidTest : TestPropertyProvider {
      override fun getProperties(): Map<String, String> = emptyMap()
    }

    val context = createContext(ValidTest::class.java)

    // When/Then: No exception is thrown
    validator.beforeAll(context)
  }

  @Test
  fun `allows test with MicronautTest and MockitoExtension when PER_CLASS is present`() {
    // Given: A test class with all required annotations
    @TestMicronautTest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @ExtendWith(MockitoExtension::class)
    class ValidTest

    val context = createContext(ValidTest::class.java)

    // When/Then: No exception is thrown
    validator.beforeAll(context)
  }

  @Test
  fun `detects MicronautTest through meta-annotations`() {
    // Given: A test class using custom meta-annotation containing @MicronautTest
    @TestMicronautTest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ValidTest : TestPropertyProvider {
      override fun getProperties(): Map<String, String> = emptyMap()
    }

    val context = createContext(ValidTest::class.java)

    // When/Then: No exception is thrown (meta-annotation detected)
    validator.beforeAll(context)
  }

  @Test
  fun `throws exception when MicronautTest and TestPropertyProvider present but PER_CLASS missing`() {
    // Given: A test class missing @TestInstance(PER_CLASS)
    @TestMicronautTest
    class InvalidTest : TestPropertyProvider {
      override fun getProperties(): Map<String, String> = emptyMap()
    }

    val context = createContext(InvalidTest::class.java)

    // When/Then: Exception is thrown with helpful message
    val exception = assertThrows<IllegalStateException> {
      validator.beforeAll(context)
    }

    assertThat(exception.message).isNotNull().contains("InvalidTest")
    assertThat(exception.message).isNotNull().contains("@TestInstance(TestInstance.Lifecycle.PER_CLASS)")
    assertThat(exception.message).isNotNull().contains("implements TestPropertyProvider")
  }

  @Test
  fun `throws exception when MicronautTest and MockitoExtension present but PER_CLASS missing`() {
    // Given: A test class missing @TestInstance(PER_CLASS)
    @TestMicronautTest
    @ExtendWith(MockitoExtension::class)
    class InvalidTest

    val context = createContext(InvalidTest::class.java)

    // When/Then: Exception is thrown with helpful message
    val exception = assertThrows<IllegalStateException> {
      validator.beforeAll(context)
    }

    assertThat(exception.message).isNotNull().contains("InvalidTest")
    assertThat(exception.message).isNotNull().contains("@ExtendWith(MockitoExtension::class)")
  }

  @Test
  fun `throws exception when both TestPropertyProvider and MockitoExtension present but PER_CLASS missing`() {
    // Given: A test class with both conditions
    @TestMicronautTest
    @ExtendWith(MockitoExtension::class)
    class InvalidTest : TestPropertyProvider {
      override fun getProperties(): Map<String, String> = emptyMap()
    }

    val context = createContext(InvalidTest::class.java)

    // When/Then: Exception message mentions both conditions
    val exception = assertThrows<IllegalStateException> {
      validator.beforeAll(context)
    }

    assertThat(exception.message).isNotNull().contains("implements TestPropertyProvider")
    assertThat(exception.message).isNotNull().contains("@ExtendWith(MockitoExtension::class)")
  }

  @Test
  fun `allows test with MicronautTest but neither TestPropertyProvider nor MockitoExtension`() {
    // Given: A test class that doesn't meet the conditions
    @TestMicronautTest
    class ValidTest

    val context = createContext(ValidTest::class.java)

    // When/Then: No exception is thrown (PER_CLASS not required)
    validator.beforeAll(context)
  }

  @Test
  fun `allows test with TestPropertyProvider but no MicronautTest`() {
    // Given: A test class without @MicronautTest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ValidTest : TestPropertyProvider {
      override fun getProperties(): Map<String, String> = emptyMap()
    }

    val context = createContext(ValidTest::class.java)

    // When/Then: No exception is thrown (rule doesn't apply)
    validator.beforeAll(context)
  }

  @Test
  fun `allows test with MockitoExtension but no MicronautTest`() {
    // Given: A test class without @MicronautTest
    @ExtendWith(MockitoExtension::class)
    class ValidTest

    val context = createContext(ValidTest::class.java)

    // When/Then: No exception is thrown (rule doesn't apply)
    validator.beforeAll(context)
  }

  @Test
  fun `allows test with MicronautTest and PER_METHOD lifecycle when conditions not met`() {
    // Given: A test class with PER_METHOD but doesn't need PER_CLASS
    @TestMicronautTest
    @TestInstance(TestInstance.Lifecycle.PER_METHOD)
    class ValidTest

    val context = createContext(ValidTest::class.java)

    // When/Then: No exception is thrown
    validator.beforeAll(context)
  }

  private fun createContext(testClass: Class<*>): ExtensionContext {
    val context = Mockito.mock(ExtensionContext::class.java)
    whenever(context.requiredTestClass).thenReturn(testClass)
    return context
  }

  /**
   * Local meta-annotation for testing that contains @MicronautTest.
   * This makes tests self-contained and not dependent on external meta-annotations.
   */
  @Target(AnnotationTarget.CLASS)
  @Retention(AnnotationRetention.RUNTIME)
  @MicronautTest(startApplication = false, transactional = false)
  private annotation class TestMicronautTest
}
