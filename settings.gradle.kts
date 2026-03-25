enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "micronaut-utils"

include(
  "assisted-inject",
  "assisted-inject-test-java",
  "assisted-inject-test-ksp",
  "catalog",
  "hikari-health",
  "junit-jupiter",
  "mock-oauth2-test-resource",
  "security-grpc",
  "security-grpc-processor",
)

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
  }
}

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("com.gradle.develocity") version "4.4.0"
  id("io.micronaut.platform.catalog") version "4.6.2"
}

val isCiServer = System.getenv().containsKey("CI")

develocity {
  buildScan {
    termsOfUseUrl = "https://gradle.com/terms-of-service"
    termsOfUseAgree.set("yes")
    publishing.onlyIf { _ -> false }
    if (isCiServer) {
      tag("CI")
    }
  }
}
