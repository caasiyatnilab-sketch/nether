# Native-Llama Android Orchestrator

An Android application that demonstrates local, on-device AI orchestration using a custom C++ JNI bridge alongside dynamic PC-fallback capabilities.

> **Status:** This is a working scaffold/demo. The native C++ layer is a **simulated `llama.cpp` bridge** (it manages model state and echoes structured output, but does not yet perform real GGUF inference — see `native-lib.cpp`). Real `llama.cpp` linking is stubbed out in `CMakeLists.txt` for a future iteration. The GGUF "unpacker" and LoRA "training" screens are interactive simulations. Chat history is encrypted at rest with an Android Keystore-backed AES/GCM key.

---

## 🛠️ Performance & Architectural Design

### 1. Dual-Inference Execution Pipeline
* **Local NDK Inference Engine**: Binds to a C++ bridge (`native-lib.cpp`) that manages model state via low-level pointers. The inference call is currently a simulated stub that echoes structured output; it is the integration point where real `llama.cpp` will be linked.
* **On-Demand Memory Release (`freeContext`)**: To mitigate Native Heap memory leaks and potential Out-Of-Memory (OOM) crashes, the Jetpack Compose architecture binds the ViewModel lifecycle to an explicit C++ memory deallocation function. When the orchestrator's `LocalAiViewModel` is cleared, it triggers `LlamaCppNative.freeContext()`, which frees and nullifies mapped C++ pointer addresses.
* **Graceful Network Fallback Protocol**: JNI calls are completely isolated with dynamic `try-catch` blocks catching `UnsatisfiedLinkError` and `OutOfMemoryError`. If a native allocation fails or the local binary is unavailable:
  1. The pipeline automatically shifts the active query to the local PC-hosted **Ollama / LM Studio API**.
  2. If the PC network link is also unreachable, it routes to a **True Local Core Fallback Engine** to process structured queries with pre-cached index maps.

### 2. Physical Footprint Optimization (release APK ~2MB)
* **Targeted ABI Filtering**: Restricts compiled native binaries to modern 64-bit architectures (`arm64-v8a` and `x86_64`), stripping out heavy, obsolete libraries.
* **Code Shrinking & R8 Optimization**: Employs ProGuard/R8 code minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) to purge unused classes and resource identifiers, yielding a highly compact APK size ideal for rapid distribution and limited hardware.

### 3. On-Device SQLite Vector RAG (Retrieval-Augmented Generation)
* Integrates a local SQLite (Room) vector store. Documents are embedded into a fixed 128-dimension space using a deterministic hashing embedding, and queries are matched by cosine similarity — entirely on-device. (This is a lightweight stand-in for a learned embedding model.)

---

## 📱 Physical Device Testing & Verification Guide

Follow these steps to deploy and test the native Orchestrator on physical Android devices:

### 1. Build and Export the APK
* Ensure the Android SDK (platform 34, build-tools 34, NDK, CMake) is installed and `local.properties` points at it via `sdk.dir`.
* Build with the Gradle wrapper:
  * Debug: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
  * Release (R8 minified): `./gradlew :app:assembleRelease`
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

* `/app/src/main/cpp/native-lib.cpp`: C++ JNI bridge mapping model allocations, direct context management, text generation algorithms, and pointer-safe cleanup routines.
* `/app/src/main/cpp/CMakeLists.txt`: Compiles the native NDK library, linking basic logging capabilities for hardware metrics tracking.
* `/app/src/main/java/com/example/data/LlamaCppNative.kt`: Kotlin JNI declaration bridge hosting safe wrapper methods and fallback error catches.
* `/app/src/main/java/com/example/viewmodel/LocalAiViewModel.kt`: Core execution controller hosting pipeline fallback redirecting logic and VM destruction hooks.
* `/app/build.gradle.kts`: Production-ready build configurations maintaining compact target architectures and asset shrinking profiles.

---
The codebase builds cleanly (`./gradlew assembleDebug assembleRelease`, lint passing) and is ready for further development toward real on-device inference. 🚀
