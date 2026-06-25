---
name: testing-nether-android
description: Test the Nether Android app (com.example, "Local AI Studio Orchestrator") end-to-end on an emulator. Use when verifying UI/crypto/hardware/GGUF changes, e.g. encryption round-trip, Dashboard hardware values, or the GGUF unpacker flow.
---

# Testing Nether (Android Compose app)

Package `com.example`, launcher `com.example/.MainActivity`. Three bottom-nav tabs:
**Dashboard**, **Terminal** (chat), **Utilities**. Inference is intentionally simulated
(stub reply), so don't test for "real" model output — test the surrounding plumbing.

## Build & install
```bash
export ANDROID_HOME=~/Android/Sdk
export PATH=$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH
cd /home/ubuntu/repos/nether
./gradlew :app:assembleDebug            # NDK builds arm64-v8a + x86_64
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example/.MainActivity
```
If `/dev/kvm` perms block the emulator: `sudo chmod 666 /dev/kvm`.
Start a clean run: `adb shell pm clear com.example` (wipes the encrypted DB so old rows
don't confuse round-trip tests).

## Emulator gotchas (likely to recur)
- **Software-GPU SystemUI ANR loop**: the launcher (NOT the app) throws "System UI isn't
  responding" dialogs. Suppress with `adb shell settings put global hide_error_dialogs 1`.
  The app renders fine behind the dialogs.
- **VM restarts kill screen recordings.** Recordings don't survive an environment restart,
  and the editor heavily compresses idle time (a multi-minute session can render as ~15s).
  Treat screenshots as primary evidence and the recording as a supplement. Capture a
  screenshot after every meaningful state change so you never depend solely on the video.
- **Coordinate scaling**: the `computer` tool screenshots may be ~705px wide while the real
  display is 1080px (scale ~1.53). Prefer `adb shell input tap X Y` with REAL-display
  coordinates, or compute `real = screenshot_coord * (1080/screenshot_width)`.
- **Typing**: GUI taps to type are unreliable. Tap the field via GUI to focus, then
  `adb shell input text "your_text"`, then `adb shell input keyevent 111` (ESC) to dismiss
  the soft keyboard.

## The 5 core checks (see test-plan.md for full adversarial rationale)
- **T1 App launch**: Terminal shows SECURITY "✅ Android Keystore AES/GCM key active" +
  SYSTEM "🚀 Nether initialized...". Proves it compiles (UI once referenced ~30 missing
  ViewModel members).
- **T2 Encryption round-trip (core)**: Terminal → type a unique string → Send → reply
  appears → `adb shell am force-stop com.example` → relaunch → the reply must reload as
  **readable plaintext**, NOT `[Decryption Failed - Unauthorized Key Signature]`.
- **T3 Ciphertext on disk (shell)**: pull DB and scan for leaks:
  ```bash
  adb exec-out run-as com.example cat databases/SecureAI.db > /tmp/SecureAI.db
  adb exec-out run-as com.example cat databases/SecureAI.db-wal > /tmp/SecureAI.db-wal
  sqlite3 /tmp/SecureAI.db "select sender, substr(encryptedText,1,50), substr(encryptionKeyUsedHash,1,16) from chat_history;"
  grep -a -c "your_unique_string" /tmp/SecureAI.db /tmp/SecureAI.db-wal   # expect 0
  ```
  `encryptedText` is Base64(IV‖ciphertext‖tag): 12-byte IV + CT + 16-byte GCM tag (a 164-char
  base64 ≈ 123 bytes). NOTE: the `sender` LABEL column (e.g. "DeepSeek R1 (1.5B)") is stored
  in the clear by design — only message *content* is encrypted, so a grep for a model name
  may hit 1 (the label), which is expected; grep for the actual message text instead.
- **T4 Real hardware**: Dashboard "HARDWARE ALLOCATION MATRIX" must reflect the emulator
  (e.g. "1 Core ARM NEON Ready" / "2.4 GB Hardware Limit"), NOT hardcoded "8 Core" / "12.0 GB".
  Values are device-specific — read the emulator's actual cores/RAM and match.
- **T5 GGUF unpacker**: Dashboard → GGUF NATIVE UNPACKER → Import → confirm. Progress bar +
  streaming `[NDK]` logs → result "Compile JNI Validation: Passed ✅", non-zero "On-Device
  Speed: N T/s", integrity checks (Magic bytes GGUF ✓, etc.).

## Recording
Maximize first: `sudo apt-get install -y wmctrl 2>/dev/null; wmctrl -r :ACTIVE: -b add,maximized_vert,maximized_horz`.
Annotate with `annotate_recording` (setup / test_start / assertion). Do the force-stop+relaunch
for T2 *inside* the recording so the plaintext reload is on camera.

## Devin Secrets Needed
None — the app runs fully offline on a local emulator; no external credentials required.
