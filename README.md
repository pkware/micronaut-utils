# Micronaut Utils

[![Maven Central](https://img.shields.io/maven-central/v/com.pkware.micronaut/assisted-inject)](https://central.sonatype.com/namespace/com.pkware.micronaut)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A collection of utility libraries for the [Micronaut](https://micronaut.io/) ecosystem.

## Modules

| Module | Description |
|--------|-------------|
| [assisted-inject](assisted-inject/) | Assisted injection pattern for Micronaut — create objects that combine DI services with runtime parameters |

## Installation

Add the modules you need to your build:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.pkware.micronaut:assisted-inject:1.0.0")
}
```

```groovy
// build.gradle
dependencies {
    implementation 'com.pkware.micronaut:assisted-inject:1.0.0'
}
```

## Requirements

- Java 21+
- Micronaut 4.x

## License

[MIT](LICENSE) — PKWARE, Inc.