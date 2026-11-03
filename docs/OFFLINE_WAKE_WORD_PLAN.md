# KIVYA Offline Wake Word: Integration Plan

## Goal
Build two separate, testable wake-word tracks:
1. Hindi demo model: use a published Hindi sample keyword only to validate microphone capture, ONNX inference, thresholds, latency, and battery use.
2. Custom phrase: train/evaluate a dedicated “Hey Kivya” model and compare it against the Hindi demo under quiet, normal-room, and media-playback conditions.

## Engine
The upstream `voicute/onnx-wakeword` Android demo documents ONNX Runtime Android 1.26.0, 16 kHz mono PCM input, a mel-spectrogram ONNX asset, classifier ONNX asset(s), and a `model_info.json` manifest. Its Android sample is a standalone app, not a drop-in SDK. KIVYA must adapt the audio capture, inference lifecycle, and detection pipeline to its own Kotlin/Compose app rather than copying the demo Activity wholesale.

## Model asset gate
Do not ship a model until all of these are checked:
- exact model file and manifest match;
- model's training data / language / phrase and intended use are documented;
- model license and redistribution terms are verified;
- SHA-256 and source URL are recorded;
- model runs against the exact upstream engine version;
- false-positive and false-negative behavior is tested on-device.

The Hindi sample is a validation model, not proof that “Hey Kivya” works. A custom phrase requires a separately trained model. No model is currently bundled in this repository.

## Android privacy and lifecycle
- Wake-word monitoring is OFF by default and starts only after an explicit user action while KIVYA is visible.
- Show an ongoing foreground-service notification while microphone capture is active, with a clear Stop action.
- Request RECORD_AUDIO at runtime.
- Handle microphone contention, app stop, service stop, phone restart, and permission revocation.
- Do not claim guaranteed always-on operation. Android/OEM battery management and foreground-service rules can stop or restrict background microphone use.
- Do not upload microphone audio. Wake-word inference should remain local.

## Milestones
1. Build foundation: ONNX Runtime dependency added; no microphone background service or model assets yet.
2. Demo model validation: add verified Hindi sample bundle and foreground test screen; report measured results.
3. Custom “Hey Kivya”: train, validate, and package the model only after licensing and quality checks.
4. Background mode: foreground microphone service with explicit ON/OFF and persistent notification.
5. Battery / false-trigger testing on the target Realme Android device.

## Current status
Milestone 1 only. The app's existing push-to-talk speech recognition remains unchanged. “Hey Kivya” detection is not active yet.
