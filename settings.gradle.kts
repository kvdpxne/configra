pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }

  plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradleup.nmcp.settings") version "2.6.1"
  }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

  repositories {
    mavenCentral()
    maven("https://jitpack.io") {
      content {
        includeGroupAndSubgroups("com.github.kvdpxne")
      }
    }
  }
}

include(
  "api",
  "core",
  "escape",
  "exceptions",
  "schema",
  "spi",
  // Formats
  "formats:ini",
  "formats:properties",
  // Extras
  "extras:diff",
  "extras:io",
  "extras:watch",
  "extras:util"
)

rootProject.name = "properties"
include("options")