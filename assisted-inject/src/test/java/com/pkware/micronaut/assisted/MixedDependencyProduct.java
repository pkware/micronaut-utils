package com.pkware.micronaut.assisted;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;

@Prototype
public record MixedDependencyProduct(
        TestService injectedService,
        @Parameter String runtimeParam
) {
    public String combine() {
        return injectedService.process(runtimeParam);
    }
}
