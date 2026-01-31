# Micronaut Utils Version Catalog

A Gradle version catalog that provides type-safe accessors for the Micronaut utility libraries.

## Usage

### 1. Add the catalog to your project

In your `settings.gradle.kts`:

```kotlin
versionCatalogs {
  create("pkwareMicronautUtils") {
    from("com.pkware.micronaut-utils:catalog:1.0.0")
  }
}
```

### 2. Reference libraries in your dependencies

Once configured, use the type-safe accessors in your `build.gradle.kts`:

```kotlin
dependencies {
  implementation(pkwareMicronautUtils.assistedInject)
  testImplementation(pkwareMicronautUtils.junitJupiter)
}
```
