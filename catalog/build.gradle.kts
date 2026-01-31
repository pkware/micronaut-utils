import com.pkware.gradle.PublishConventionPlugin

plugins {
  `version-catalog`
  `publish-catalog-conventions`
}

catalog {
  versionCatalog {
    version("micronaut-utils", project.version.toString())

    rootProject.childProjects.forEach { (key, value) ->
      value.plugins.all {
        if (this is PublishConventionPlugin) {
          library(key, "$group", key).versionRef("micronaut-utils")
        }
      }
    }
  }
}
