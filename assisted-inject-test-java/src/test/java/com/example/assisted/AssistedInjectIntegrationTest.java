package com.example.assisted;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that verifies @Assisted works when consumed from a JAR dependency.
 * <p>
 * This test is in a separate module from assisted-inject to simulate real-world usage
 * where the library is consumed as a compiled JAR. This catches issues like:
 * - Meta-annotations not being processed from JARs
 * - Missing bean definitions for interceptors
 * - Incorrect classpath configuration
 */
@MicronautTest
class AssistedInjectIntegrationTest {

  @Inject
  GreetingFactory factory;

  @Test
  void factoryIsInjectable() {
    assertNotNull(factory, "Factory should be injectable from JAR dependency");
  }

  @Test
  void factoryCreatesProductWithInjectedService() {
    Greeting greeting = factory.create("World");

    assertNotNull(greeting);
    assertEquals("Hello, World!", greeting.greet());
  }

  @Test
  void factoryCreatesNewInstanceEachTime() {
    Greeting first = factory.create("Alice");
    Greeting second = factory.create("Bob");

    assertNotSame(first, second);
    assertEquals("Hello, Alice!", first.greet());
    assertEquals("Hello, Bob!", second.greet());
  }
}
