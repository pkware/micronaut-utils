package com.example.assisted

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

/**
 * Integration test that verifies @Assisted works with Kotlin KSP when consumed from a JAR.
 *
 * This test is in a separate module from assisted-inject to simulate real-world usage.
 * Unlike the Java integration test, this uses KSP instead of Java annotation processing.
 *
 * Key question this test answers: Does KSP handle the @Introduction meta-annotation.
 */
@MicronautTest
class AssistedInjectKspIntegrationTest {

  @Inject
  lateinit var factory: GreetingFactory

  @Test
  fun `factory is injectable from JAR dependency`() {
    assertThat(factory, "Factory should be injectable from JAR dependency").isNotNull()
  }

  @Test
  fun `factory creates product with injected service`() {
    val greeting = factory.create("World")

    assertThat(greeting).isNotNull()
    assertThat(greeting.greet()).isEqualTo("Hello, World!")
  }

  @Test
  fun `factory creates new instance each time`() {
    val first = factory.create("Alice")
    val second = factory.create("Bob")

    assertThat(first).isNotSameInstanceAs(second)
    assertThat(first.greet()).isEqualTo("Hello, Alice!")
    assertThat(second.greet()).isEqualTo("Hello, Bob!")
  }
}
