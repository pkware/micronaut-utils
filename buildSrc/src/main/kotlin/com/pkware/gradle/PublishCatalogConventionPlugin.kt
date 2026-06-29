package com.pkware.gradle

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.VersionCatalog
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/**
 * Configures the version-catalog module to publish to Maven Central via the vanniktech
 * maven-publish plugin. Uses the `versionCatalog` software component (packaging `toml`) instead of
 * the `java` component. Signing is applied only on CI.
 */
class PublishCatalogConventionPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit = target.run {
    apply(plugin = "com.vanniktech.maven.publish.base")

    configure<MavenPublishBaseExtension> {
      configure(VersionCatalog())
      configureCommonPublishing(this@run)
    }
  }
}
