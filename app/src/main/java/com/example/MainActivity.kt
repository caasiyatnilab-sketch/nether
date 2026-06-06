package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navigation.AppDestinations
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.viewmodel.LocalAiViewModel

// Central theme definition to avoid missing old theme imports
@Composable
fun LocalAiStudioTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF38BDF8), // sky-400
        secondary = Color(0xFF06B6D4), // cyan-500
        tertiary = Color(0xFF8B5CF6), // violet-500
        background = Color(0xFF0F172A), // slate-900 cosmic dark background
        surface = Color(0xFF1E293B), // slate-800
        onPrimary = Color(0xFF020617),
        onSecondary = Color(0xFF020617),
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Full Edge-to-Edge immersion support as per frontend styling best practices!
        enableEdgeToEdge()
        
        setContent {
            LocalAiStudioTheme {
                val viewModel: LocalAiViewModel = viewModel()
                var currentRoute by remember { mutableStateOf(AppDestinations.DASHBOARD) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .testTag("main_bottom_nav_bar")
                                .windowInsetsPadding(WindowInsets.navigationBars),
                            containerColor = Color(0xFF020617),
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = currentRoute == AppDestinations.DASHBOARD,
                                onClick = { currentRoute = AppDestinations.DASHBOARD },
                                modifier = Modifier.testTag("nav_item_dashboard"),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Dashboard"
                                    )
                                },
                                label = { Text("Dashboard", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF38BDF8),
                                    unselectedIconColor = Color.White.copy(alpha = 0.4f),
                                    selectedTextColor = Color(0xFF38BDF8),
                                    unselectedTextColor = Color.White.copy(alpha = 0.4f),
                                    indicatorColor = Color(0xFF1E293B)
                                )
                            )

                            NavigationBarItem(
                                selected = currentRoute == AppDestinations.ORCHESTRATOR,
                                onClick = { currentRoute = AppDestinations.ORCHESTRATOR },
                                modifier = Modifier.testTag("nav_item_orchestrator"),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = "Orchestrator Terminal"
                                    )
                                },
                                label = { Text("Terminal", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF38BDF8),
                                    unselectedIconColor = Color.White.copy(alpha = 0.4f),
                                    selectedTextColor = Color(0xFF38BDF8),
                                    unselectedTextColor = Color.White.copy(alpha = 0.4f),
                                    indicatorColor = Color(0xFF1E293B)
                                )
                            )

                            NavigationBarItem(
                                selected = currentRoute == AppDestinations.VECTOR_RAG,
                                onClick = { currentRoute = AppDestinations.VECTOR_RAG },
                                modifier = Modifier.testTag("nav_item_diagnostics"),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Utilities & Hardware Tuning"
                                    )
                                },
                                label = { Text("Utilities", fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF38BDF8),
                                    unselectedIconColor = Color.White.copy(alpha = 0.4f),
                                    selectedTextColor = Color(0xFF38BDF8),
                                    unselectedTextColor = Color.White.copy(alpha = 0.4f),
                                    indicatorColor = Color(0xFF1E293B)
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color(0xFF0F172A))
                    ) {
                        when (currentRoute) {
                            AppDestinations.DASHBOARD -> DashboardScreen(viewModel)
                            AppDestinations.ORCHESTRATOR -> ChatScreen(viewModel)
                            AppDestinations.VECTOR_RAG -> DiagnosticsScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}
