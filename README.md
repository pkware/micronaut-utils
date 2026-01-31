# Micronaut Utils

[![Maven Central](https://img.shields.io/maven-central/v/com.pkware.micronaut/assisted-inject)](https://central.sonatype.com/namespace/com.pkware.micronaut)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A collection of utility libraries for the [Micronaut](https://micronaut.io/) ecosystem.

## Modules

| Module                             | Description                                                                                                |
|------------------------------------|------------------------------------------------------------------------------------------------------------|
| [assisted-inject](assisted-inject) | Assisted injection pattern for Micronaut — create objects that combine DI services with runtime parameters |
| [junit-jupiter](junit-jupiter)     | Test helpers to make using Micronaut in testing easier                                                     |

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

## Releasing

1. Change the relevant version in `gradle.properties` to a non-SNAPSHOT version.
2. `git commit -am "Release version X.Y.Z."` (where and X.Y.Z is the new version)
3. Push or merge to the main branch.
4. Update `gradle.properties` to the next SNAPSHOT version.
5. `git commit -am "Prepare next development version."`
6. After the merge, tag the release commit on the main branch. `git tag -a X.Y.Z -m "Version X.Y.Z"` (where X.Y.Z is the new version)
7. `git push --tags`.
