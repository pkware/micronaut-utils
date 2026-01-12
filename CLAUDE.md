# CLAUDE.md

## Notes for Claude

Comments formatted as `// TODO @Claude` are instructions for you. Treat these as tasks to fix, implement, or add to your plan. Scan for them when starting work on a file.

## Project Overview

Micronaut Utils is a collection of lightweight utility libraries for the Micronaut ecosystem. Each module should be small, focused, and solve one problem well.

**Current modules:**
- `assisted-inject` — Assisted injection pattern for Micronaut

**Read the module READMEs** — each module has a README.md with detailed usage documentation.

## Module Structure

```
micronaut-utils/
├── assisted-inject/           # Main library (published to Maven Central)
├── assisted-inject-test-java/ # Integration tests for Java annotation processing
├── assisted-inject-test-ksp/  # Integration tests for Kotlin KSP
├── buildSrc/                  # Gradle convention plugins
│   └── src/main/kotlin/com/pkware/gradle/
│       ├── JavaConventionsPlugin.kt
│       ├── KotlinConventionsPlugin.kt
│       └── PublishConventionPlugin.kt
└── gradle/libs.versions.toml  # Version catalog
```

## Build & Test

```bash
./gradlew build          # Build all modules and run tests
./gradlew test           # Run tests only
./gradlew check          # Run tests + linting (ktlint, detekt)
./gradlew spotlessApply  # Auto-fix Kotlin formatting
```

## Code Style

**Formatting (enforced by ktlint + editorconfig):**
- 2-space indentation
- 120 character line length
- UTF-8, LF line endings
- Trailing newline required

**Kotlin static analysis (detekt):**
- Public APIs must be documented (KDoc)
- No `FIXME` or `STOPSHIP` comments
- No `@author` tags

**Test assertions:**
- **Kotlin tests**: Use assertk (JUnit assertions are forbidden by detekt)
- **Java tests**: Use JUnit assertions

**Documentation:**
- Always document nullable/optional parameters — explain what `null` means
- Use backticks for literals in KDoc: `` `null` ``, `` `0` ``

## Architecture

**Keep modules minimal.** Each utility should:
- Solve one specific problem
- Have minimal dependencies
- Be easy to understand at a glance

**Micronaut uses compile-time DI.** Unlike Spring, Micronaut processes annotations at compile time, not runtime. This means:
- Bean definitions are generated during compilation
- Annotation processors (Java) or KSP (Kotlin) must be configured correctly
- Runtime reflection is minimal

## Testing

New features typically need **both** unit tests and integration tests:

| Test Type | Purpose | Location |
|-----------|---------|----------|
| Unit tests | Test internal logic in isolation | `<module>/src/test/` |
| Integration tests (Java) | Verify Java annotation processing works | `assisted-inject-test-java/` |
| Integration tests (KSP) | Verify Kotlin KSP processing works | `assisted-inject-test-ksp/` |

**Why separate test modules?** The test modules consume `assisted-inject` as a compiled JAR dependency, simulating real-world usage. This catches issues that wouldn't appear when testing within the same module.

## Key Files

**assisted-inject module:**
- `Assisted.java` — The `@Assisted` annotation users apply to factory interfaces
- `AssistedInterceptor.java` — Implements factory methods via `BeanContext.createBean()`

**Convention plugins:**
- `java-conventions` — Java 21 toolchain, JUnit 5, reproducible builds
- `kotlin-conventions` — Extends java-conventions + ktlint + detekt + assertk
- `publish-conventions` — Maven Central publishing configuration

## Common Patterns

**Adding a new module:**
1. Create directory with `build.gradle.kts`
2. Apply appropriate convention plugin (`java-conventions` or `kotlin-conventions`)
3. Add to `settings.gradle.kts`
4. Create a README.md documenting usage

**Micronaut AOP pattern (used in assisted-inject):**
```java
// 1. Create marker annotation with @Introduction
@Introduction
@Retention(RUNTIME)
public @interface MyAnnotation {}

// 2. Create interceptor
@InterceptorBean(MyAnnotation.class)
class MyInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        // Implementation
    }
}
```