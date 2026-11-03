# Kivya Android Assistant

Kivya is an Android-first voice assistant starter with two modes:
- Phone Assistant: speech-to-text, spoken replies, opening supported Android settings and web destinations.
- AI Tools: reserved for authenticated GitHub/AWS/backend actions.

## Build
Open this repository in Android Studio (JDK 17, Android SDK 35) or run:
`gradle assembleDebug`

The GitHub Actions workflow builds a debug APK on every push to main.

## Current scope
The first milestone includes a Compose UI, Hindi speech recognition, Android TextToSpeech, permission-aware microphone access, and a small allow-listed phone command router.

GitHub/AWS actions are not wired yet. They require a backend with user authentication, narrowly scoped credentials, explicit confirmation for writes/deployments, and server-side audit logs. Never place cloud keys or GitHub tokens in the APK.

This is an initial development build, not a production-ready Siri replacement. Android restricts background listening and privileged device controls; supported actions must use Android APIs and user-granted permissions.
