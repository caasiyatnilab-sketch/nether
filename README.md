# Native-Llama Android Orchestrator

An Android application that demonstrates a native C++ JNI bridge with deterministic on-device demo responses alongside dynamic PC-fallback capabilities. The checked-in native layer does not currently parse, load, or memory-map GGUF model weights.

---

## 🛠️ Performance & Architectural Design

### 1. Dual-Inference Execution Pipeline
* **Local NDK Demo Engine**: Binds to a C++ bridge (`native-lib.cpp`) that keeps a small deterministic demo state for JNI lifecycle testing. It does not include real `llama.cpp` model/context state or GGUF memory mapping yet.
* **On-Demand Native Context Release (`freeContext`)**: The Jetpack Compose architecture binds the ViewModel lifecycle to an explicit C++ cleanup function. When the orchestrator's `LocalAiViewModel` is cleared, it triggers `LlamaCppNative.freeContext()`, which frees the small native demo context and duplicated model-path string.
* **Graceful Network Fallback Protocol**: JNI calls are completely isolated with dynamic `try-catch` blocks catching `UnsatisfiedLinkError` and `OutOfMemoryError`. If a native allocation fails or the local binary is unavailable:
  1. The pipeline automatically shifts the active query to the local PC-hosted **Ollama / LM Studio API**.
  2. If the PC network link is also unreachable, it routes to a **True Local Core Fallback Engine** to process structured queries with pre-cached index maps.

### 2. Physical Footprint Optimization (APK Size: ~4.8MB)
* **Targeted ABI Filtering**: Restricts compiled native binaries to modern 64-bit architectures (`arm64-v8a` and `x86_64`), stripping out heavy, obsolete libraries.
* **Code Shrinking & R8 Optimization**: Employs ProGuard/R8 code minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) to purge unused classes and resource identifiers, yielding a highly compact APK size ideal for rapid distribution and limited hardware.

### 3. On-Device SQLite Vector RAG (Retrieval-Augmented Generation)
* Integrates a local sandbox SQLite Vector Storage space that matches queries to indexed system files, contextual documents, and mock corporate lists seamlessly, enriching prompt execution directly within on-device sandboxing.

---

## 📱 Physical Device Testing & Verification Guide

Follow these steps to deploy and test the native Orchestrator on physical Android devices:

### 1. Build and Export the APK
* In the Google AI Studio build platform, locate the top navigation or settings bar.
* Click **Download APK** (or triggering Gradle `:app:assembleRelease`).
* Safely transfer the generated APK file to your physical Android device via USB, Google Drive, or local storage.

### 2. Enable USB Debugging / Developer Mode
* On your physical Android phone/tablet, navigate to **Settings** -> **About Phone**.
* Tap **Build Number** 7 times to reveal the Developer Options.
* Go back to Settings, enter **Developer Options**, and make sure **Install via USB** or **Allow Unknown Sources** (under App manager/Settings) is enabled.

### 3. Install the APK
* Open your phone's File Manager and tap the transferred APK file.
* Confirm installation. Because this is a custom-built engineering build, Google Play Protect may warn you of an unsigned or unknown developer—select **"Install Anyway"** to load the sandbox applet.

### 4. Connect to Your Host PC's Ollama Server (Fallback Testing)
* Ensure your computer and your physical phone are connected to the **same Wi-Fi network**.
* Start your local Ollama server on your computer. Make sure it binds to all network interfaces (bind to `0.0.0.0` by setting the environment variable `OLLAMA_HOST=0.0.0.0` or checking your LM Studio network permissions).
* Find your computer's local IP address (e.g., `192.168.1.150`).
* Launch the **Native-Llama Orchestrator** on your phone.
* Navigate to the **Utilities and Tuning** screen, and enter the server endpoint URL (e.g., `http://192.168.1.150:11434`).
* Tap **Test Connection**. Once verified, you can pull downloaded models or route fallback prompts dynamically from your phone to your PC!

---

## ⚙️ Technical Specifications & File Map

* `/app/src/main/cpp/native-lib.cpp`: C++ JNI bridge for deterministic demo responses, direct demo-context management, and pointer-safe cleanup routines. Real GGUF loading should replace the demo state with actual `llama.cpp` model/context objects before the app claims local GGUF inference.
* `/app/src/main/cpp/CMakeLists.txt`: Compiles the native NDK library, linking basic logging capabilities for hardware metrics tracking.
* `/app/src/main/java/com/example/data/LlamaCppNative.kt`: Kotlin JNI declaration bridge hosting safe wrapper methods and fallback error catches.
* `/app/src/main/java/com/example/viewmodel/LocalAiViewModel.kt`: Core execution controller hosting pipeline fallback redirecting logic and VM destruction hooks.
* `/app/build.gradle.kts`: Production-ready build configurations maintaining compact target architectures and asset shrinking profiles.

---
The codebase is ready for demo deployment, with real GGUF-backed inference still requiring an actual `llama.cpp` integration.
