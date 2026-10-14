# Plugin Template

---

## Introduction

This repository is a skeleton for Minecraft server plugins. It contains no application logic by default and is intended to be extended with your own implementation. Despite being a skeleton, it is fully functional as a build foundation: you can use it to create any type of plugin, regardless of scope or feature set.

The template is preconfigured with Java 25 via Gradle Toolchain, Gradle Kotlin DSL, JUnit 5, automated `plugin.yml` generation, Maven publishing with optional GPG signing, and standardized tooling (`.editorconfig`, `.gitignore`, `.sdkmanrc`, and shared IntelliJ IDEA settings). Server API support is handled in one of two ways: by placing server JAR files in `.local_libraries/`, or by resolving Paper directly from a Maven repository. The template is prepared for team collaboration within the **mcdtm.pl** ecosystem and enforces a consistent development environment across contributors.

### Why Java 25?

Java 25 is used intentionally, not as an oversight. The mcdtm.pl organization is standardizing on Java 25 across its entire plugin and project structure, and the target server environment already runs on Java 25. Minecraft server software itself has no issues running on this version. Since Java 25 is the latest release, it will be the de facto standard by the time the organization's new structure is complete. New projects should therefore be built against Java 25 from the start.

---

## Requirements

| Dependency | Version | Notes |
| :--- | :--- | :--- |
| JDK | **25** | Required by the organization's standardization policy. Recommended: Temurin or Oracle. SDKMAN!: `sdk install java 25.x.x-tem` |
| IDE | IntelliJ IDEA (recommended) | Any modern Java IDE will work; shared IDEA settings are included in `.idea/` |
| Git | Any recent | — |

---

## Quick Start

```bash
# 1. Create a new repository from this template
#    (GitHub → "Use this template")

# 2. Clone your new repository
git clone <your-repo-url>
cd <your-repo>

# 3. Add the server API JAR
mkdir -p .local_libraries
cp /path/to/paper-api.jar .local_libraries/

# 4. Build the project
./gradlew build
```

The compiled artifact will be placed in `build/libs/`.

### Alternative: Maven-based Paper dependency

If you prefer **not** to use local server JAR files, you can resolve Paper directly from a Maven repository instead:

1. Add the PaperMC Maven repository to `settings.gradle.kts`:

   ```kotlin
   dependencyResolutionManagement {
       repositories {
           mavenCentral()
           maven("https://repo.papermc.io/repository/maven-public/")
       }
   }
   ```

2. Create `gradle/libs.versions.toml` and define the Paper dependency:

   ```toml
   [versions]
   paper = "1.21.x-R0.1-SNAPSHOT"

   [libraries]
   paper-api = { module = "io.papermc.paper:paper-api", version.ref = "paper" }
   ```

3. Reference it in `build.gradle.kts`:

   ```kotlin
   dependencies {
       compileOnly(libs.paper.api)
   }
   ```

With this approach, the `.local_libraries/` directory becomes unnecessary.

---

## Quick Setup

Make the following changes after creating your repository from the template:

| # | File | What to change |
| :--- | :--- | :--- |
| 1 | `gradle.properties` | Set `group`, `version`, `description` |
| 2 | `src/main/resources/plugin.yml` | Update `name` and `main` (fully qualified main class, e.g. `com.example.myplugin.MyPlugin`) |
| 3 | `src/main/java/` | Rename package structure to match the new `group` value |
| 4 | `.local_libraries/` | Create this directory and place the server API `.jar` here (e.g. `paper-api.jar`) — or switch to the Maven approach described above. This directory is listed in `.gitignore` and must not be committed. |
| 5 | `settings.gradle.kts` | Update `rootProject.name` if applicable |

### `gradle.properties` example

```properties
group=com.example.myplugin
version=1.0.0
description=Short description of your plugin
```

### `plugin.yml` example

```yaml
name: MyPlugin
version: ${version}
main: com.example.myplugin.MyPlugin
api-version: '1.21'
```

### How `${version}` works

The `${version}` placeholder in `plugin.yml` is not a Bukkit feature — it is a Gradle resource-expansion token. During the build, `processResources` in `build.gradle.kts` replaces `${version}` (and any other declared placeholders) with values from `gradle.properties` before the file is packaged into the final JAR. This keeps the version in `plugin.yml` in sync with the project version automatically.

The relevant Gradle configuration looks like this:

```kotlin
tasks.processResources {
    filesMatching("plugin.yml") {
        expand(
            "version" to project.version,
            "description" to project.description
        )
    }
}
```

If you add more placeholders (e.g. `${author}`), declare them in the `expand(...)` map. Otherwise they will be left as literal text in the output `plugin.yml`.

### Minimal main class example

Create `src/main/java/<your/package>/MyPlugin.java`:

```java
package com.example.myplugin;

import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("MyPlugin enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MyPlugin disabled.");
    }
}
```

Make sure the class name and package match the `main` entry in `plugin.yml`.

---

## Useful Commands

| Command | Description |
| :--- | :--- |
| `./gradlew build` | Compiles sources, runs tests, and produces the JAR in `build/libs/` |
| `./gradlew clean` | Removes the `build/` directory |
| `./gradlew test` | Runs unit tests only |
| `./gradlew projectInfo` | Prints project configuration summary (version, Java, Gradle) |
| `./gradlew publishToMavenLocal` | Installs the built artifact into the local Maven repository |

---

## Project Structure

```text
.
├── .idea/                     # Shared IntelliJ IDEA configuration
├── gradle/                    # Gradle Wrapper files
├── src/
│   ├── main/
│   │   ├── java/              # Plugin source code
│   │   └── resources/         # plugin.yml, configuration files
│   └── test/                  # Unit tests (JUnit 5)
├── .editorconfig              # Shared code style rules
├── .gitattributes             # Git attribute rules (line endings, binary handling)
├── .gitignore                 # Git exclusions
├── .sdkmanrc                  # SDKMAN! Java version pin
├── build.gradle.kts           # Main Gradle build script
├── gradle.properties          # Project metadata (group, version, description)
├── gradlew                    # Gradle Wrapper (Unix)
├── gradlew.bat                # Gradle Wrapper (Windows)
├── jitpack.yml                # JitPack publishing configuration
├── LICENSE                    # Project license (MIT)
├── README.md                  # This file
├── settings.gradle.kts        # Gradle project settings
└── build/                     # Build output (generated, gitignored)
```

> **Note:** The `.local_libraries/` directory is not tracked by Git and does not exist in a freshly cloned repository. It must be created manually before building — see section 3.

---

## Troubleshooting

### `UnsupportedClassVersionError` when loading the plugin

The server is running an older JDK than the one the plugin was compiled against. This template targets Java 25 by design — the server must also run on Java 25. Upgrade the server's runtime or rebuild the plugin against a lower toolchain (only if the organization's policy allows it).

### `Could not find or load main class` / plugin fails to load

- Verify that `main` in `plugin.yml` matches the fully qualified class name (package + class).
- Verify that the class extends `JavaPlugin`.
- Verify that `api-version` in `plugin.yml` matches the target server version.

### `package org.bukkit does not exist` during build

The server API is not on the compile classpath.

- **Local JAR approach:** place the API `.jar` (e.g. `paper-api.jar`) in `.local_libraries/`.
- **Maven approach:** ensure the PaperMC repository is declared in `settings.gradle.kts` and `libs.paper.api` is added to `dependencies` in `build.gradle.kts`.

### `${version}` appears literally in `plugin.yml` inside the built JAR

The `processResources` task is not configured to expand `plugin.yml`, or the placeholder name does not match the key in the `expand(...)` map. See section 4 for the required Gradle configuration.

### `.local_libraries/` is empty or missing after cloning

This is expected. The directory is gitignored and each developer supplies the server API JAR locally. Create it manually and place the appropriate `.jar` inside before building, or switch to the Maven-based dependency approach.

---

## Contributing & Guidelines

This project is part of the **mcdtm.pl** ecosystem. Before contributing, review the following:

- 📜 [Code of Conduct](CODE_OF_CONDUCT.md) — respectful and substantive communication is mandatory.
- 🔒 [Security Policy](SECURITY.md) — how to report vulnerabilities.

---

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.