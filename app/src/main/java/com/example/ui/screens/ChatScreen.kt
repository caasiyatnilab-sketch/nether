package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatRole
import com.example.viewmodel.LocalAiViewModel
import com.example.viewmodel.TerminalLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Shared palette for the chat surface.
private val ChatBg = Color(0xFF0F172A)        // slate-900
private val ChatSurface = Color(0xFF1E293B)   // slate-800
private val UserBubble = Color(0xFF0E7490)    // cyan-700
private val AssistantBubble = Color(0xFF334155) // slate-700
private val Accent = Color(0xFF22D3EE)        // cyan-400
private val Violet = Color(0xFF8B5CF6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: LocalAiViewModel) {
    val lines by viewModel.terminalOutput.collectAsState()
    val isBusy by viewModel.isAgentBusy.collectAsState()
    val masterKeyEnabled by viewModel.masterKeyEnabled.collectAsState()
    val models by viewModel.modelsState.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val ragEnabled by viewModel.ragContextEnabled.collectAsState()
    val ragUsed by viewModel.lastRagContextUsed.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val activeModel = models.firstOrNull { it.id == selectedModelId }
    // What actually renders: conversation always, system notices only when expanded.
    val visibleLines = remember(lines, showSystem) {
        if (showSystem) lines else lines.filter { it.role != ChatRole.SYSTEM }
    }
    LaunchedEffect(visibleLines.size, isBusy) {
        val target = visibleLines.size - 1 + if (isBusy) 1 else 0
        if (target >= 0) listState.animateScrollToItem(target.coerceAtLeast(0))
    }

    fun submit() {
        if (textInput.isNotBlank() && !isBusy) {
            viewModel.processLocalPrompt(textInput.trim())
            textInput = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatBg)
    ) {
        ChatHeader(
            modelName = activeModel?.name ?: "No model",
            encrypted = masterKeyEnabled,
            showSystem = showSystem,
            onToggleSystem = { showSystem = !showSystem },
            onClear = { viewModel.clearTerminalLog() }
        )

        ModelSelectorRow(
            models = models.map { it.id to it.name },
            selectedId = selectedModelId,
            onSelect = { viewModel.selectModel(it) }
        )

        // Conversation
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (visibleLines.isEmpty() && !isBusy) {
                ChatEmptyState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleLines) { line ->
                        when (line.role) {
                            ChatRole.USER -> ChatBubble(line, isUser = true)
                            ChatRole.ASSISTANT -> ChatBubble(line, isUser = false)
                            ChatRole.SYSTEM -> SystemNotice(line)
                        }
                    }
                    if (isBusy) {
                        item { TypingIndicator(activeModel?.name ?: "Assistant") }
                    }
                }
            }
        }

        // RAG context toggle
        RagContextBar(
            enabled = ragEnabled,
            usedTitles = ragUsed,
            onToggle = { viewModel.toggleRagContext() }
        )

        // Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = {
                    Text(
                        "Message ${activeModel?.name ?: "the assistant"}...",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp, max = 140.dp)
                    .testTag("terminal_prompt_input"),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = ChatSurface,
                    unfocusedContainerColor = ChatSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = { submit() },
                enabled = textInput.isNotBlank() && !isBusy,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("submit_prompt_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    disabledContainerColor = Color(0xFF334155)
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (textInput.isNotBlank() && !isBusy) Color.Black else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(
    modelName: String,
    encrypted: Boolean,
    showSystem: Boolean,
    onToggleSystem: () -> Unit,
    onClear: () -> Unit
) {
    Surface(color = ChatSurface, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Chat", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (encrypted) Icons.Default.Lock else Icons.Default.Warning,
                        contentDescription = "Encryption state",
                        tint = if (encrypted) Accent else Color.Yellow,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (encrypted) "End-to-end AES-256 (Keystore) · $modelName"
                        else "Keystore unavailable · $modelName",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }

            // Toggle system log visibility
            IconButton(
                onClick = onToggleSystem,
                modifier = Modifier.size(40.dp).testTag("toggle_system_log")
            ) {
                Icon(
                    imageVector = if (showSystem) Icons.Default.Info else Icons.Default.List,
                    contentDescription = "Toggle system log",
                    tint = if (showSystem) Accent else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(40.dp).testTag("clear_history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear conversation",
                    tint = Color.Red.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelSelectorRow(
    models: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        models.forEach { (id, name) ->
            val selected = id == selectedId
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (selected) Accent else ChatSurface,
                border = if (selected) null else BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelect(id) }
                    .testTag("model_chip_$id")
            ) {
                Text(
                    text = name,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(line: TerminalLine, isUser: Boolean) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                Icon(Icons.Default.Face, contentDescription = null, tint = Violet, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(line.sender, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA))
            }
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            ),
            color = if (isUser) UserBubble else AssistantBubble,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = line.text,
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Text(
            text = timeFmt.format(Date(line.timestamp)),
            fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun SystemNotice(line: TerminalLine) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = "${line.sender} · ${line.text}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TypingIndicator(modelName: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Violet, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("$modelName is thinking…", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun RagContextBar(enabled: Boolean, usedTitles: List<String>, onToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (enabled) Violet.copy(alpha = 0.18f) else ChatSurface,
            border = BorderStroke(1.dp, if (enabled) Violet else Color(0xFF334155)),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onToggle() }
                .testTag("rag_context_toggle")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (enabled) Violet else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "RAG context",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) Color.White else Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = when {
                            enabled && usedTitles.isNotEmpty() -> "Used: " + usedTitles.joinToString(", ")
                            enabled -> "Injecting nearest indexed documents into prompts"
                            else -> "Off — tap to ground replies in your indexed documents"
                        },
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Violet
                    )
                )
            }
        }
    }
}

@Composable
private fun ChatEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Email,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Start a conversation",
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Pick a model above and send a message. Replies run on-device and are encrypted at rest. Enable RAG to ground answers in your indexed documents.",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
