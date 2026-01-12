package com.pkware.micronaut.assisted;

import io.micronaut.aop.Introduction;

@Introduction
@Assisted
public interface MultiMethodFactory {
    TypeAProduct createTypeA(String value);
    TypeBProduct createTypeB(int number);
}
