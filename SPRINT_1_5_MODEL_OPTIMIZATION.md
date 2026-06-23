# Sprint 1.5: Model Optimization — 3B and Below Priority

**Status:** 📋 Pre-Implementation  
**Focus:** Optimize for real mobile devices (not just SDKs)  
**Goal:** Sub-5GB footprint, 2-4 sec first-token latency

---

## 🎯 Why 3B and Below?

### Device Reality Check
```
Snapdragon 8 Gen 2 / A17 Pro:
├─ Total RAM: 8-12 GB
├─ Available for app: ~4-6 GB
├─ OS + System: ~2-3 GB
├─ Model size limit: ~3-4 GB (comfortable)
└─ Safe models: 1B-3.5B parameters

Older/Mid-range devices:
├─ Snapdragon 870 / A14
├─ Total RAM: 6-8 GB
├─ Available for app: ~2-4 GB
└─ Safe models: 1B-2B parameters
```

**Optimal Range:** **1B - 3.5B parameters** (2.2 GB - 4.1 GB file size)

---

## 📊 Recommended Model Stack

### Tier 1: Ultra-Light (Fit all devices)
| Model | Params | File Size | Min RAM | Speed | Quality |
|-------|--------|-----------|---------|-------|---------|
| **TinyLlama 1.1B** | 1.1B | 636 MB | 2.5 GB | 8-10 tok/s | Good for simple Q&A |
| **Phi 2 (GGUF)** | 2.7B | 1.6 GB | 3.5 GB | 4-6 tok/s | Excellent coding |
| **Gemma 2B** | 2B | 1.2 GB | 3.0 GB | 6-8 tok/s | Good balance |

### Tier 2: Balanced (Mid-range devices)
| Model | Params | File Size | Min RAM | Speed | Quality |
|-------|--------|-----------|---------|-------|---------|
| **Mistral 7B Instruct** | 7B | 4.2 GB | 6.5 GB | 2-3 tok/s | Best quality |
| **Llama 3.2 3B** | 3B | 1.9 GB | 4.0 GB | 5-7 tok/s | Very good |
| **Qwen2 3B** | 3B | 1.8 GB | 3.8 GB | 5-7 tok/s | Very good |

### Tier 3: Heavy (Flagship only)
| Model | Params | File Size | Min RAM | Speed | Quality |
|-------|--------|-----------|---------|-------|---------|
| **Phi-4 Mini** | 3.8B | 2.8 GB | 5.0 GB | 3-4 tok/s | Excellent |
| **Qwen 3 4B** | 4B | 4.2 GB | 6.5 GB | 2-3 tok/s | Excellent |

---

## ✅ Action: Update Model Priorities

### Current (Sprint 1)
```kotlin
// app/src/main/java/com/example/data/ModelUniverse.kt
val models = listOf(
    AiModel(id = "qwen_3_4b", name = "Qwen 3 4B", ...),     // ← 4GB
    AiModel(id = "phi_4_mini", name = "Phi-4 Mini", ...),   // ← 2.8GB
    AiModel(id = "gemma_3_4b", name = "Gemma 3 4B", ...),   // ← 3.9GB
    AiModel(id = "llama_3_2_3b", name = "Llama 3.2 3B", ...) // ← 1.9GB
)
```

### Updated (Sprint 1.5) — 3B Priority
```kotlin
val models = listOf(
    // Tier 1: Ultra-light (recommended for all devices)
    AiModel(
        id = "tinyllama_1_1b",
        name = "TinyLlama 1.1B",
        paramSizeGb = 1.1,
        minRamGb = 2.5,
        fileSize = "636 MB",
        cpuTokensPerSec = 9.0,
        gpuTokensPerSec = 25.0,
        description = "Tiny, fast. Best for basic Q&A and quick responses."
    ),
    AiModel(
        id = "gemma_2b",
        name = "Gemma 2B",
        paramSizeGb = 2.0,
        minRamGb = 3.0,
        fileSize = "1.2 GB",
        cpuTokensPerSec = 7.0,
        gpuTokensPerSec = 20.0,
        description = "Balanced. Good quality, works on 6GB+ phones."
    ),
    AiModel(
        id = "phi_2_2_7b",
        name = "Phi 2.7B",
        paramSizeGb = 2.7,
        minRamGb = 3.5,
        fileSize = "1.6 GB",
        cpuTokensPerSec = 5.0,
        gpuTokensPerSec = 18.0,
        description = "Excellent for coding. Fast, high quality."
    ),
    
    // Tier 2: Mid-range (recommended for 8GB+ devices)
    AiModel(
        id = "llama_3_2_3b",
        name = "Llama 3.2 3B",
        paramSizeGb = 3.2,
        minRamGb = 4.0,
        fileSize = "1.9 GB",
        cpuTokensPerSec = 6.0,
        gpuTokensPerSec = 22.0,
        description = "Very good quality. Recommended for most devices."
    ),
    AiModel(
        id = "qwen2_3b",
        name = "Qwen2 3B",
        paramSizeGb = 3.0,
        minRamGb = 3.8,
        fileSize = "1.8 GB",
        cpuTokensPerSec = 6.5,
        gpuTokensPerSec = 23.0,
        description = "Excellent balance of speed and quality."
    ),
    
    // Tier 3: Heavy (for flagship devices only)
    AiModel(
        id = "phi_4_mini",
        name = "Phi-4 Mini",
        paramSizeGb = 3.8,
        minRamGb = 5.0,
        fileSize = "2.8 GB",
        cpuTokensPerSec = 3.5,
        gpuTokensPerSec = 16.0,
        description = "Excellent quality. Requires 12GB+ RAM."
    ),
    AiModel(
        id = "qwen_3_4b",
        name = "Qwen 3 4B (Flagship)",
        paramSizeGb = 4.0,
        minRamGb = 6.5,
        fileSize = "4.2 GB",
        cpuTokensPerSec = 2.5,
        gpuTokensPerSec = 14.0,
        description = "Premium quality. For high-end devices only."
    )
)
```

---

## 🔧 Implement Device Detection

Add smart model recommendation based on device RAM:

```kotlin
// app/src/main/java/com/example/utils/DeviceHelper.kt
object DeviceHelper {
    
    fun getRecommendedModels(context: Context): List<String> {
        val ramGb = getTotalRamGb(context)
        
        return when {
            ramGb >= 12 -> listOf(
                "qwen_3_4b",      // Can run heavy models
                "phi_4_mini",
                "llama_3_2_3b",
                "gemma_2b",
                "tinyllama_1_1b"
            )
            ramGb >= 8 -> listOf(
                "llama_3_2_3b",   // Mid-range
                "qwen2_3b",
                "phi_2_2_7b",
                "gemma_2b",
                "tinyllama_1_1b"
            )
            ramGb >= 6 -> listOf(
                "gemma_2b",       // Conservative
                "phi_2_2_7b",
                "tinyllama_1_1b"
            )
            else -> listOf(
                "tinyllama_1_1b"  // Ultra-light only
            )
        }
    }
    
    fun getTotalRamGb(context: Context): Int {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory() / (1024 * 1024 * 1024)
        return totalMemory.toInt()
    }
    
    fun getAvailableRamGb(context: Context): Int {
        val runtime = Runtime.getRuntime()
        val freeMemory = runtime.freeMemory() / (1024 * 1024 * 1024)
        return freeMemory.toInt()
    }
}
```

---

## 📱 UI Update: Smart Model Selection

Update ViewModel to show only compatible models:

```kotlin
// In LocalAiViewModel.kt
init {
    viewModelScope.launch {
        val totalRam = DeviceHelper.getTotalRamGb(getApplication())
        val recommendedIds = DeviceHelper.getRecommendedModels(getApplication())
        
        // Filter models to only recommended for this device
        _modelsState.value = ModelUniverse.models.filter { model ->
            model.minRamGb <= totalRam && recommendedIds.contains(model.id)
        }
        
        addTerminalLine(
            "DEVICE",
            "📱 Device RAM: ${totalRam}GB | Recommended models: ${recommendedIds.size}"
        )
    }
}
```

---

## 📥 Download Instructions: 3B Models

### Option 1: Via Ollama (Easiest)
```bash
# Install Ollama: https://ollama.ai

# Download 3B models
ollama pull llama2:3b
ollama pull phi:3b
ollama pull gemma2:3b
ollama pull mistral

# Models saved to ~/.ollama/models/
# Convert to .gguf if needed
```

### Option 2: Manual Download from HuggingFace
```bash
# TinyLlama
wget https://huggingface.co/TheBloke/TinyLlama-1.1B-GGUF/resolve/main/tinyllama-1.1b.Q4_K_M.gguf

# Phi 2.7B
wget https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf

# Llama 3.2 3B
wget https://huggingface.co/lmstudio-community/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf

# Qwen2 3B
wget https://huggingface.co/lmstudio-community/Qwen2-3B-GGUF/resolve/main/Qwen2-3B-Instruct-Q4_K_M.gguf
```

### Option 3: Via LM Studio (GUI)
1. Download: https://lmstudio.ai
2. Search for model (e.g., "Llama 3.2 3B")
3. Download GGUF version
4. Locate file in `~/LMStudio/models/`

### Transfer to Device
```bash
# Copy model(s) to phone
adb push tinyllama-1.1b.Q4_K_M.gguf /sdcard/Download/tinyllama_1_1b.gguf
adb push Llama-3.2-3B-Instruct-Q4_K_M.gguf /sdcard/Download/llama_3_2_3b.gguf

# Verify
adb shell ls -lh /sdcard/Download/
```

---

## 🎯 Sprint 1.5 Checklist

- [ ] Update `ModelUniverse.kt` with 3B-prioritized model list
- [ ] Implement `DeviceHelper.kt` for device detection
- [ ] Update ViewModel to filter models by device RAM
- [ ] Add UI indicators: "Recommended for your device"
- [ ] Download 3 core models (TinyLlama, Gemma 2B, Llama 3.2 3B)
- [ ] Test model loading and inference on device
- [ ] Profile memory usage and speed
- [ ] Document optimal models per device tier

---

## 📊 Expected Performance: 3B Models

### TinyLlama 1.1B (Ultra-Light)
```
Device: Snapdragon 8 Gen 2
RAM: 8GB
Model: TinyLlama 1.1B (636 MB)
─────────────────────────
First Token: 200-300ms
Per Token: 100-120ms
Speed: 8-10 tok/s
Memory: 2.5 GB total
Battery: ~50mAh per 100 tokens
Status: ✅ Excellent for all devices
```

### Gemma 2B (Balanced)
```
Device: Snapdragon 870
RAM: 6GB
Model: Gemma 2B (1.2 GB)
─────────────────────────
First Token: 400-500ms
Per Token: 140-160ms
Speed: 6-7 tok/s
Memory: 3.2 GB total
Battery: ~70mAh per 100 tokens
Status: ✅ Good for mid-range
```

### Llama 3.2 3B (Sweet Spot)
```
Device: Snapdragon 8 Gen 2
RAM: 12GB
Model: Llama 3.2 3B (1.9 GB)
─────────────────────────
First Token: 300-400ms
Per Token: 150-180ms
Speed: 5-6 tok/s
Memory: 4.2 GB total
Battery: ~80mAh per 100 tokens
Status: ✅ Recommended for 8GB+ phones
```

---

## 🚀 Implementation Steps

### Phase 1: Model Curation
1. Test each 3B model on target device
2. Benchmark speed/memory/battery
3. Select top 3 for default bundle

### Phase 2: Smart Selection
1. Implement device detection
2. Show model recommendations
3. Warn if insufficient RAM

### Phase 3: Optimization
1. Enable NEON SIMD on ARM
2. Add GPU acceleration hooks
3. Profile and optimize JNI calls

---

## 📝 Notes

**Why 3B over 4B+?**
- ✅ Fits comfortably in 6-8GB devices
- ✅ 5-7 tokens/sec on CPU (acceptable UX)
- ✅ ~1-4 second first token latency
- ✅ Quality remains strong (Llama 3.2 3B is very capable)
- ✅ Battery-friendly for mobile

**When to use heavier models?**
- Flagship phones (12GB+ RAM)
- Offline long-form generation
- Non-interactive background tasks

---

**Recommendation:** Start with **Llama 3.2 3B** as default + **TinyLlama 1.1B** as fallback. This gives best UX across all device tiers.

Ready to implement? 🚀
