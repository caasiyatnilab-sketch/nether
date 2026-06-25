package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.data.ModelType
import com.example.viewmodel.LocalAiViewModel

private val PrimaryBg = Color(0xFF0F172A)
private val SurfaceColor = Color(0xFF1E293B)
private val AccentBlue = Color(0xFF38BDF8)
private val AccentEmerald = Color(0xFF34D399)
private val AccentViolet = Color(0xFF8B5CF6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: LocalAiViewModel) {
    val models by viewModel.modelsState.collectAsState()
    val activeModelId by viewModel.selectedModelId.collectAsState()
    val cpuCoreCount by viewModel.cpuCoreCount.collectAsState()
    val deviceRamGb by viewModel.deviceTotalRamGb.collectAsState()
    val availRamGb by viewModel.deviceAvailableRamGb.collectAsState()
    val gpuRenderer by viewModel.gpuRenderer.collectAsState()
    val deviceProcessor by viewModel.deviceProcessor.collectAsState()
    val loadedIds by viewModel.loadedModelIds.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val dualSuggestion by viewModel.dualSuggestion.collectAsState()
    val teamMode by viewModel.teamModeEnabled.collectAsState()
    val drafterId by viewModel.drafterModelId.collectAsState()
    val reasonerId by viewModel.reasonerModelId.collectAsState()
    val ollamaUrl by viewModel.ollamaUrl.collectAsState()
    val mcpUrl by viewModel.mcpUrl.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(PrimaryBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeaderCard() }

            item {
                HardwareCard(
                    cpuCoreCount = cpuCoreCount,
                    processor = deviceProcessor,
                    totalRamGb = deviceRamGb,
                    availRamGb = availRamGb,
                    gpuRenderer = gpuRenderer
                )
            }

            // Smart recommendation
            recommendations.firstOrNull { it.fits }?.let { rec ->
                item {
                    RecommendationCard(
                        title = "RECOMMENDED FOR THIS DEVICE",
                        modelName = rec.model.name,
                        reason = rec.reason,
                        actionLabel = "Load",
                        onAction = { viewModel.loadModel(rec.model.id) }
                    )
                }
            }

            // Dual model teamwork
            item {
                DualModeCard(
                    teamMode = teamMode,
                    onToggle = { viewModel.setTeamMode(it) },
                    models = models,
                    drafterId = drafterId,
                    reasonerId = reasonerId,
                    loadedIds = loadedIds,
                    suggestionText = dualSuggestion?.reason ?: "",
                    onPickDrafter = { viewModel.setDrafter(it) },
                    onPickReasoner = { viewModel.setReasoner(it) },
                    onLoadDrafter = { viewModel.loadModel(drafterId) },
                    onLoadReasoner = { viewModel.loadModel(reasonerId) }
                )
            }

            item {
                Text(
                    "ON-DEVICE MODEL MANAGER",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            items(models, key = { it.id }) { model ->
                ModelCard(
                    model = model,
                    isActive = model.id == activeModelId,
                    isLoaded = loadedIds.contains(model.id),
                    isDownloading = downloadingIds.contains(model.id),
                    progress = downloadProgress[model.id] ?: 0f,
                    fitsRam = deviceRamGb <= 0f || deviceRamGb >= model.minRamGb,
                    onDownload = { viewModel.downloadModel(model.id) },
                    onLoad = { viewModel.loadModel(model.id) },
                    onDelete = { viewModel.deleteModel(model.id) }
                )
            }

            item {
                ConnectivityCard(
                    ollamaUrl = ollamaUrl,
                    onOllamaUrl = { viewModel.ollamaUrl.value = it },
                    onPingOllama = { viewModel.checkOllamaBackend() },
                    mcpUrl = mcpUrl,
                    onSetMcp = { viewModel.configureMcp(it) },
                    onPingMcp = { viewModel.pingMcp() }
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun HeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(SurfaceColor, Color(0xFF1E1E38))))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("NETHER · LOCAL AI", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(AccentEmerald.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("ON-DEVICE", fontSize = 8.sp, color = AccentEmerald, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Local AI Studio Orchestrator", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Download, run, and combine real GGUF models on-device with genuine llama.cpp inference — private by default.",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HardwareCard(
    cpuCoreCount: Int,
    processor: String,
    totalRamGb: Float,
    availRamGb: Float,
    gpuRenderer: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("HARDWARE (REAL SCAN)", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(10.dp))
            SpecRow("CPU", "$cpuCoreCount cores · $processor")
            SpecRow("RAM", "${"%.1f".format(totalRamGb)} GB total · ${"%.1f".format(availRamGb)} GB free")
            SpecRow("GPU", gpuRenderer)
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        Text(value, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun RecommendationCard(title: String, modelName: String, reason: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E2A2E))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 10.sp, color = AccentEmerald, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(modelName, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text(reason, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
            }
            Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald), modifier = Modifier.testTag("load_recommended")) {
                Text(actionLabel, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DualModeCard(
    teamMode: Boolean,
    onToggle: (Boolean) -> Unit,
    models: List<AiModel>,
    drafterId: String,
    reasonerId: String,
    loadedIds: Set<String>,
    suggestionText: String,
    onPickDrafter: (String) -> Unit,
    onPickReasoner: (String) -> Unit,
    onLoadDrafter: () -> Unit,
    onLoadReasoner: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("DUAL-MODEL TEAMWORK", fontSize = 11.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("Drafter answers, reasoner refines — both run on-device", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                }
                Switch(checked = teamMode, onCheckedChange = onToggle, modifier = Modifier.testTag("team_mode_toggle"))
            }
            if (suggestionText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("💡 $suggestionText", fontSize = 11.sp, color = Color(0xFFC7D2FE))
            }
            if (teamMode) {
                Spacer(Modifier.height(12.dp))
                RolePicker("Drafter", models, drafterId, loadedIds, onPickDrafter, onLoadDrafter)
                Spacer(Modifier.height(10.dp))
                RolePicker("Reasoner", models, reasonerId, loadedIds, onPickReasoner, onLoadReasoner)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RolePicker(
    label: String,
    models: List<AiModel>,
    selectedId: String,
    loadedIds: Set<String>,
    onPick: (String) -> Unit,
    onLoad: () -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$label: ${models.firstOrNull { it.id == selectedId }?.name ?: selectedId}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            val loaded = loadedIds.contains(selectedId)
            Button(
                onClick = onLoad,
                enabled = !loaded,
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet, disabledContainerColor = Color(0xFF3F3F5A)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) { Text(if (loaded) "Loaded" else "Load", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            models.forEach { m ->
                val sel = m.id == selectedId
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (sel) AccentViolet else Color.Black.copy(alpha = 0.3f),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onPick(m.id) }
                ) {
                    Text(m.name, fontSize = 10.sp, color = if (sel) Color.Black else Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: AiModel,
    isActive: Boolean,
    isLoaded: Boolean,
    isDownloading: Boolean,
    progress: Float,
    fitsRam: Boolean,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("model_card_${model.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0xFF14233B) else SurfaceColor),
        border = if (isLoaded) BorderStroke(1.5.dp, AccentEmerald) else if (isActive) BorderStroke(1.dp, AccentBlue) else null
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        TypeBadge(model.type)
                    }
                    Text("${model.developer} · ${model.sizeLabel} · min ${model.minRamGb}GB RAM", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 2.dp))
                }
                StatusBadge(isLoaded, model.isDownloaded, isDownloading, fitsRam)
            }

            Spacer(Modifier.height(8.dp))
            Text(model.description, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))

            if (!fitsRam) {
                Spacer(Modifier.height(6.dp))
                Text("⚠️ Exceeds this device's RAM — may fail to load", fontSize = 10.sp, color = Color(0xFFFBBF24))
            }

            if (isDownloading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = AccentBlue,
                    trackColor = Color(0xFF334155)
                )
                Text("${(progress * 100).toInt()}%", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 2.dp))
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!model.isDownloaded) {
                    Button(
                        onClick = onDownload,
                        enabled = !isDownloading,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.weight(1f).testTag("download_${model.id}")
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isDownloading) "Downloading…" else "Download", color = Color.Black, fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = onLoad,
                        enabled = !isLoaded,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald, disabledContainerColor = Color(0xFF2B4A40)),
                        modifier = Modifier.weight(1f).testTag("load_${model.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text(if (isLoaded) "Loaded" else "Load", color = Color.Black, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_${model.id}"),
                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeBadge(type: ModelType) {
    val (bg, fg) = when (type) {
        ModelType.CODER -> Color(0xFFF59E0B).copy(alpha = 0.2f) to Color(0xFFFBBF24)
        ModelType.REASONING_LLM -> Color(0xFF8B5CF6).copy(alpha = 0.2f) to Color(0xFFA78BFA)
        ModelType.CHAT_LLM -> Color(0xFF10B981).copy(alpha = 0.2f) to Color(0xFF34D399)
    }
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(type.name, fontSize = 8.sp, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusBadge(isLoaded: Boolean, isDownloaded: Boolean, isDownloading: Boolean, fitsRam: Boolean) {
    val (label, color) = when {
        isLoaded -> "LOADED" to AccentEmerald
        isDownloading -> "DOWNLOADING" to AccentBlue
        isDownloaded -> "READY" to Color.White
        !fitsRam -> "TOO BIG" to Color(0xFFFBBF24)
        else -> "NOT CACHED" to Color.Yellow
    }
    Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun ConnectivityCard(
    ollamaUrl: String,
    onOllamaUrl: (String) -> Unit,
    onPingOllama: () -> Unit,
    mcpUrl: String,
    onSetMcp: (String) -> Unit,
    onPingMcp: () -> Unit
) {
    var mcpDraft by remember(mcpUrl) { mutableStateOf(mcpUrl) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("CONNECTIVITY (OPTIONAL)", fontSize = 11.sp, color = AccentBlue, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = ollamaUrl,
                onValueChange = onOllamaUrl,
                label = { Text("Ollama URL (remote fallback)") },
                modifier = Modifier.fillMaxWidth().testTag("ollama_url_input"),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AccentBlue, unfocusedBorderColor = Color.White.copy(alpha = 0.2f))
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onPingOllama, modifier = Modifier.fillMaxWidth().testTag("ping_ollama_button"), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                Text("Ping Ollama Node", fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = mcpDraft,
                onValueChange = { mcpDraft = it },
                label = { Text("MCP server URL (JSON-RPC/HTTP)") },
                modifier = Modifier.fillMaxWidth().testTag("mcp_url_input"),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AccentViolet, unfocusedBorderColor = Color.White.copy(alpha = 0.2f))
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSetMcp(mcpDraft) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)) {
                    Text("Set MCP", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onPingMcp, modifier = Modifier.weight(1f)) {
                    Text("List Tools", color = Color.White)
                }
            }
            Text("Tip: type /help in Chat for on-device skills (battery, clipboard, torch, device).", fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(top = 8.dp))
        }
    }
}
