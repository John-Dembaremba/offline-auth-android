# Offline Auth Sample App

This module is a minimal Android application used to verify that `offline-auth-android` can be consumed by a normal Android project.

It is not intended to be a production application.

## Purpose

The sample app is used to validate:

- consumption through `project(":offlineauth")`
- consumption through the generated release AAR
- public API accessibility
- Android Keystore integration
- required core library desugaring
- release build compatibility
- future R8/ProGuard validation

## Current Validation

The sample app has successfully built using:

```text
project(":offlineauth")
```

and:

```text
offlineauth-release.aar
```

## Core Library Desugaring

Applications consuming `offline-auth-android` must enable core library desugaring.

Example:

```kotlin
android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    coreLibraryDesugaring(
        "com.android.tools:desugar_jdk_libs:2.1.5",
    )
}
```

## Build

From the repository root:

```bash
./gradlew :sample-app:assembleDebug
```

## Library Usage

The intended entry point is:

```kotlin
val manager = OfflineAuthManager.create(context)
```

The manager provides:

```kotlin
manager.getPublicKey()
manager.sync(snapshot)
manager.authenticate(username, password)
```

## Release Testing

Before publishing a release, the sample app should be built against the generated release AAR to confirm that the packaged artifact is consumable outside the library module.
