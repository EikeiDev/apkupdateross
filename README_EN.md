# APKUpdater OSS

[Русская версия](README.md)

**APKUpdater OSS** is an open-source Android app for finding, downloading, and installing app updates outside a single app store. It checks multiple independent sources, groups matching results by package, and lets you choose the update source that fits your needs.

The project is focused on phones and touch devices. Android TV support has been intentionally removed: the UI, gestures, and install flows are designed for regular phone use.

APKUpdater OSS is a heavily modified fork of the original [APKUpdater](https://github.com/rumboalla/apkupdater) by [rumboalla](https://github.com/rumboalla). It uses the package id `com.apkupdateross`, so it can be installed alongside the original APKUpdater.

## Features

- Check installed apps for updates across multiple sources.
- `Apps`, `Search`, `Updates`, and `Settings` tabs with a modern Material 3 interface.
- Group the same app update from multiple sources into one card.
- Pick a source inside the update card when several sources are available.
- Release type badges on the Updates tab: `Stable`, `Beta`, `Alpha`, `Pre-release`.
- Filters for hiding alpha, beta, and pre-release builds.
- `Update all` button for bulk installation.
- Save APK files to `Downloads/APKUpdaterOSS`.
- Install through the standard Session Installer, Root, or Shizuku.
- Scheduled background update checks with notifications.
- Background auto-download and install when Root or Shizuku mode is used.
- Hide individual updates or whole apps, including configurable swipe controls.
- Custom theme colors for accent, background, cards, and bottom navigation.
- System, dark, and light themes, plus Material You / Dynamic Color support.
- Compact mode and configurable portrait/landscape grid columns.
- Backup and restore settings.
- Copy app lists and logs.
- Built-in self-update dialog with readable changelog formatting.

## Update Sources

| Source | Support |
| --- | --- |
| GitHub | Releases, pre-release flag, custom repositories, optional API token |
| GitLab | Releases, custom repositories, optional API token |
| F-Droid / IzzyOnDroid | Configurable F-Droid repositories and suggested/stable versions |
| Google Play | Search and update metadata through Aurora GPlayApi with device language preferences |
| RuStore | Search, updates, changelog, and direct download links |
| APKMirror | Search, updates, download pages, APKM/split packages, and ABI compatibility checks |
| Aptoide | Search and updates from the public catalog |
| APKPure | Search, updates, and basic release filtering |
| APKCombo | Experimental source with APK variants and compatibility checks |
| Uptodown | Experimental source with APK/XAPK/APKS, direct download, and WebView fallback |
| AppGallery | Experimental Huawei AppGallery source with region selection |

APKCombo, Uptodown, and AppGallery are disabled by default and can be enabled manually in `Settings -> Sources`. Some sources depend on public APIs or HTML pages, so they may temporarily break when a store changes its website or backend.

## Settings

Settings are organized into categories:

- `Sources`: stores, GitHub/GitLab, F-Droid repositories, AppGallery region.
- `Updates`: release filters, check schedule, notifications, metrics, and hidden updates.
- `Install`: Session, Root, or Shizuku install mode.
- `UI`: theme, custom colors, animations, swipes, compact view, and grid columns.
- `Network`: global timeout and Google Play timeout.
- `Data & logs`: export/import settings, app lists, and logs.
- `About`: version, GitHub, donations, and project information.

## Installation

1. Open [Releases](https://github.com/EikeiDev/apkupdateross/releases).
2. Download `com.apkupdateross-release.apk`.
3. Install the APK and allow installs from unknown sources if Android asks.

Minimum Android version: **Android 7.0 Nougat, API 24**.

## Build from Source

JDK 17 and Android SDK are required.

```bash
# Windows
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease

# Linux / macOS
./gradlew assembleDebug
./gradlew assembleRelease
```

Generated APKs are placed in:

```text
app/build/outputs/apk/debug/
app/build/outputs/apk/release/
```

For your own release signing, configure a keystore in `local.properties`. If no signing config is found, local release builds fall back to the debug signing config.

## Security and Limitations

APKUpdater OSS is not an app store and does not host APK files. It gets metadata and downloads from the sources selected by the user. Always check the source before installing, especially when an update does not come from the developer's official store.

In normal mode, Android still shows the system install confirmation. Silent installs are available only through Root or Shizuku. The app does not include ad SDKs or analytics.

## Support Development

If APKUpdater OSS is useful to you, you can support the project:

| Method | Details |
| --- | --- |
| DonationAlerts | [donationalerts.com/r/eikeidev](https://www.donationalerts.com/r/eikeidev) |
| BTC | `12E586ZRxrYDTPy6A1LDHQ1AvZku6Ggx9x` |
| GRAM / TON | `UQDgDs-0LDZFFq54ZR3UtJAIK_oSfT4cmkY-tyjeMSxcSX1i` |
| USDT | `0xc75986e1460cc232f74b9a600b9c0e41dfa644f7` |

Always verify the transfer network in your wallet before sending crypto.

## Translations

The app currently ships with Russian and English locales. New features are localized for these two languages first.

## License

Copyright (c) 2026

Original APKUpdater Copyright (c) 2016-2024 rumboalla.

Licensed under the [GNU General Public License v3](https://www.gnu.org/licenses/gpl-3.0.en.html).
