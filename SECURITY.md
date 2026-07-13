# Security

## Supported Use

This is an experimental Android bridge for local personal-device use. Debug builds are not intended for production distribution.

## Reporting

Please report security issues privately to the repository owner rather than opening a public issue with sensitive details.

## Notes

- Do not commit `local.properties`, signing keys, official SDK binaries, APKs, screenshots with health data, or device logs.
- Official HEALBE SDK AAR/JAR files are intentionally ignored by git. Add them locally under `phonebridge/libs/` only if your license allows it.
- The local UDP bridge should be used only on trusted networks.
- The app does not require a server token, cloud API key, or GitHub secret.
