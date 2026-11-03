# Kivya Android Assistant

Kivya is an Android-first voice assistant starter with two modes:
- Phone Assistant: speech-to-text, spoken replies, opening supported Android settings and web destinations.
- AI Tools: reserved for authenticated GitHub/AWS/backend actions.

## Build
Open this repository in Android Studio (JDK 17, Android SDK 35) or run:
`gradle assembleDebug`

The GitHub Actions workflow builds a debug APK on every push to main.

## Offline wake-word roadmap

Kivya is preparing an on-device wake-word path using ONNX Runtime for Android. The implementation will be developed in two tracks: (1) validate an existing Hindi demo keyword model on-device, then (2) train/evaluate a dedicated “Hey Kivya” model and compare false-trigger and missed-trigger rates. The app does not yet listen for a wake word in the background. Model assets and inference/audio-capture integration are intentionally not included until their provenance, model format, licensing, and Android build are verified. Background microphone use must be user-enabled, visible through an ongoing notification, and comply with Android foreground-service restrictions.

## Current scope
The first milestone includes a Compose UI, Hindi speech recognition, Android TextToSpeech, permission-aware microphone access, and a small allow-listed phone command router.

GitHub/AWS actions are not wired yet. They require a backend with user authentication, narrowly scoped credentials, explicit confirmation for writes/deployments, and server-side audit logs. Never place cloud keys or GitHub tokens in the APK.

This is an initial development build, not a production-ready Siri replacement. Android restricts background listening and privileged device controls; supported actions must use Android APIs and user-granted permissions.
