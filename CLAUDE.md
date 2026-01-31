# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Micronaut utility libraries.

- **Java**: 21+
- **Micronaut**: 4.x
- **Build**: Gradle with Kotlin DSL

## Build Commands

```bash
./gradlew build              # Full build (compile, test, check, assemble)
./gradlew test               # Run all tests
./gradlew check              # Run all quality checks
./gradlew spotlessApply      # Auto-fix code formatting
```

**Kotlin static analysis (detekt):**
- Public APIs must be documented (KDoc)
- No `FIXME` or `STOPSHIP` comments

**Test assertions:**
- **Kotlin tests**: Use assertk (JUnit assertions are forbidden by detekt)
- **Java tests**: Use JUnit assertions

**Documentation:**
- Always document nullable/optional parameters — explain what `null` means
- Use backticks for literals in KDoc: `` `null` ``, `` `0` ``

## Architecture

## Code Conventions

- **Null-safety**: Java uses jspecify `@NullMarked` package annotation
- **Kotlin tests**: Use assertk (JUnit assertions forbidden by detekt)
- **Java tests**: Use JUnit assertions

## Custom Gradle Plugins (buildSrc)

- `java-conventions`: Java 21 toolchain, JUnit parallel tests, reproducible builds
- `kotlin-conventions`: Extends java-conventions, adds Spotless/Detekt
- `publish-conventions`: Maven Central publishing with signing
