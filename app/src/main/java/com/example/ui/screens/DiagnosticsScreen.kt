package com.example.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.LocalAiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(viewModel: LocalAiViewModel) {
    var activeSubTab by remember { mutableStateOf(0) } // 0 = Vector VSS, 1 = LoRA Tuning

    val primaryBg = Color(0xFF0F172A) // slate-900 cosmic dark
    val surfaceColor = Color(0xFF1E293B) // slate-800
    val accentViolet = Color(0xFF8B5CF6) // violet-500
    val accentCyan = Color(0xFF06B6D4) // cyan-500

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(primaryBg)
    ) {
        // Sub-tabs Row (Vector vs LoRA)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF020617))
                .padding(4.dp)
        ) {
            Button(
                onClick = { activeSubTab = 0 },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("subtab_vss"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubTab == 0) surfaceColor else Color.Transparent,
                    contentColor = if (activeSubTab == 0) Color.White else Color.White.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(0.dp)
             ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.List, contentDescription = "Vector VSS", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vector DB RAG", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Button(
                onClick = { activeSubTab = 1 },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("subtab_lora"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubTab == 1) surfaceColor else Color.Transparent,
                    contentColor = if (activeSubTab == 1) Color.White else Color.White.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = "LoRA Tuning", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PC LoRA Tuning", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Sub-view rendering
        if (activeSubTab == 0) {
            VectorVssView(viewModel, surfaceColor, accentCyan)
        } else {
            LoraTuningView(viewModel, surfaceColor, accentViolet)
        }
    }
}

@Composable
fun VectorVssView(viewModel: LocalAiViewModel, cardColor: Color, themeColor: Color) {
    val documents by viewModel.ragDocuments.collectAsState()
    val queryInput by viewModel.ragQueryInput.collectAsState()
    val isSearching by viewModel.isRagIndexing.collectAsState()
    val searchResults by viewModel.ragRetrievalResults.collectAsState()

    var docTitleInput by remember { mutableStateOf("weekly_orchestra_notes.txt") }
    var docContentInput by remember { mutableStateOf("Local index maps Isaac's coordinates dynamically over custom AES headers.") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Doc Indexer Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CUSTOM DOCUMENT EMBEDDING INTEGRATOR",
                        fontSize = 11.sp,
                        color = themeColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = docTitleInput,
                        onValueChange = { docTitleInput = it },
                        label = { Text("Document Header / Title") },
                        modifier = Modifier.fillMaxWidth().testTag("vss_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = docContentInput,
                        onValueChange = { docContentInput = it },
                        label = { Text("Document Data Content") },
                        modifier = Modifier.fillMaxWidth().testTag("vss_body_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (docTitleInput.isNotBlank() && docContentInput.isNotBlank()) {
                                viewModel.createIndexForDocument(docTitleInput, docContentInput)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("vss_index_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) {
                        Text("Compute 128-Dim Embedding & Index", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Query Search Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "VECTOR COSINE-SIMILARITY PROMPT MATCH",
                        fontSize = 11.sp,
                        color = themeColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = queryInput,
                        onValueChange = { viewModel.ragQueryInput.value = it },
                        label = { Text("Query Context") },
                        modifier = Modifier.fillMaxWidth().testTag("vss_search_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.executeRagRetrieval() },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("vss_search_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("Query Nearest Vectors", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "COMPUTED COSINE MATRICES SIMILARITY COEFFS:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        searchResults.forEach { scorePair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• id: ${scorePair.first.vectorId} (${scorePair.first.title})",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Score: ${String.format("%.1f", scorePair.second * 100)}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = themeColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // Indexed items title
        item {
            Text(
                text = "INDEXED FILES VECTOR SCHEMA [COLUMNS = 128]",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Indexed Files Dynamic List list
        if (documents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No vector indices generated in room db SQLite registry.", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        } else {
            items(documents) { doc ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Doc ID: ${doc.vectorId} - ${doc.title}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = doc.chunkText,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "128-float tensors starting with: [" + doc.embeddingJson.split(",").take(3).joinToString(", ") + "...]",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = themeColor.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.removeRagDocument(doc.vectorId) },
                            modifier = Modifier.testTag("delete_vss_${doc.vectorId}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Wipe index record", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoraTuningView(viewModel: LocalAiViewModel, cardColor: Color, themeColor: Color) {
    val isTraining by viewModel.isCreatorTraining.collectAsState()
    val baseModelId by viewModel.creatorBaseModelId.collectAsState()
    val creatorModelName by viewModel.creatorModelName.collectAsState()
    val totalEpochs by viewModel.creatorEpochs.collectAsState()
    val datasetName by viewModel.creatorDatasetName.collectAsState()
    val loraRank by viewModel.creatorLoraRank.collectAsState()
    val learningRate by viewModel.creatorLr.collectAsState()

    val progress by viewModel.creatorTrainingProgress.collectAsState()
    val epochCurrent by viewModel.creatorEpochCurrent.collectAsState()
    val lossValues by viewModel.creatorLossValues.collectAsState()
    val accuracy by viewModel.creatorTrainingAccuracy.collectAsState()
    val logs by viewModel.creatorTrainingLogs.collectAsState()
    val activeTaskCategory by viewModel.creatorTaskCategory.collectAsState()
    val activeQuantization by viewModel.creatorQuantization.collectAsState()

    val models by viewModel.modelsState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Scheduler Form Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PC WEBSOCKET TRAINING OFFLOAD PARAMETERS",
                        fontSize = 11.sp,
                        color = themeColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Select base baseModelId from models
                    Text("Select Base Model weights:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        models.forEach { model ->
                            val isSelected = model.id == baseModelId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) themeColor else Color.Black.copy(0.4f))
                                    .clickable { viewModel.creatorBaseModelId.value = model.id }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = model.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = creatorModelName,
                        onValueChange = { viewModel.creatorModelName.value = it },
                        label = { Text("New Tuned Model ID") },
                        modifier = Modifier.fillMaxWidth().testTag("lora_id_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = datasetName,
                        onValueChange = { viewModel.creatorDatasetName.value = it },
                        label = { Text("Training Dataset Filename") },
                        modifier = Modifier.fillMaxWidth().testTag("lora_dataset_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Target Epochs: $totalEpochs", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            Slider(
                                value = totalEpochs.toFloat(),
                                onValueChange = { viewModel.creatorEpochs.value = it.toInt() },
                                valueRange = 2f..10f,
                                steps = 7
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("LoRA Adapter Rank: $loraRank", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            Slider(
                                value = loraRank.toFloat(),
                                onValueChange = { viewModel.creatorLoraRank.value = it.toInt() },
                                valueRange = 8f..64f,
                                steps = 3
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Selection Task Category:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            OutlinedTextField(
                                value = activeTaskCategory,
                                onValueChange = { viewModel.creatorTaskCategory.value = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quantization Precision:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            OutlinedTextField(
                                value = activeQuantization,
                                onValueChange = { viewModel.creatorQuantization.value = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.runLoraWebsocketTraining() },
                        enabled = !isTraining,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("lora_train_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) {
                        Text("Offload & Commene LoRA Tuning", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Live Training Interactive Logs Card
        if (isTraining || lossValues.isNotEmpty() || logs.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CALIBRATION ANALYSIS LIVE PLOT",
                                fontSize = 11.sp,
                                color = themeColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isTraining) Color.Yellow.copy(alpha = 0.2f) else Color.Green.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isTraining) "CALIBRATING - EPOCH $epochCurrent/$totalEpochs" else "CONVERGED SUCCESSFULLY",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTraining) Color.Yellow else Color.Green
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        
                        // Training stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Cross-Entropy Loss", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                Text(
                                    text = if (lossValues.isEmpty()) "0.0000" else String.format("%.4f", lossValues.last()),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Calculated Accuracy", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                Text(
                                    text = String.format("%.1f%%", accuracy),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Green
                                )
                            }
                        }

                        // Drawing our custom loss canvas line chart dynamically!
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(6.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                if (lossValues.size > 1) {
                                    val path = Path()
                                    val maxVal = 4.5f
                                    val minVal = 0.0f
                                    val rangeY = maxVal - minVal

                                    val stepX = size.width / (lossValues.size - 1)
                                    lossValues.forEachIndexed { idx, value ->
                                        val cX = idx * stepX
                                        val normalizedY = (maxVal - value) / rangeY
                                        val cY = normalizedY * size.height

                                        if (idx == 0) {
                                            path.moveTo(cX, cY)
                                        } else {
                                            path.lineTo(cX, cY)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = themeColor,
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                } else {
                                    // Empty plot guidelines
                                    drawLine(
                                        color = Color.White.copy(0.1f),
                                        start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                                        end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Progress layout
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color = themeColor,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "OFFLOAD FEED CONSOLE LOGS:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                logs.forEach { log ->
                                    Text(
                                        text = log,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.Green.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
