package com.pkware.micronaut.assisted;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;

@Prototype
public record SimpleProduct(@Parameter String value) {
}
