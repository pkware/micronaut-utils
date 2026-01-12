package com.pkware.micronaut.assisted;

import io.micronaut.aop.Introduction;

@Introduction
@Assisted
public interface MultiParamFactory {
  MultiParamProduct create(String stringParam, int intParam, boolean boolParam);
}
