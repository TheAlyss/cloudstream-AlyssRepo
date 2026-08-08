# CloudStream Plugin Architecture Research

This document details the architecture, build system, metadata formats, and official conventions for creating a CloudStream plugin repository, based on the current official CloudStream documentation and template repositories.

---

## 1. Primary Official References & Resources
- **Official Documentation Website**: [https://recloudstream.github.io/](https://recloudstream.github.io/)
- **Official GitHub Organization**: [https://github.com/recloudstream](https://github.com/recloudstream)
- **Official Plugin Template Repository**: [https://github.com/recloudstream/TestPlugins](https://github.com/recloudstream/TestPlugins)
- **Official CloudStream Gradle Plugin**: `com.github.recloudstream:gradle`

---

## 2. Recommended Architecture & Build Components

### 2.1 Project Structure
A CloudStream plugin repository is structured as a multi-module Kotlin/Android Gradle project:

```
cloudstream-AlyssRepo/
├── .github/
│   └── workflows/
│       └── build.yml                 # Automated CI/CD workflow for plugin builds
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── AlyssTestProvider/                # Example plugin submodule
│   ├── build.gradle.kts              # Submodule plugin configuration & metadata
│   └── src/
│       └── main/
│           └── kotlin/
│               └── com/
│                   └── thealyss/
│                       └── cloudstream/
│                           ├── AlyssTestPlugin.kt    # Entry point annotated with @CloudstreamPlugin
│                           └── AlyssTestProvider.kt  # Extends MainAPI for provider logic
├── build.gradle.kts                  # Root Gradle build script
├── settings.gradle.kts               # Dynamic module registration script
├── gradle.properties                 # JVM & Gradle configurations
├── gradlew                           # Unix wrapper script
├── gradlew.bat                       # Windows wrapper script
├── repo.json                         # Repository entry point manifest
└── README.md                         # Repository documentation
```

### 2.2 Modern Versioning & Tooling Stack
- **Android Gradle Plugin (AGP)**: `8.7.3`
- **Kotlin Plugin**: `2.1.0`
- **CloudStream Gradle Plugin**: `com.github.recloudstream:gradle:-SNAPSHOT`
- **JDK Requirement**: Java 17 / Java 21 (Target JVM Bytecode: 11 / 17)
- **Maven Repositories**: `google()`, `mavenCentral()`, and `https://jitpack.io` (JitPack hosts CloudStream Gradle dependencies).

---

## 3. Dynamic Submodule Organization
Multiple providers are organized as independent submodules in root level folders.
In `settings.gradle.kts`, the root project dynamically discovers all directories containing a `build.gradle.kts` file and includes them automatically:

```kotlin
rootProject.name = "CloudstreamPlugins"

val disabled = listOf<String>()

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach(block)
}
```

---

## 4. Provider Class & Plugin Entry Point Architecture

### 4.1 Plugin Entry Point (`Plugin`)
Every CloudStream plugin module requires a entry-point class marked with `@CloudstreamPlugin` extending `com.lagradost.cloudstream3.plugins.Plugin`. When the plugin is loaded by CloudStream, `load(context: Context)` is executed:

```kotlin
package com.thealyss.cloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AlyssTestPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AlyssTestProvider())
    }
}
```

### 4.2 Main Provider Logic (`MainAPI`)
Provider logic inherits from `com.lagradost.cloudstream3.MainAPI`. It overrides mandatory metadata fields and core functions:
- `mainUrl`: Base website URL
- `name`: Display name of the provider
- `supportedTypes`: Set of supported `TvType`s (e.g., `TvType.Movie`, `TvType.TvSeries`)
- `search(query: String)`: Handles search queries
- `load(url: String)`: Fetches details/episodes metadata
- `loadLinks(...)`: Resolves playable video links and subtitle links

---

## 5. Plugin Manifest & Metadata Requirements

Each plugin submodule configures its manifest attributes in its `build.gradle.kts` inside the `cloudstream` block:

```kotlin
version = 1

cloudstream {
    description = "Test Provider for Alyss CloudStream Repository"
    authors = listOf("TheAlyss")
    status = 1 // 0: Down, 1: Ok, 2: Slow, 3: Beta-only
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/builds/icon.png"
}
```

---

## 6. Build Process & Metadata Generation

1. **Building `.cs3` Plugin Binaries**:
   Running `./gradlew make` or `./gradlew :AlyssTestProvider:make` compiles Kotlin code and packages the plugin bytecode along with manifest info into a zip file with `.cs3` extension (`build/AlyssTestProvider.cs3`).
2. **Generating `plugins.json`**:
   Running `./gradlew makePluginsJson` scans generated `.cs3` files and generates `plugins.json` containing version numbers, checksums, authors, and download links.

---

## 7. Repository Manifest Formats

### 7.1 `repo.json` Format
This is the root repository entry point added into CloudStream app settings:

```json
{
  "name": "TheAlyss CloudStream Repository",
  "description": "Official CloudStream plugin repository by TheAlyss",
  "manifestVersion": 1,
  "pluginLists": [
    "https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/builds/plugins.json"
  ]
}
```

### 7.2 `plugins.json` Format
Generated automatically by `makePluginsJson` task. Contains metadata for each built plugin:
```json
[
  {
    "name": "Alyss Test Provider",
    "pluginClassName": "com.thealyss.cloudstream.AlyssTestPlugin",
    "version": 1,
    "url": "https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/builds/AlyssTestProvider.cs3",
    "description": "Test Provider for Alyss CloudStream Repository",
    "authors": ["TheAlyss"],
    "status": 1,
    "tvTypes": ["Movie", "TvSeries"]
  }
]
```

---

## 8. GitHub Actions Automated Build Architecture
The automated workflow monitors pushes to `main`/`master`, checks out the source code (`src`) and the target `builds` branch (`builds`), runs Gradle build tasks, updates artifacts in `builds`, and commits back to GitHub:

1. Checkout `main` branch to `./src`
2. Checkout `builds` branch to `./builds`
3. Setup JDK 17 / 21
4. Execute `./gradlew make makePluginsJson`
5. Copy `.cs3` outputs and `plugins.json` into `./builds/`
6. Git commit & push changes to `builds` branch.
