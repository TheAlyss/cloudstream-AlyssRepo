# 🎬 TheAlyss CloudStream Repository

[![Build Plugins](https://github.com/TheAlyss/cloudstream-AlyssRepo/actions/workflows/build.yml/badge.svg)](https://github.com/TheAlyss/cloudstream-AlyssRepo/actions/workflows/build.yml)
![CloudStream](https://img.shields.io/badge/CloudStream-v3.0+-blue.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

Official CloudStream plugin repository maintained by **TheAlyss**. This repository builds and distributes custom [CloudStream 3](https://github.recloudstream.org) extensions and streaming providers.

---

## ✨ Features

- **CloudStream Plugin Support**: Full compatibility with the modern CloudStream 3 plugin system.
- **Multi-Provider Support**: Scalable multi-module Gradle architecture allowing multiple providers to be added effortlessly.
- **Automated CI/CD Builds**: Automated GitHub Actions workflow to build `.cs3` binaries on push.
- **Binary Artifacts (`.cs3`)**: Auto-packaged and compressed plugin binaries.
- **Dynamic Metadata (`plugins.json`)**: Automatically generated plugin manifests.
- **GitHub Hosting**: Hosted directly on GitHub via raw content URLs.

---

## 📥 Installation in CloudStream

### Option 1: 1-Click Install (Mobile Browser)

If you are reading this on your device with CloudStream installed:

👉 [**Add Repository to CloudStream**](cloudstream://https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/main/repo.json)

---

### Option 2: Manual Setup

1. Open **CloudStream** on your Android device.
2. Go to **Settings** $\rightarrow$ **Extensions**.
3. Select **Add Repository**.
4. Enter the Repository Manifest URL:
   ```text
   https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/main/repo.json
   ```
   *(Or direct plugins list: `https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/builds/plugins.json`)*
5. Tap **Add**. You can now install and update extensions directly within CloudStream.

---

## 🧩 Included Plugins & Providers

| Plugin / Provider | Package | Status | Supported Types |
| :--- | :--- | :---: | :--- |
| **Filmapik Provider** | `com.thealyss.cloudstream.filmapik` | 🟢 Active | Movies, TV Series, K-Drama, Anime |

---

## ⚙️ How GitHub Actions CI/CD Works

1. When code is pushed to the `main` or `master` branch, GitHub Actions executes `.github/workflows/build.yml`.
2. The workflow checks out the repository source code and the `builds` branch.
3. It compiles all submodules with Java 17 and generates the `.cs3` binaries and updated `plugins.json`.
4. The generated binaries and metadata are automatically committed and pushed to the `builds` branch.

---

## 📜 License

This repository is maintained for personal extension development for CloudStream.
