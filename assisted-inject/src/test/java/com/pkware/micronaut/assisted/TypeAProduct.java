package com.pkware.micronaut.assisted;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;

@Prototype
public record TypeAProduct(@Parameter String value) {}
