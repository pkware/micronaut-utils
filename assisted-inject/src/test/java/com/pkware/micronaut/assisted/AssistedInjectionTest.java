package com.pkware.micronaut.assisted;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class AssistedInjectionTest {

    @Inject
    SimpleFactory simpleFactory;

    @Inject
    MultiParamFactory multiParamFactory;

    @Inject
    MixedDependencyFactory mixedDependencyFactory;

    @Inject
    MultiMethodFactory multiMethodFactory;

    @Test
    void factoryCreatesInstanceWithSingleParameter() {
        var result = simpleFactory.create("test-value");

        assertNotNull(result);
        assertEquals("test-value", result.value());
    }

    @Test
    void factoryCreatesNewInstanceOnEachCall() {
        var first = simpleFactory.create("first");
        var second = simpleFactory.create("second");

        assertNotSame(first, second);
        assertEquals("first", first.value());
        assertEquals("second", second.value());
    }

    @Test
    void factoryPassesMultipleParametersInOrder() {
        var result = multiParamFactory.create("hello", 42, true);

        assertEquals("hello", result.stringParam());
        assertEquals(42, result.intParam());
        assertTrue(result.boolParam());
    }

    @Test
    void factoryInjectsDependenciesAlongsideParameters() {
        var result = mixedDependencyFactory.create("runtime-value");

        assertNotNull(result.injectedService());
        assertEquals("runtime-value", result.runtimeParam());
        assertEquals("SERVICE:runtime-value", result.combine());
    }

    @Test
    void singleFactoryInterfaceSupportsMultipleMethods() {
        var typeA = multiMethodFactory.createTypeA("a-value");
        var typeB = multiMethodFactory.createTypeB(123);

        assertEquals("a-value", typeA.value());
        assertEquals(123, typeB.number());
    }

    @Test
    void factoryRejectsNullForNonNullableParameters() {
        // Micronaut's DI validates arguments - null is rejected for non-nullable parameters
        var exception = assertThrows(
            io.micronaut.context.exceptions.BeanInstantiationException.class,
            () -> simpleFactory.create(null)
        );
        assertTrue(exception.getMessage().contains("cannot be null"));
    }
}
