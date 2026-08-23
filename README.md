# 🌟 TheAlyss CloudStream Repository

[![Build Plugins](https://github.com/TheAlyss/cloudstream-AlyssRepo/actions/workflows/build.yml/badge.svg)](https://github.com/TheAlyss/cloudstream-AlyssRepo/actions/workflows/build.yml)
![CloudStream](https://img.shields.io/badge/CloudStream-v3.0+-blue.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

Official CloudStream plugin repository maintained by **TheAlyss**. This repository builds and distributes custom [CloudStream 3](https://github.com/recloudstream/cloudstream) extensions and streaming providers.

---

## 📲 Cara Pasang / Installation in CloudStream

### Kaedah 1: 1-Click Install (Mobile / Android)
Klik butang di bawah dari telefon pintar anda yang mempunyai aplikasi CloudStream:

[![Add to CloudStream](https://img.shields.io/badge/CloudStream-Add%20Repository-blue?style=for-the-badge&logo=android)](cloudstreamrepo://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/builds/repo.json)

---

### Kaedah 2: Guna Shortcode
1. Buka **CloudStream** ➡️ **Settings** ➡️ **Extensions**.
2. Tekan **Add Repository**.
3. Masukkan **Shortcode** atau **Short URL**:
   ```text
   alyss
   ```
   *(atau `https://py.md/alyss`)*
4. Tekan **Add**.

---

### Kaedah 3: URL Penuh Manual (Raw URL)
```text
https://raw.githubusercontent.com/TheAlyss/cloudstream-AlyssRepo/builds/repo.json
```

---

## 📦 Included Plugins & Providers

| Plugin / Provider | Package | Status | Supported Types |
| :--- | :--- | :---: | :--- |
| **Filmapik Provider** | `com.thealyss.cloudstream.filmapik` | 🟢 Active | Movies, TV Series, K-Drama, Anime |
| **MovieBox Provider** | `com.thealyss.cloudstream.moviebox` | 🟢 Active | Movies, TV Series, K-Drama, C-Drama, Anime |

---

## ⚙️ How GitHub Actions CI/CD Works

1. Apabila kod di-*push* ke cawangan `main`, GitHub Actions akan menjalankan `.github/workflows/build.yml`.
2. Workflow mengkompilasi modul-modul provider dan menjana fail binari `.cs3` serta `plugins.json`.
3. Hasil binaan disimpan secara automatik di cawangan `builds` untuk edaran pantas.

---

## 📜 License

This repository is maintained for personal extension development for CloudStream.
