# Micronaut Utils

[![Maven Central](https://img.shields.io/maven-central/v/com.pkware.micronaut-utils/assisted-inject)](https://central.sonatype.com/namespace/com.pkware.micronaut-utils)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A collection of utility libraries for the [Micronaut](https://micronaut.io/) ecosystem.

## Modules

| Module                                              | Description                                                                                  |
|-----------------------------------------------------|----------------------------------------------------------------------------------------------|
| [assisted-inject](assisted-inject)                  | Assisted injection — factory interfaces that combine injected services with runtime parameters |
| [hikari-health](hikari-health)                      | Health indicator for HikariCP connection pools with detailed pool statistics                  |
| [junit-jupiter](junit-jupiter)                      | JUnit 5 extensions that catch common @MicronautTest lifecycle configuration mistakes         |
| [security-grpc](security-grpc)                      | gRPC server interceptor that enforces Micronaut Security @Secured rules on gRPC methods      |
| [mock-oauth2-test-resource](mock-oauth2-test-resource) | Micronaut test-resources provider that starts a mock OAuth2 server for test and dev environments |
| [security-grpc-processor](security-grpc-processor)  | Annotation processor required by security-grpc to read @Secured metadata at compile time     |

## Installation

### Using the version catalog (recommended)

Add the catalog to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
  versionCatalogs {
    create("pkwareMicronautUtils") {
      from("com.pkware.micronaut-utils:catalog:<version>")
    }
  }
}
```

Then reference modules by name in `build.gradle.kts`:

```kotlin
dependencies {
  implementation(pkwareMicronautUtils.assistedInject)
  implementation(pkwareMicronautUtils.hikariHealth)
  testImplementation(pkwareMicronautUtils.junitJupiter)

  // gRPC security requires both the runtime library and the annotation processor
  implementation(pkwareMicronautUtils.securityGrpc)
  annotationProcessor(pkwareMicronautUtils.securityGrpcProcessor)  // Java
  // ksp(pkwareMicronautUtils.securityGrpcProcessor)               // Kotlin KSP
}
```

### Using coordinates directly

```kotlin
// build.gradle.kts
dependencies {
  implementation("com.pkware.micronaut-utils:assisted-inject:<version>")
  implementation("com.pkware.micronaut-utils:hikari-health:<version>")
  testImplementation("com.pkware.micronaut-utils:junit-jupiter:<version>")

  // gRPC security requires both the runtime library and the annotation processor
  implementation("com.pkware.micronaut-utils:security-grpc:<version>")
  annotationProcessor("com.pkware.micronaut-utils:security-grpc-processor:<version>")  // Java
  // ksp("com.pkware.micronaut-utils:security-grpc-processor:<version>")               // Kotlin KSP
}
```

## Requirements

- Java 21+
- Micronaut 4.x

## License

[MIT](LICENSE) — PKWARE, Inc.

## Releasing
Releases are automated via [release-please](https://github.com/googleapis/release-please). As `feat`
and `fix` commits accumulate on `main`, release-please maintains an open release PR that
bumps the version in `gradle.properties`. Merging that PR causes release-please to
push a semver tag, which triggers the publish workflow to sign and publish artifacts to Maven Central
and create a GitHub Release with auto-generated release notes.

Every push to `main` also publishes the current `-SNAPSHOT` version to the Maven Central snapshot
repository at `central.sonatype.com/repository/maven-snapshots/`.
