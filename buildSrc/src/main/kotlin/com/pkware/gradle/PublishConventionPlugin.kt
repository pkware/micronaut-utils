package com.pkware.gradle

import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/**
 * Configures a library module to publish to Maven Central via the vanniktech maven-publish plugin.
 *
 * Java modules publish a real javadoc jar; Kotlin modules publish an empty one (their public API is
 * Kotlin and the javadoc task has no public Java types to document). Signing is applied only on CI.
 */
class PublishConventionPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit = target.run {
    apply(plugin = "com.vanniktech.maven.publish.base")

    configure<MavenPublishBaseExtension> {
      val javadocJar = if (plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
        JavadocJar.Empty()
      } else {
        JavadocJar.Javadoc()
      }
      configure(JavaLibrary(javadocJar = javadocJar, sourcesJar = SourcesJar.Sources()))
      configureCommonPublishing(this@run)
    }
  }
}
