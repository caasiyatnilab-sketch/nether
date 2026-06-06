package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.LocalAiViewModel
import com.example.viewmodel.TerminalLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: LocalAiViewModel) {
    val logs by viewModel.terminalOutput.collectAsState()
    val isBusy by viewModel.isAgentBusy.collectAsState()
    val encryptionKey by viewModel.encryptionKey.collectAsState()
    val masterKeyEnabled by viewModel.masterKeyEnabled.collectAsState()
    
    val clickedFile by viewModel.clickedFileSystem.collectAsState()
    val clickedContact by viewModel.clickedContacts.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val primaryBg = Color(0xFF0F172A) // slate-900 cosmic dark
    val terminalBlack = Color(0xFF020617) // raw deeper black for prompt rows
    val terminalAccent = Color(0xFF10B981) // active emerald prompt
    val userAccent = Color(0xFF06B6D4) // user prompt cyan

    // Auto-scroll when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(primaryBg)
    ) {
        // Top Security Information bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted State",
                        tint = if (masterKeyEnabled) terminalAccent else Color.Yellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "OFFLINE AES-128 CONTEXT ENCRYPTION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Active Secret Hash: ..." + viewModel.encryptionKey.value.hashCode().toString(),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                IconButton(
                    onClick = { viewModel.clearTerminalLog() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Console",
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Terminal Output Logger Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(terminalBlack)
                .border(BorderStroke(1.dp, Color(0xFF334155)), RoundedCornerShape(12.dp))
        ) {
            if (logs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "No Logs yet",
                        tint = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Orchestrator Sandbox Ready",
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Attach sandboxed contexts and submit prompt matrices. True native llama.cpp execution is wrapped and monitored locally.",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs) { log ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when (log.sender) {
                                        "USER" -> Color(0xFF0F172A).copy(alpha = 0.6f)
                                        else -> Color.Transparent
                                    }
                                )
                                .padding(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            when {
                                                log.sender == "USER" -> userAccent.copy(alpha = 0.2f)
                                                log.sender.contains("SYS") || log.sender.contains("NATIVE") -> Color.Green.copy(alpha = 0.2f)
                                                else -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = log.sender.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = when {
                                            log.sender == "USER" -> userAccent
                                            log.sender.contains("SYS") || log.sender.contains("NATIVE") -> Color.Green
                                            else -> Color(0xFFA78BFA)
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Offset: ${log.timestamp % 1000000}",
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.text,
                                fontSize = 13.sp,
                                color = if (log.sender == "USER") Color.White else Color.White.copy(alpha = 0.85f),
                                fontFamily = if (log.sender == "USER") FontFamily.Default else FontFamily.Monospace
                            )
                        }
                    }
                    
                    if (isBusy) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = terminalAccent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Computing matrix weights locally...",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Context Prompt Attachments Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // File attachment toggle
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.toggleFileSystemContext() }
                    .testTag("file_context_toggle"),
                shape = RoundedCornerShape(8.dp),
                color = if (clickedFile) Color(0xFF1E293B) else Color(0xFF334155).copy(alpha = 0.3f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (clickedFile) userAccent else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "File Sandbox",
                        tint = if (clickedFile) userAccent else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "File Sandbox",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (clickedFile) Color.White else Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }

            // Contacts Index attachment toggle
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.toggleContactsContext() }
                    .testTag("contacts_context_toggle"),
                shape = RoundedCornerShape(8.dp),
                color = if (clickedContact) Color(0xFF1E293B) else Color(0xFF334155).copy(alpha = 0.3f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (clickedContact) userAccent else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Contacts Index",
                        tint = if (clickedContact) userAccent else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Contacts",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (clickedContact) Color.White else Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }

        // Bottom command controller
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = {
                    Text(
                        "Input local command context...",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("terminal_prompt_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = userAccent,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = terminalBlack,
                    unfocusedContainerColor = terminalBlack
                ),
                shape = RoundedCornerShape(10.dp)
            )
            
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.processLocalPrompt(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("submit_prompt_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = userAccent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Submit matrix pipeline",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
