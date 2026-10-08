# Interactions Compose Library (KMP)

[![JitPack](https://jitpack.io/v/com.github.connecttag/interactions-compose-lib-kmp.svg)](https://jitpack.io/#com.github.connecttag/interactions-compose-lib-kmp)

A unified **Kotlin Multiplatform (KMP)** Compose library providing cross-platform file picking, document exporting, permission launchers, and location resolution coordinators for **Android**, **iOS**, and **JVM (Desktop)**.

---

## Features

- **PlatformFilePickerLauncher**: Cross-platform launcher to pick single or multiple files (`PlatformFilePickerRequest`, `PlatformFilePickerResult`).
- **PlatformDocumentExporter & PlatformEncryptedDocumentExporter**: Export plain or encrypted documents directly to user storage.
- **PlatformPermissionLauncher & PlatformPermissionRuntime**: Unified permission handling for Camera, Location, Audio, and Notifications across Android and iOS with Moko integration.
- **ForegroundLocationAccessCoordinator**: Robust coordination between location permissions, device GPS settings, and accuracy requirements.
- **PlatformSettingsLauncher**: Open application system settings or specific setting screens cross-platform.

---

## Installation

### 1. Add JitPack repository
In your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add dependency
In your module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.connecttag.interactions-compose-lib-kmp:interactions-compose-lib-kmp:1.0.0")
        }
    }
}
```

---

## License

```text
Copyright 2026 Connect Tag

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
