# GoBe Rokid Monitor

Small Android/Kotlin scaffold for a Rokid smart-glasses display that shows HEALBE GoBe U-style health metrics on a 640x480 black-and-green screen.

## What is included

- Fullscreen portrait activity for Rokid glasses. This matches the tested device's natural 480x640 orientation and avoids the 90-degree rotated view.
- Minimal black background and green monospace text UI with compact status bars.
- Custom launcher icon matching the black/green glasses monitor theme.
- Runtime BLE permission handling for Android 12+ and older Android versions.
- `HealthDataProvider` interface so the HEALBE SDK is isolated behind one adapter.
- `BroadcastHealthDataProvider` so phone/PC/SDK bridge values can update the glasses display without showing fake values.
- `UdpHealthDataProvider` so the phone bridge can push values over Wi-Fi.
- The Rokid monitor automatically broadcasts a `request=latest` packet when launched. The phone bridge replies from its cached HEALBE values without requiring the phone UI to be opened.
- `BridgeForegroundService` keeps the phone bridge alive in the background as a `connectedDevice` foreground service, so the phone can answer Rokid requests after the bridge has been started once.
- `BridgeBootReceiver` restarts the phone bridge after reboot or app update when Android allows it.
- `DirectGoBeBleBridge`, an SDK-free BLE route based on HEALBE's officially shared External API v1.0b document.
- `SimulatedHealbeProvider` remains available for development, but it is not the default provider.
- Official HEALBE SDK notes from `healbesdk 1.4.0-beta01` public Dokka docs.
- `phonebridge/libs/*.aar` / `*.jar` support so the official SDK can be bundled once obtained.
- `HealbeSdkAvailability` detection in the phone bridge.
- `OfficialHealbeSdkBridge`, a reflection-based official SDK data path that activates automatically when the SDK is bundled.
- No AndroidX dependency in the app code; it uses platform `Activity` and permission APIs.
- Gradle wrapper is included. Create `local.properties` locally for your Android SDK path; it is intentionally ignored by git.

## Project Layout

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/goberokid/MainActivity.kt
app/src/main/java/com/example/goberokid/healbe/HealthDataProvider.kt
app/src/main/java/com/example/goberokid/healbe/HealthSnapshot.kt
app/src/main/java/com/example/goberokid/healbe/SimulatedHealbeProvider.kt
app/src/main/java/com/example/goberokid/healbe/HealbeSdkProvider.kt
app/src/main/res/layout/activity_main.xml
```

## Official HEALBE SDK

The official public documentation was checked:

https://healbesdk.bitbucket.io/index.html

It documents `healbesdk 1.4.0-beta01`, including:

- `HealbeSdk.init(context)`
- `HealbeSdk.get()`
- `HealbeSdk.GOBE`
- `HealbeSdk.HEALTH_DATA`
- `GoBe.scan()`, `GoBe.connect()`, `GoBe.isReady`, `GoBe.sensorId`
- `HealthData.hydrationState`, `currentStressLevel`, `currentStressState`, `currentNeuroData`, and other health data methods

The installed official HEALBE Android app also contains `com/healbe/healbesdk/...` class names. That confirms this SDK family is used by HEALBE, but Android does not let this bridge link against another app's internal classes. The official AAR/JAR still has to be added to this project.

Place the official SDK file in:

```text
phonebridge/libs/
```

The phone bridge build already includes local `*.aar` and `*.jar` files from that folder.

See `docs/healbe-sdk-notes.md` for the checked API surface and integration direction.

When the SDK file is available, `OfficialHealbeSdkBridge` automatically attempts to:

1. `HealbeSdk.init(context)`.
2. `HealbeSdk.get().GOBE` for scan/connect/default wristband state.
3. `HealbeSdk.get().HEALTH_DATA` for metrics.
4. Subscribe to hydration, stress, heart, and energy streams by reflection.
5. Map SDK values into `HealthPayload`:
   - water balance percent
   - water/hydration state
   - energy balance kcal
   - steps today
   - pulse bpm
   - stress level
   - battery percent
   - updated timestamp

Until the official SDK binary is bundled, the app uses the current HEALBE app screen-capture and notification fallback. That fallback still runs through the same background bridge service; the manual fields in the phone app are only a test/backup path.

## Official External BLE API

The Google Drive folder shared by HEALBE was also checked:

```text
!_Healbe API docs officially shared
```

It contains:

- `Healbe GoBe API v1.0b ENG`
- `Healbe User Data description v1.0a ENG`
- `Healbe API Terms of Use`

The External API document defines an SDK-free BLE protocol:

- Data service UUID: `1bb21a49-4762-4939-9d50-868ddec2cb34`
- Data characteristic UUID: `34e6c128-c04e-4e98-98c6-3bf0b196c3fe`
- PIN service UUID: `c7c49271-4a41-4223-aa5b-e9cfd76bd862`
- PIN characteristic UUID: `f84b607f-af90-4505-b035-bf55e5c0f5c4`
- Packet format starts with ASCII `<#`, followed by command and length bytes as hex text.

`DirectGoBeBleBridge` now uses that route to scan for GoBe, authenticate with the saved PIN, and poll:

- `CMD_GET_SENSOR_STATE` / type `0x01` for running state and battery
- `CMD_GET_HEART_INFO` / type `0x12` for current heart rate
- `CMD_GET_STRESS_LEVEL` / type `0x09` for current stress
- `CMD_GET_FULL_SUMMARY` / type `0x11` for today's steps and energy balance
- `CMD_GET_SHORT_SUMMARY_EX` / type `0x2A` for latest minute heart/stress/hydration state

The first run needs Android Bluetooth permissions and the GoBe PIN saved in the phone bridge. After that, the bridge service can keep polling GoBe and cache values for Rokid.

## Send Values From ADB

While the app is running, push values into the glasses display with:

```powershell
adb -s <ROKID_DEVICE_ID> shell am broadcast `
  -a com.example.goberokid.UPDATE_HEALTH `
  --ei water 83 `
  --ei energy -240 `
  --ei steps 6240 `
  --ei pulse 72 `
  --es stress Low `
  --ei battery 91 `
  --es source PHONE
```

To test the phone bridge path, send the same values to the phone app and let it forward over UDP:

```powershell
adb -s <PHONE_DEVICE_ID> shell am broadcast `
  -a com.example.goberokidbridge.SEND_HEALTH `
  --es host <ROKID_IP_ADDRESS> `
  --ei water 83 `
  --ei energy -240 `
  --ei steps 6240 `
  --ei pulse 72 `
  --es stress Low `
  --ei battery 91 `
  --es source PHONE
```

## Phone Bridge Runtime

The intended runtime is:

1. Start `GoBe Rokid Bridge` on the phone once. It starts `BridgeForegroundService`.
2. The bridge opens UDP request port `45455` and starts the official SDK adapter when the SDK is bundled.
3. Start `GoBe Rokid Monitor` on Rokid. It broadcasts `request=latest`.
4. The phone replies to Rokid on port `45454` with the latest cached HEALBE payload.

Capture/source priority:

1. Official HEALBE SDK through `OfficialHealbeSdkBridge`, when the official SDK AAR/JAR is present.
2. Direct GoBe BLE through `DirectGoBeBleBridge`, when Bluetooth permissions and the GoBe PIN are available.
3. Accessibility screen reader for `com.healbe.healbegobe`, when enabled in Android Accessibility settings.
4. Notification listener for `com.healbe.healbegobe`, when enabled in Notification Access settings.
5. Manual entry or ADB broadcast, only for testing and emergency fallback.

When Rokid Monitor starts, it sends a UDP request on port `45455`. Bridge replies to port `45454` using the latest saved payload. Opening the phone app is not part of the normal glasses flow once the bridge service is running.

## Build

Create a local `local.properties` file that points to your Android SDK:

```properties
sdk.dir=C\:\\path\\to\\android-sdk
```

Then build:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --offline assembleDebug
```

## Related Note

See `../Healbe_Rokid_App_Spec_clean.md` for the cleaned Japanese specification and practical route comparison from the pasted Gemini conversation.
