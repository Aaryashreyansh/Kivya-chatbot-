# KIVYA Android Personal Assistant

KIVYA is an Android-first personal voice assistant built with Kotlin and Jetpack Compose.

## Current development features

- **Voice assistant:** user-enabled local "Hey KIVYA" keyword listener, foreground-service notification, notification Stop action, and a talk button.
- **Hindi + English:** choose Hindi (hi-IN) or English (en-IN) for speech recognition. Spoken replies select Hindi or English TTS based on the response script.
- **Offline AI:** Qwen3 0.6B INT4 runs locally through LiteRT-LM after the user downloads the approximately 347 MB model. The model is not bundled in the APK.
- **AI memory:** optional, user-controlled chat history and explicitly saved notes stored in app-private SharedPreferences on this device. Memory can be paused. Saved notes can be listed or deleted.
- **Phone control:** allow-listed Android actions such as opening apps/settings, alarms, contacts and calls. Contact access and direct calling request Android permissions. SMS/WhatsApp messages are opened for user review; KIVYA does not silently send them.
- **Routines:** locally saved, limited command sequences with an allow-list. Communication and payment commands are blocked in routines.

## Installable test APK

The GitHub Actions workflow builds app-debug.apk, runs emulator tests, and uploads a separate artifact named kivya-debug-apk-<run number> when the full workflow succeeds.

1. Open the repository's Actions tab.
2. Open the latest successful KIVYA Functional Tests run.
3. Under Artifacts, download kivya-debug-apk-<run number>.
4. Extract the ZIP and install app-debug.apk on an Android device. Allow installation from the browser/file manager if Android asks.

This is a debug-signed development APK, not a Play Store release. A private release keystore and production signing process are required before public distribution.

## First-time setup

1. Install the APK and open KIVYA.
2. Grant microphone permission only if you want voice input or the wake-word listener.
3. Grant notification permission so Android can show the ongoing microphone-use notification and its Stop action.
4. Choose Hindi or English in Voice Language.
5. To use Offline AI, tap DOWNLOAD · 347 MB while online. Keep the app open until model installation completes, then enable OFFLINE ON. The first model download needs a stable connection and sufficient free storage. Later offline text replies run on-device.
6. Use AI MEMORY to pause or resume local chat-history saving. Notes are saved only when you explicitly say/type "याद रखो: ..." or "remember: ...".

## Build prerequisites

- JDK 17
- Android SDK 35 and platform tools
- Gradle 8.9
- The checksum-verified Sherpa-ONNX 2.0.1 Android AAR in app/libs/sherpa-onnx-2.0.1.aar
- The keyword-spotting model assets in app/src/main/assets/kivya-kws-en/

The CI workflow downloads and verifies the Sherpa-ONNX runtime and prepares the wake-word model before running gradle assembleDebug.

## Privacy and Android limitations

- Saved memory is local to this app installation. It is stored in app-private preferences, not encrypted by this feature; do not save passwords, financial details, or other secrets.
- Online chat mode sends the current prompt and recent conversation context to the configured KIVYA chat endpoint. Offline mode sends prompts to the on-device model instead.
- Android controls background microphone use, foreground-service startup, and whether an app may open its screen from the background. The ongoing notification is intentional and includes a Stop action. Screen-off wake-word operation must be verified on the target phone and may be affected by battery restrictions.
- The selected Hindi/English voice-recognition service is provided by Android and may require its own language pack or internet connection. Offline text generation does not automatically make speech recognition offline.
- The current APK is a development build. Device-specific behavior, voice accuracy, and long-running background operation still need physical-phone testing.

## CI validation

The KIVYA Functional Tests workflow builds the APK, boots an Android emulator, checks the main UI, verifies wake-word foreground-service lifecycle and notification Stop behavior, and uploads diagnostics. A separate opt-in workflow-dispatch input can run the large offline-model test.
