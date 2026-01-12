# assisted-inject-test-ksp

Integration tests for assisted injection using **Kotlin KSP**.

## Purpose

This module consumes `assisted-inject` as a compiled JAR dependency to verify that `@Assisted` works correctly with Kotlin Symbol Processing (KSP).

## KSP vs Java Annotation Processing

Unlike Java annotation processing, KSP correctly handles meta-annotations from compiled JARs. This means Kotlin users get a cleaner experience — just `@Assisted` is enough:

```kotlin
@Assisted  // No need for @Introduction
interface MyFactory {
    fun create(value: String): MyProduct
}
```

This test module proves that KSP users don't need the `@Introduction` workaround required by Java AP.