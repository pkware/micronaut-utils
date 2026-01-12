# assisted-inject-test-java

Integration tests for assisted injection using **Java annotation processing**.

## Purpose

This module consumes `assisted-inject` as a compiled JAR dependency to verify that `@Assisted` works correctly when used from an external project. This catches issues that wouldn't appear when testing within the same module, such as:

- Meta-annotations not being processed from JARs
- Missing bean definitions for interceptors
- Incorrect classpath configuration

## Known Limitation

Java annotation processing doesn't process meta-annotations from compiled JARs. This means `@Introduction` (which is on `@Assisted`) isn't detected. The workaround is to add `@Introduction` directly to factory interfaces:

```java
@Introduction  // Required for Java AP
@Assisted
public interface MyFactory {
    MyProduct create(String value);
}
```

See `assisted-inject-test-ksp` for Kotlin/KSP, which handles meta-annotations correctly.