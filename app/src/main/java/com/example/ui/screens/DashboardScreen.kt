package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AiModel
import com.example.viewmodel.LocalAiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: LocalAiViewModel) {
    val models by viewModel.modelsState.collectAsState()
    val activeModelId by viewModel.selectedModelId.collectAsState()
    val isGpuEnabled by viewModel.isGpuEnabled.collectAsState()
    val ollamaUrl by viewModel.ollamaUrl.collectAsState()
    val cpuCoreCount by viewModel.cpuCoreCount.collectAsState()
    val deviceRamGb by viewModel.availableDeviceRamGb.collectAsState()
    
    // Unpacker dialog / input state
    var showUnpackerDialog by remember { mutableStateOf(false) }
    var inputModelName by remember { mutableStateOf("Hermes-3-Llama-3.1-8B") }
    var inputSizeCategory by remember { mutableStateOf("8.0 Billion") }
    var inputTaskCategory by remember { mutableStateOf("Deep Reasoning Logic") }
    var inputSource by remember { mutableStateOf("Nous Research") }

    val extractionActive by viewModel.gGufExtractionActive.collectAsState()
    val extractionProgress by viewModel.gGufExtractionProgress.collectAsState()
    val extractionModelName by viewModel.gGufExtractionModelName.collectAsState()
    val extractionLogs by viewModel.gGufExtractionLogs.collectAsState()
    val validationPassed by viewModel.gGufValidationPassed.collectAsState()
    val integrityChecks by viewModel.gGufIntegrityChecks.collectAsState()
    val benchmarkTps by viewModel.gGufBenchmarkTps.collectAsState()
    val benchmarkVram by viewModel.gGufBenchmarkVramUsageMb.collectAsState()

    val primaryBg = Color(0xFF0F172A) // slate-900 cosmic dark
    val surfaceColor = Color(0xFF1E293B) // slate-800
    val accentBlue = Color(0xFF38BDF8) // sky-400
    val accentEmerald = Color(0xFF34D399) // emerald-400

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(primaryBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(surfaceColor, Color(0xFF1E1E38))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AETHER INTEL CORE",
                                    fontSize = 11.sp,
                                    color = accentBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(accentEmerald.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "LOCAL POWERED",
                                        fontSize = 8.sp,
                                        color = accentEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Local AI Studio Orchestrator",
                                fontSize = 24.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Benchmark, optimize, and stack GGUF models on-device inside a zero-trust hardware sandbox.",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // System Specs & Hardware Matrix Block
            item {
                Text(
                    text = "HARDWARE ALLOCATION MATRIX",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "CPU Allocation",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "$cpuCoreCount Core ARM NEON Ready",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "System RAM Bounds",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "${String.format("%.1f", deviceRamGb)} GB Hardware Limit",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Divider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White.copy(alpha = 0.1f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "GPU Acceleration",
                                    tint = accentBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "C++ Vulkan / GPU Acceleration",
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                            Switch(
                                checked = isGpuEnabled,
                                onCheckedChange = { viewModel.isGpuEnabled.value = it },
                                modifier = Modifier.testTag("gpu_acceleration_toggle")
                            )
                        }
                    }
                }
            }

            // Connection Controller
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "PC NETWORK API GATEWAY",
                            fontSize = 11.sp,
                            color = accentBlue,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ollamaUrl,
                            onValueChange = { viewModel.ollamaUrl.value = it },
                            label = { Text("Ollama URL Endpoint") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ollama_url_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = accentBlue,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.checkOllamaBackend() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("ping_ollama_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                        ) {
                            Text("Ping & Sync Ollama Node", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // GGUF Native packager tester output block
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)) // deep indigo background
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "GGUF NATIVE UNPACKER",
                                    fontSize = 12.sp,
                                    color = Color(0xFFA5B4FC),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Compile offline downloaded weights",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            Button(
                                onClick = { showUnpackerDialog = true },
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("open_unpacker_dialog"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                            ) {
                                Text("Import", fontSize = 12.sp)
                            }
                        }

                        // Extraction Progress
                        if (extractionActive) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Extracting & Testing '$extractionModelName'...",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = extractionProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF818CF8),
                                trackColor = Color(0xFF312E81)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .verticalScroll(rememberScrollState())
                                    .padding(8.dp)
                            ) {
                                Column {
                                    extractionLogs.forEach { log ->
                                        Text(
                                            text = log,
                                            color = Color.Green,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        } else if (extractionModelName.isNotEmpty()) {
                            // Stats from loaded
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "LAST COMPILATION: $extractionModelName",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF818CF8),
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Compile JNI Validation: " + if(validationPassed) "Passed ✅" else "Failed ❌",
                                            fontSize = 11.sp,
                                            color = if(validationPassed) Color.Green else Color.Red
                                        )
                                        Text(
                                            text = "On-Device Speed: $benchmarkTps T/s",
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "VRAM Offset Map size: $benchmarkVram MB",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    integrityChecks.forEach { check ->
                                        Text(
                                            text = "• $check",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Header for selection
            item {
                Text(
                    text = "REGISTERED MODELS SYSTEM INDEX",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Models dynamic layout
            items(models) { model ->
                val isActive = model.id == activeModelId
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectModel(model.id) }
                        .testTag("model_card_${model.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) Color(0xFF1E293B) else surfaceColor
                    ),
                    border = BorderStroke(
                        width = if (isActive) 1.5.dp else 0.dp,
                        color = if (isActive) accentBlue else Color.Transparent
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = model.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                when(model.type) {
                                                    com.example.data.ModelType.CODER -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                                    com.example.data.ModelType.REASONING_LLM -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                                    com.example.data.ModelType.CHAT_LLM -> Color(0xFF10B981).copy(alpha = 0.2f)
                                                }
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = model.type.name,
                                            fontSize = 8.sp,
                                            color = when(model.type) {
                                                com.example.data.ModelType.CODER -> Color(0xFFFBBF24)
                                                com.example.data.ModelType.REASONING_LLM -> Color(0xFFA78BFA)
                                                com.example.data.ModelType.CHAT_LLM -> Color(0xFF34D399)
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = "Developer: ${model.developer} | Parameters: ${model.size}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isActive) accentEmerald.copy(alpha = 0.2f)
                                        else Color.White.copy(alpha = 0.05f)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (model.isRunning) "RUNNING" else if (model.isDownloaded) "READY" else "NOT CACHED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (model.isRunning) accentEmerald else if (model.isDownloaded) Color.White else Color.Yellow
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = model.description,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                Text(
                                    text = "Accuracy: ${model.accuracyScore}%",
                                    fontSize = 11.sp,
                                    color = accentBlue,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Reasoning Score: ${model.reasoningScore}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFA78BFA),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "CPU: ${model.cpuTokensPerSec}T/s | GPU: ${model.gpuTokensPerSec}T/s",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // Import GGUF dialogue
        if (showUnpackerDialog) {
            AlertDialog(
                onDismissRequest = { showUnpackerDialog = false },
                title = { Text("Native GGUF Unpacker Controller", color = Color.White) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Compile an offline downloaded GGUF file format weights directly into active registry with NDK verification parameters:",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        OutlinedTextField(
                            value = inputModelName,
                            onValueChange = { inputModelName = it },
                            label = { Text("Model Registered Name") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        OutlinedTextField(
                            value = inputSizeCategory,
                            onValueChange = { inputSizeCategory = it },
                            label = { Text("Parameter Size (e.g. 1.5 Billion)") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        OutlinedTextField(
                            value = inputTaskCategory,
                            onValueChange = { inputTaskCategory = it },
                            label = { Text("Task Group (e.g. Coder, Reasoning)") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        OutlinedTextField(
                            value = inputSource,
                            onValueChange = { inputSource = it },
                            label = { Text("Model Source Hub") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showUnpackerDialog = false
                            viewModel.triggerGgufExtractionAndTest(
                                modelName = inputModelName,
                                size = inputSizeCategory,
                                typeStr = inputTaskCategory,
                                source = inputSource
                            )
                        }
                    ) {
                        Text("Verify & Link weights")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnpackerDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = surfaceColor
            )
        }
    }
}
