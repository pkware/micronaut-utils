package com.pkware.gradle

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.base.TestingExtension

/**
 * Plugin for projects that support Kotlin.
 */
class KotlinConventionsPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit = target.run {
    apply<JavaConventionsPlugin>()
    pluginManager.apply("org.jetbrains.kotlin.jvm")

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val ktlintVersion = libs.findVersion("ktlint").get().toString()

    apply<SpotlessPlugin>()
    configure<SpotlessExtension> {
      kotlin {
        target("src/**/*.kt")
        ktlint(ktlintVersion)
      }
      kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
      }
    }

    apply<DetektPlugin>()
    configure<DetektExtension> {
      buildUponDefaultConfig = true
      parallel = true
      config.from("$rootDir/detekt.yml")
    }
    configurations.named("detektPlugins") {
      dependencies.add(project.dependencies.create("com.pkware.detekt:import-extension:1.2.0"))
    }

    configure<TestingExtension> {
      suites.withType<JvmTestSuite>().configureEach {
        dependencies {
          implementation(libs.findLibrary("assertk").get().get().toString())
        }
      }
    }
  }
}
