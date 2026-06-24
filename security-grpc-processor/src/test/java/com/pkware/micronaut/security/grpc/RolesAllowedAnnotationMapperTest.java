package com.pkware.micronaut.security.grpc;

import io.micronaut.context.BeanContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link RolesAllowedAnnotationMapper} causes Micronaut to generate
 * {@link ExecutableMethod} metadata for methods annotated with {@code @RolesAllowed},
 * and that the role values survive on the metadata.
 */
@MicronautTest(startApplication = false)
class RolesAllowedAnnotationMapperTest {

  @Inject
  BeanContext beanContext;

  @Test
  void rolesAllowedMethodHasExecutableMethodMetadata() {
    BeanDefinition<?> definition = beanContext.getBeanDefinition(TestBean.class);
    boolean found = definition.getExecutableMethods().stream()
        .anyMatch(m -> m.getMethodName().equals("rolesMethod"));
    assertTrue(found, "@RolesAllowed method should produce ExecutableMethod metadata");
  }

  @Test
  void rolesAllowedMethodRetainsRoleValues() {
    BeanDefinition<?> definition = beanContext.getBeanDefinition(TestBean.class);
    ExecutableMethod<?, ?> method = definition.getExecutableMethods().stream()
        .filter(m -> m.getMethodName().equals("rolesMethod"))
        .findFirst().orElseThrow();
    String[] roles = method.stringValues("jakarta.annotation.security.RolesAllowed");
    assertTrue(roles.length > 0, "@RolesAllowed values should be retained");
    assertEquals("test:scope", roles[0]);
  }

  @Test
  void plainMethodDoesNotHaveExecutableMethodMetadata() {
    BeanDefinition<?> definition = beanContext.getBeanDefinition(TestBean.class);
    boolean found = definition.getExecutableMethods().stream()
        .anyMatch(m -> m.getMethodName().equals("plainMethod"));
    assertFalse(found, "Unannotated method should not produce ExecutableMethod metadata");
  }

  @Singleton
  static class TestBean {
    @RolesAllowed("test:scope")
    public String rolesMethod() { return "secured"; }
    public String plainMethod() { return "plain"; }
  }
}
