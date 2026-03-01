package com.pkware.micronaut.security.grpc;

import io.micronaut.context.BeanContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.security.annotation.Secured;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link SecuredAnnotationMapper} causes Micronaut to generate
 * {@link ExecutableMethod} metadata for methods annotated with {@code @Secured}.
 */
@MicronautTest(startApplication = false)
class SecuredAnnotationMapperTest {

  @Inject
  BeanContext beanContext;

  @Test
  void securedMethodHasExecutableMethodMetadata() {
    BeanDefinition<?> definition = beanContext.getBeanDefinition(TestBean.class);
    var executableMethods = definition.getExecutableMethods();

    boolean found = executableMethods.stream()
        .anyMatch(m -> m.getMethodName().equals("securedMethod"));

    assertTrue(found,
        "@Secured method should produce ExecutableMethod metadata via SecuredAnnotationMapper");
  }

  @Test
  void securedMethodRetainsOriginalAnnotationValues() {
    BeanDefinition<?> definition = beanContext.getBeanDefinition(TestBean.class);

    var method = definition.getExecutableMethods().stream()
        .filter(m -> m.getMethodName().equals("securedMethod"))
        .findFirst()
        .orElseThrow();

    String[] roles = method.stringValues("io.micronaut.security.annotation.Secured");
    assertTrue(roles.length > 0, "@Secured values should be retained");
    assertEquals("test:scope", roles[0], "Expected 'test:scope' but got: " + roles[0]);
  }

  @Test
  void unsecuredMethodDoesNotHaveExecutableMethodMetadata() {
    BeanDefinition<?> definition = beanContext.getBeanDefinition(TestBean.class);
    var executableMethods = definition.getExecutableMethods();

    boolean found = executableMethods.stream()
        .anyMatch(m -> m.getMethodName().equals("unsecuredMethod"));

    assertFalse(found,
        "Unsecured method should not produce ExecutableMethod metadata");
  }

  @Singleton
  static class TestBean {

    @Secured("test:scope")
    public String securedMethod() {
      return "secured";
    }

    public String unsecuredMethod() {
      return "unsecured";
    }
  }
}
