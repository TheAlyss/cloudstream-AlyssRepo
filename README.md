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

### Option 1: 1-Click Install (Mobile Browser)

If you are reading this on your device with CloudStream installed:

[👉 **Add Repository to CloudStream**](cloudstream://https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/main/repo.json)

---

### Option 2: Manual Setup

1. Open **CloudStream** on your Android device.
2. Go to **Settings** > **Extensions**.
3. Select **Add Repository**.
4. Enter the Repository Manifest URL:
   ```
   https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/main/repo.json
   ```
   *(Or direct plugins list: `https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/builds/plugins.json`)*
5. Tap **Add**. You can now install and update **Filmapik** and **AlyssTest** directly within CloudStream.

---

## Included Plugins & Providers

| Plugin / Provider | Package | Status | Supported Types |
| :--- | :--- | :---: | :--- |
| **Filmapik Provider** | `com.thealyss.cloudstream.filmapik` | OK (1) | Movies, TV Series, K-Drama, Anime |
| **Alyss Test Provider** | `com.thealyss.cloudstream` | OK (1) | Movies, TV Series |

---

## How GitHub Actions CI/CD Works

1. When code is pushed to the `main` or `master` branch, GitHub Actions executes `.github/workflows/build.yml`.
2. The workflow checks out the repository source code and the `builds` branch.
3. It compiles all submodules with Java 17 and generates the `.cs3` binaries and updated `plugins.json`.
4. The generated binaries and metadata are automatically committed and pushed to the `builds` branch.

---

## License

This repository is maintained for personal extension development for CloudStream.
