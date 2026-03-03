# APKUpdater OSS

*[🇷🇺 Читать на русском](README.md)*

**APKUpdater OSS** is an open-source tool that simplifies the process of finding updates for your installed apps. It provides functionality similar to an app store but aggregates updates across multiple independent sources.

This project is a heavily modified and modernized fork of the original [APKUpdater](https://github.com/rumboalla/apkupdater) created by [rumboalla](https://github.com/rumboalla). Huge thanks to the original author for the brilliant foundation!

## 🌟 What's New

* **RuStore integration** – fetch APKs and release notes directly from the official Russian store.
* **Custom Git repositories** – add GitHub/GitLab projects, specify package names, regex filters and treat them like first-class sources.
* **Material Design 3 UI** – refreshed navigation, typography, cards, smooth animations and dynamic color support.
* **Safe package rename** – ships as `com.apkupdateross`, so it happily co-exists with the original APKUpdater.

## ✨ Core Features
* **Update sources**: **APKMirror**, **Aptoide**, **F-Droid**, **IzzyOnDroid**, **APKPure**, **GitLab**, **GitHub**, **Google Play**, **RuStore**, plus your custom Git repos.
* **App search** across supported stores.
* **Background scheduling** with configurable intervals (1 / 3 / 7 days) and notification reminders.
* **Install modes**: Session (standard), Root, and Shizuku.
* **Android TV UI** and support for Android 7.0 (24) through Android 14 (34).
* **Themes**: light, dark, system and Material You dynamic colors.
* **Direct / root installs** without ads or tracking.

## 📥 Download
Head over to the **Releases** section of this repository to download the latest Stable build (`com.apkupdateross-release.apk`).

## 💻 Build from Source

```bash
./gradlew assembleRelease
```

Use `./gradlew assembleDebug` for debug builds.

## ☕ Support Development

If you enjoy APKUpdater OSS, you can support the project via CloudTips: [cloudtips.ru/p/4d69001b](https://pay.cloudtips.ru/p/4d69001b)

## 🌍 Translations
Currently the app ships with **English** and **Russian** locales. New features land in these languages first.

## 📜 License
Copyright &copy; 2026

Original APKUpdater Copyright &copy; 2016-2024 rumboalla. 

Licensed under the [GNU General Public License v3](https://www.gnu.org/licenses/gpl-3.0.en.html).
