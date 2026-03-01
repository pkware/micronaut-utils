package com.pkware.micronaut.security.grpc;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps {@code @Secured} → {@code @Executable} at compile time so that Micronaut
 * generates {@link io.micronaut.inject.ExecutableMethod} metadata for gRPC service
 * methods annotated with {@code @Secured}.
 *
 * <p>Uses string names to avoid a compile-time dependency on {@code micronaut-security}.
 * The original {@code @Secured} annotation is retained — {@link NamedAnnotationMapper}
 * adds annotations, it does not replace.
 *
 * <p>Registered via {@code META-INF/services/io.micronaut.inject.annotation.AnnotationMapper}.
 */
public final class SecuredAnnotationMapper implements NamedAnnotationMapper {

  private static final String SECURED = "io.micronaut.security.annotation.Secured";
  private static final String EXECUTABLE = "io.micronaut.context.annotation.Executable";

  @Override
  public String getName() {
    return SECURED;
  }

  @Override
  public List<AnnotationValue<?>> map(
      AnnotationValue<Annotation> annotation,
      VisitorContext visitorContext) {
    return List.of(AnnotationValue.builder(EXECUTABLE).build());
  }
}
