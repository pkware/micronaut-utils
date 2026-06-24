package com.pkware.micronaut.security.grpc;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps {@code @RolesAllowed} → {@code @Executable} at compile time so that Micronaut
 * generates {@link io.micronaut.inject.ExecutableMethod} metadata for gRPC service
 * methods annotated with {@code jakarta.annotation.security.RolesAllowed}.
 *
 * <p>This serves the {@code grpc-authorization} module the same way
 * {@link SecuredAnnotationMapper} serves {@code security-grpc}. Uses string names to avoid
 * a compile-time dependency on jakarta annotations. The original {@code @RolesAllowed}
 * annotation is retained — {@link NamedAnnotationMapper} adds annotations, it does not replace.
 *
 * <p>Registered via {@code META-INF/services/io.micronaut.inject.annotation.AnnotationMapper}.
 */
public final class RolesAllowedAnnotationMapper implements NamedAnnotationMapper {

  private static final String ROLES_ALLOWED = "jakarta.annotation.security.RolesAllowed";
  private static final String EXECUTABLE = "io.micronaut.context.annotation.Executable";

  @Override
  public String getName() {
    return ROLES_ALLOWED;
  }

  @Override
  public List<AnnotationValue<?>> map(
      AnnotationValue<Annotation> annotation,
      VisitorContext visitorContext) {
    return List.of(AnnotationValue.builder(EXECUTABLE).build());
  }
}
