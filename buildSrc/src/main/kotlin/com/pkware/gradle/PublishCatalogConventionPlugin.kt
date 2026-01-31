package com.pkware.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

/**
 * Plugin for publishing Gradle version catalogs.
 *
 * Adapts the standard publishing convention for version catalogs, which use the
 * `versionCatalog` component instead of the `java` component and don't require
 * Javadoc or sources jars.
 */
class PublishCatalogConventionPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit = target.run {
    apply<MavenPublishPlugin>()

    configure<PublishingExtension> {
      publications {
        register<MavenPublication>("mavenCatalog") {
          // Version catalog component is registered by the version-catalog plugin
          val versionCatalogComponent = components.findByName("versionCatalog")
          if (versionCatalogComponent != null) {
            from(versionCatalogComponent)
          }
          pom {
            name.set(pomName)
            description.set(pomDescription)
            packaging = pomPackaging
            url.set("https://github.com/pkware/norm")

            organization {
              name.convention("PKWARE, Inc.")
              url.convention("https://www.pkware.com")
            }

            developers {
              developer {
                id.set("all")
                name.set("PKWARE, Inc.")
              }
            }

            scm {
              connection.set("scm:git:git://github.com/pkware/norm.git")
              developerConnection.set("scm:git:ssh://github.com/pkware/norm.git")
              url.set("https://github.com/pkware/norm")
            }

            licenses {
              license {
                name.set("MIT License")
                distribution.set("repo")
                url.set("https://github.com/pkware/norm/blob/main/LICENSE")
              }
            }
          }
        }
      }
      repositories {
        maven {
          name = "MavenCentral"
          url = uri(if (version.toString().isReleaseBuild) releaseRepositoryUrl else snapshotRepositoryUrl)
          credentials {
            username = repositoryUsername
            password = repositoryPassword
          }
        }
      }
    }

    val isCiServer = System.getenv().containsKey("CI")
    if (isCiServer) {
      pluginManager.apply(SigningPlugin::class.java)
      configure<SigningExtension> {
        // Signing credentials are stored as secrets in GitHub.
        // See https://docs.gradle.org/current/userguide/signing_plugin.html#sec:signatory_credentials for more information.

        useInMemoryPgpKeys(
          signingKeyId,
          signingKey,
          signingPassword,
        )

        val publishExtension = extensions.getByType<PublishingExtension>()
        val publication = publishExtension.publications.findByName("mavenCatalog")
        if (publication != null) {
          sign(publication)
        }
      }
    }
  }
}
