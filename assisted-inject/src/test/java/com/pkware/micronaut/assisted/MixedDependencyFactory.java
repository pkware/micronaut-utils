package com.pkware.micronaut.assisted;

import io.micronaut.aop.Introduction;

@Introduction
@Assisted
public interface MixedDependencyFactory {
  MixedDependencyProduct create(String runtimeParam);
}
