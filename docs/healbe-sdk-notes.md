# HEALBE SDK Notes

Checked on 2026-07-13 from the official Dokka site:

https://healbesdk.bitbucket.io/index.html

The public documentation is for `healbesdk 1.4.0-beta01`.

## Important Public API

- `com.healbe.healbesdk.business_api.HealbeSdk`
  - Root SDK class.
  - `init(context)` and `init(context, configuration)` initialize the SDK.
  - `get()` returns the SDK instance.
  - `waitSdk()` emits the SDK instance after initialization.
  - Exposes `GOBE`, `HEALTH_DATA`, `TASKS`, `USER`, and `ALARMS`.
- `HealbeSdk.Configuration.Builder`
  - `setBaseUrl(...)`
  - `setWeightBaseUrl(...)`
  - `setSyncInitialDelay(...)`
  - `setSyncPeriod(...)`
  - `setGroupMinutes(...)`
  - `build()`
- `com.healbe.healbesdk.business_api.gobe.GoBe`
  - `scan()`
  - `set(device)`
  - `connect()`
  - `disconnect()`
  - `isReady`
  - `sensorId`
- `com.healbe.healbesdk.business_api.healthdata.HealthData`
  - `hydrationState`
  - `currentStressLevel`
  - `currentStressState`
  - `currentNeuroData`
  - `lastStressData`
  - `secondsOfWearing(...)`
  - Energy, heart, sleep, stress, water, and neuroactivity data packages are documented.

## What Was Verified Locally

The installed official HEALBE Android app contains `com/healbe/healbesdk/...` class names, including health data, stress, hydration, GoBe, and device API packages. That confirms the documented SDK package family is used by the official app.

This does not make those classes available to `GoBe Rokid Bridge`. Android apps cannot directly link against another installed app's internal classes. The bridge needs the official HEALBE SDK AAR/JAR added to its own build.

## How To Add The SDK

Place the official HEALBE SDK AAR or JAR into:

```text
phonebridge/libs/
```

The Gradle file already includes:

```groovy
dependencies {
    implementation fileTree(dir: "libs", include: ["*.jar", "*.aar"])
}
```

After adding the SDK file, rebuild:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --offline assembleDebug
```

## Implementation Direction

The bridge now contains `OfficialHealbeSdkBridge`, a reflection-based integration layer.

It intentionally has no compile-time dependency on the HEALBE SDK. When an official SDK AAR/JAR is placed in `phonebridge/libs/`, the bridge attempts to:

```kotlin
HealbeSdk.init(context)
val sdk = HealbeSdk.get()
val gobe = sdk.GOBE
val healthData = sdk.HEALTH_DATA
```

Then it subscribes, by reflection, to likely public streams such as:

- `hydrationState`
- `currentStressState`
- `currentStressLevel`
- `currentHeartRate`
- `lastHeartData`
- energy summary methods

When values arrive, it saves a `HealthPayload` and lets `BridgeRequestServer` return that payload to Rokid.

The current screen-capture path should remain as fallback for devices where the SDK is unavailable or authorization is not completed.
