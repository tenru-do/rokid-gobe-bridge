# Privacy

GoBe Rokid Monitor is designed to keep health data local to the user's devices.

- The project does not include captured HEALBE data, device serial numbers, local SDK paths, or APK build artifacts in source control.
- Runtime health values are cached on the Android device using private app storage.
- The bridge has no cloud backend and does not upload health data.
- Rokid communication uses local-network UDP between the user's phone and glasses.
- The GoBe PIN, when entered, is stored only in the phone app's private preferences.
- Accessibility and notification access are optional fallback routes. Enable them only if the direct BLE or official SDK route is not available.

Before publishing logs, screenshots, or issue reports, remove health values, device IDs, IP addresses, and personal account details.
