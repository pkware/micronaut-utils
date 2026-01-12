package com.pkware.micronaut.assisted;

import io.micronaut.aop.Introduction;

@Introduction
@Assisted
public interface SimpleFactory {
    SimpleProduct create(String value);
}
