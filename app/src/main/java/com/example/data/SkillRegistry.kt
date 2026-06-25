package com.example.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One callable on-device capability. Skills are real actions against Android
 * APIs (battery, clipboard, torch, etc.) — invoked from chat via slash commands
 * like `/battery` or `/torch on`. Each declares its required runtime permission
 * (if any) so the UI can request it before use.
 */
data class Skill(
    val command: String,
    val description: String,
    val usage: String,
    val requiresPermission: String? = null,
    val run: (Context, List<String>) -> String
)

/**
 * Registry of device-control skills plus optional MCP-provided tools. The chat
 * layer checks [tryHandle] first; if the input is a known command it executes
 * the real action and returns its output instead of running inference.
 */
class SkillRegistry(private val mcp: McpClient) {

    private val builtIns: List<Skill> = listOf(
        Skill("help", "List available skills", "/help") { _, _ -> helpText() },
        Skill("skills", "List available skills", "/skills") { _, _ -> helpText() },
        Skill("time", "Current device date & time", "/time") { _, _ ->
            "🕒 " + SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.getDefault()).format(Date())
        },
        Skill("battery", "Battery level & charging state", "/battery") { ctx, _ -> battery(ctx) },
        Skill("device", "Device hardware summary", "/device") { ctx, _ -> device(ctx) },
        Skill("clipboard", "Read or set the clipboard", "/clipboard get | /clipboard set <text>") { ctx, args ->
            clipboard(ctx, args)
        },
        Skill("torch", "Toggle the flashlight", "/torch on | /torch off") { ctx, args ->
            torch(ctx, args)
        }
    )

    fun all(): List<Skill> = builtIns

    /** Returns the human-readable name list for showing in the UI / system prompt. */
    fun summaryForPrompt(): String =
        "Available on-device skills (type the command): " +
            builtIns.joinToString(", ") { "/${it.command}" } +
            if (mcp.isConfigured()) " plus MCP tools via /mcp." else ""

    /**
     * If [input] is a slash command, execute it and return the result.
     * Returns null when [input] is not a skill (so normal inference runs).
     */
    suspend fun tryHandle(context: Context, input: String): String? {
        val trimmed = input.trim()
        if (!trimmed.startsWith("/")) return null
        val parts = trimmed.removePrefix("/").split(Regex("\\s+"))
        val cmd = parts.firstOrNull()?.lowercase() ?: return null
        val args = parts.drop(1)

        if (cmd == "mcp") return mcp.handleCommand(args)

        val skill = builtIns.firstOrNull { it.command == cmd }
            ?: return "Unknown skill '/$cmd'. Type /help for the list."
        return runCatching { skill.run(context, args) }
            .getOrElse { "Skill /$cmd failed: ${it.message}" }
    }

    fun isCommand(input: String): Boolean = input.trim().startsWith("/")

    private fun helpText(): String {
        val lines = builtIns.joinToString("\n") { "• ${it.usage} — ${it.description}" }
        val mcpLine = if (mcp.isConfigured()) "\n• /mcp list | /mcp call <tool> <json> — MCP server tools" else ""
        return "Skills you can run on-device:\n$lines$mcpLine"
    }

    private fun battery(ctx: Context): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = plugged != 0
        return "🔋 Battery: $level% ${if (charging) "(charging)" else "(on battery)"}"
    }

    private fun device(ctx: Context): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mem = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return buildString {
            append("📱 ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("CPU: ${Runtime.getRuntime().availableProcessors()} cores, ${Build.SUPPORTED_ABIS.firstOrNull()}\n")
            append("RAM: ${"%.1f".format(mem.totalMem / 1.0e9)} GB total, ${"%.1f".format(mem.availMem / 1.0e9)} GB free")
        }
    }

    private fun clipboard(ctx: Context, args: List<String>): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        return when (args.firstOrNull()?.lowercase()) {
            "set" -> {
                val text = args.drop(1).joinToString(" ")
                if (text.isBlank()) return "Usage: /clipboard set <text>"
                cm.setPrimaryClip(android.content.ClipData.newPlainText("nether", text))
                "📋 Copied to clipboard."
            }
            "get", null -> {
                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
                if (text.isNullOrBlank()) "📋 Clipboard is empty." else "📋 Clipboard: $text"
            }
            else -> "Usage: /clipboard get | /clipboard set <text>"
        }
    }

    private fun torch(ctx: Context, args: List<String>): String {
        val on = when (args.firstOrNull()?.lowercase()) {
            "on", "1", "true" -> true
            "off", "0", "false" -> false
            else -> return "Usage: /torch on | /torch off"
        }
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camId = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "🔦 No flashlight on this device."
        cm.setTorchMode(camId, on)
        return "🔦 Flashlight ${if (on) "ON" else "OFF"}."
    }

    @Suppress("unused")
    private fun hasPermission(ctx: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, perm) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
