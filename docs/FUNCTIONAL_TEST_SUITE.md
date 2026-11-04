# KIVYA Functional Test Suite

This suite separates what CI can prove from what still needs a physical Android phone.

## Automated in the default emulator run

| Check | What PASS means | What it does not prove |
|---|---|---|
| Install / launch / UI | APK installs, app starts, key UI controls render | Chat backend quality or voice features |
| Wake service start | With RECORD_AUDIO granted and model assets packaged, Android reports the foreground service running | Correct wake-word detection or false-trigger rate |
| Wake service stop | Tapping Stop exits foreground wake-word mode | Reliability across OEM battery managers or screen-off operation |
| Process survival | App process remains alive after the checks | Long-duration stability |

The wake service test uses the same Sherpa-ONNX runtime and GigaSpeech model preparation as the Android CI workflow. The model is English-based and keyword tokenization does not establish that “Hey KIVYA” is accurately detected. No wake-word accuracy claim is made by this test.

## Optional offline inference test

Run the workflow manually with `run_offline_model_test=true`.

- Downloads the app's Qwen3 0.6B INT4 LiteRT-LM model (approximately 347 MB).
- Waits for the app to report that installation and model loading completed.
- Disables emulator Wi-Fi and mobile data.
- Sends a prompt requesting the exact marker `KIVYA_OFFLINE_OK`.
- Passes only if that marker appears in the conversation.

This is intentionally opt-in because of the large download and emulator CPU/runtime cost. A successful result verifies inference on that emulator image, not performance or memory use on every phone.

## Not automatically proven by this suite

- Hindi speech recognition and text-to-speech quality: Android emulator speech services/audio routing are not representative of a physical handset.
- Wake-word detection accuracy: requires controlled audio samples, positive/negative phrase sets, and measured false accept / false reject rates.
- Background microphone behavior after screen-off, app swipe-away, reboot, permission revocation, and OEM battery restrictions.
- Real phone actions such as calls, SMS, WhatsApp, Wi-Fi, and Bluetooth. These require a test device, test SIM/accounts, explicit permissions, and safe confirmation flows.

## Physical Android phone checklist

Record the exact phone model, Android version, app version, and test date. Test in a quiet room and a normal room, with screen on and off:

1. Grant and revoke microphone permission; confirm the app responds safely.
2. Tap Hey KIVYA and confirm the persistent notification appears.
3. Say “Hey KIVYA” repeatedly, then say similar phrases that should not trigger it. Record detections and false triggers.
4. Lock the screen, wait several minutes, and repeat. Repeat after battery optimization is enabled.
5. Tap Stop and confirm the microphone indicator/notification goes away.
6. Download the offline model on Wi-Fi, enable Offline mode, turn off all network access, and send several Hindi and English prompts.
7. Test voice recognition and TTS separately.
8. Test device actions only with a test contact/number and confirm every action before sending or placing a call.

Never mark a feature PASS merely because its button exists or the app displays a “listening” status string. Attach the workflow artifact and device logs to any reported PASS.
