# TheAlyss CloudStream Repository

Official CloudStream plugin repository maintained by **TheAlyss**. This repository builds and distributes custom CloudStream extensions and streaming providers.

---

## Features

- **CloudStream Plugin Support**: Full compatibility with the modern CloudStream 3 plugin system.
- **Multi-Provider Support**: Scalable multi-module Gradle architecture allowing multiple providers to be added effortlessly.
- **Automated CI/CD Builds**: Automated GitHub Actions workflow to build `.cs3` binaries on push.
- **Binary Artifacts (`.cs3`)**: Auto-packaged and compressed plugin binaries.
- **Dynamic Metadata (`plugins.json`)**: Automatically generated plugin manifests.
- **GitHub Hosting**: Hosted directly on GitHub via raw content URLs.

---

## Installation in CloudStream

To add this plugin repository to your CloudStream application:

1. Open **CloudStream** on your Android device.
2. Go to **Settings** > **Extensions**.
3. Select **Add Repository**.
4. Enter the Repository URL:
   ```
   https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/builds/repo.json
   ```
5. Tap **Add**. You can now install and update plugins directly within CloudStream.

---

## Included Plugins & Providers

| Plugin / Provider | Package | Status | Supported Types |
| :--- | :--- | :---: | :--- |
| **Alyss Test Provider** | `com.thealyss.cloudstream` | OK (1) | Movies, TV Series |

---

## Local Development & Setup

### Prerequisites
- **JDK 17** or **JDK 21** installed and configured in `JAVA_HOME`.
- **Android SDK** (API 35 / Build Tools).
- **Git**.

### Cloning the Repository
```bash
git clone https://github.com/TheAlyss/cloudstream-AlyssRepo.git
cd cloudstream-AlyssRepo
```

### Building Plugins Locally

Use the included Gradle wrapper to build plugin binaries (`.cs3` files):

#### Windows (PowerShell / Command Prompt):
```powershell
.\gradlew.bat make
```

#### Linux / macOS:
```bash
chmod +x gradlew
./gradlew make
```

The compiled `.cs3` files will be placed inside the `build/` directory of each provider submodule (e.g. `AlyssTestProvider/build/AlyssTestProvider.cs3`).

### Generating Repository Metadata (`plugins.json`)

To generate or update the `plugins.json` manifest file locally:

```powershell
.\gradlew.bat makePluginsJson
```

### Adding a New Provider

1. Create a new directory in the project root (e.g. `MyNewProvider`).
2. Add a `build.gradle.kts` file inside the new folder with your provider metadata:
   ```kotlin
   version = 1

   cloudstream {
       description = "Description of my new provider"
       authors = listOf("TheAlyss")
       status = 1
       tvTypes = listOf("Movie", "Anime")
   }
   ```
3. Create your Kotlin source files inside `MyNewProvider/src/main/kotlin/com/thealyss/cloudstream/`:
   - An entry point class extending `Plugin()` annotated with `@CloudstreamPlugin`.
   - A provider class extending `MainAPI()`.
4. Run `.\gradlew.bat make` to compile the new provider. The dynamic `settings.gradle.kts` will automatically include the new submodule.

---

## How GitHub Actions CI/CD Works

1. When code is pushed to the `main` or `master` branch, GitHub Actions executes `.github/workflows/build.yml`.
2. The workflow checks out the repository source code and the `builds` branch.
3. It compiles all submodules with Java 17 and generates the `.cs3` binaries and updated `plugins.json`.
4. The generated binaries and metadata are automatically committed and pushed to the `builds` branch.

---

## License

This repository is maintained for personal extension development for CloudStream.
