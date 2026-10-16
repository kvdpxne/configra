plugins {
  `maven-publish`
  signing
}

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "maven-publish")
  apply(plugin = "signing")

  group = providers.gradleProperty("group").get()
  version = providers.gradleProperty("version").get()
  description = providers.gradleProperty("description")
    .getOrElse("${rootProject.name} ${project.name}")

  extensions.configure<JavaPluginExtension> {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
    withJavadocJar()
  }

  dependencies {
    "testImplementation"(rootProject.libs.junit.jupiter)
    "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }

  tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
      encoding = "UTF-8"
      addBooleanOption("Xdoclint:none", true)
      addStringOption("Xdoclint:none", "-quiet")
    }
  }

  extensions.configure<PublishingExtension> {
    publications {
      create<MavenPublication>("mavenJava") {
        from(components["java"])

        pom {
          name.set("${rootProject.name}-${project.name}")
          description.set(project.description)
          url.set(providers.gradleProperty("url").get())

          licenses {
            license {
              name.set(providers.gradleProperty("license.name").get())
              url.set(providers.gradleProperty("license.url").get())
            }
          }
          developers {
            developer {
              id.set(providers.gradleProperty("developer.id").get())
              name.set(providers.gradleProperty("developer.name").get())
            }
          }
          scm {
            connection.set(providers.gradleProperty("scm.connection").get())
            developerConnection.set(
              providers.gradleProperty("scm.developerConnection").get()
            )
            url.set(providers.gradleProperty("scm.url").get())
          }
        }
      }
    }
  }

  extensions.configure<SigningExtension> {
    val signingKey = providers.gradleProperty("signing.secretKey").orNull
    val signingPassword =
      providers.gradleProperty("signing.password").orNull

    if (signingKey != null && signingPassword != null) {
      useInMemoryPgpKeys(signingKey, signingPassword)
      sign(the<PublishingExtension>().publications["mavenJava"])
    }
  }

  tasks.named("publish") {
    dependsOn(tasks.named("test"))
  }
}

tasks.register("publishAll") {
  group = "publishing"
  description = "Publishes every subproject to the configured repository."
  dependsOn(subprojects.map { "${it.path}:publish" })
}