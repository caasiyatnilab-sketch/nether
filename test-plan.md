# Test Plan — PR #3 (nether audit fixes)

Environment: Android emulator `nether_test` (android-34, x86_64, 2 cores, ~2.4 GB RAM),
debug APK installed (`com.example/.MainActivity`). Recorded GUI run + shell evidence.

The PR's headline claims: **the app now compiles & runs** (it didn't before — UI referenced
~30 missing ViewModel members) and **encryption is real** (Keystore AES/GCM round-trip, not
AES/ECB-with-alias-as-key + silent plaintext fallback). Tests are designed so a broken version
would look visibly different.

## T1 — App launches and renders (compile/runtime reconciliation)
Steps: Cold-launch the app. Observe the bottom nav + the Chat terminal screen.
- PASS: App opens to a screen with the terminal log box; a SYSTEM line
  "🚀 Nether initialized..." and a SECURITY line "✅ Android Keystore AES/GCM key active" appear.
- FAIL: Crash / blank screen / ANR.
Why adversarial: the pre-PR code does not compile, so no runnable APK could show this at all;
a missing ViewModel member would throw at composition.

## T2 — Chat encryption round-trip survives a process restart (CORE)
Steps:
1. On Chat, type `audit-roundtrip-12345` into the prompt box and tap Send.
2. Observe a USER line with that text and an assistant reply line appear.
3. Force-stop the app (`adb shell am force-stop com.example`) and relaunch.
4. Observe the prior USER + assistant lines reload from the encrypted DB.
- PASS: After relaunch, the assistant reply text reloads as **readable plaintext** (the same
  text shown before), NOT the string `[Decryption Failed - Unauthorized Key Signature]`.
- FAIL: Reloaded line shows `[Decryption Failed...]` or `[Encryption Failed]` or empty text.
Why adversarial: history is decrypted via the Keystore key on load (LocalAiViewModel.kt:123).
If GCM/Keystore wiring were broken, the reloaded message would render the decrypt-failure marker.

## T3 — Stored data is actually ciphertext (no silent plaintext) [shell evidence]
Steps: After T2, pull the DB rows via `run-as`:
`adb shell run-as com.example sqlite3 databases/SecureAI.db "select encryptedText from ..."`
(or hexdump the db file and grep).
- PASS: The `encryptedText` column does NOT contain the literal assistant reply substring; it is
  Base64 of (IV‖ciphertext‖tag). The reply plaintext does not appear in the DB file.
- FAIL: The literal reply text appears in the DB (old code's plaintext fallback behavior).

## T4 — Dashboard shows REAL hardware (DeviceHelper wiring)
Steps: Navigate to the Dashboard tab. Read the "HARDWARE ALLOCATION MATRIX" card.
- PASS: "CPU Allocation" reads **"2 Core ARM NEON Ready"** and "System RAM Bounds" reads
  **"2.4 GB Hardware Limit"** (matching the emulator's 2 cores / 2.42 GB) — NOT the previously
  hardcoded "8 Core" / "12.0 GB".
- FAIL: Shows 8 Core / 12.0 GB (hardcoded), or 0 / 0.0.
Why adversarial: values are emulator-specific; the old hardcoded constants would be plainly wrong.

## T5 — GGUF unpacker simulation runs end-to-end (new ViewModel state block)
Steps: On Dashboard, tap "Import" on the GGUF NATIVE UNPACKER card, confirm/trigger extraction in
the dialog, and watch the progress + NDK logs.
- PASS: A progress bar advances to 100%, streaming `[NDK] ...` log lines appear, then a result
  panel shows "Compile JNI Validation: Passed ✅", a non-zero "On-Device Speed: N T/s", and
  integrity checks ("Magic bytes: GGUF ✓", etc.).
- FAIL: Button does nothing / no progress / validation never passes.
Why adversarial: exercises `triggerGgufExtractionAndTest` + 8 new StateFlows the UI binds to;
if any were missing the screen wouldn't compile/update.

## Out of scope (regression only, test if time)
- RAG indexing/retrieval and LoRA training sims (also new ViewModel methods) — note if exercised.
- Real llama.cpp inference: intentionally still simulated; the assistant reply is a stub.
