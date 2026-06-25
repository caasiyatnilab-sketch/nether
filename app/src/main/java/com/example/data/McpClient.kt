package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal but real Model Context Protocol client speaking JSON-RPC 2.0 over
 * HTTP POST (the "streamable HTTP" request/response form). Lets the app discover
 * (`tools/list`) and invoke (`tools/call`) tools exposed by an external MCP
 * server the user configures. No server is bundled — point it at your own.
 */
class McpClient {

    @Volatile
    private var serverUrl: String = ""

    @Volatile
    private var initialized = false

    private val ids = AtomicInteger(1)

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = "application/json".toMediaType()

    fun configure(url: String) {
        serverUrl = url.trim()
        initialized = false
    }

    fun isConfigured(): Boolean = serverUrl.isNotBlank()

    fun endpoint(): String = serverUrl

    private fun rpc(method: String, params: JSONObject?): JSONObject {
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", ids.getAndIncrement())
            .put("method", method)
        if (params != null) payload.put("params", params)

        val req = Request.Builder()
            .url(serverUrl)
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(json))
            .build()

        http.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("MCP HTTP ${resp.code}: ${bodyStr.take(200)}")
            val obj = JSONObject(bodyStr)
            if (obj.has("error")) {
                val err = obj.getJSONObject("error")
                error("MCP error ${err.optInt("code")}: ${err.optString("message")}")
            }
            return obj.optJSONObject("result") ?: JSONObject()
        }
    }

    private fun ensureInitialized() {
        if (initialized) return
        val params = JSONObject()
            .put("protocolVersion", "2024-11-05")
            .put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "Nether").put("version", "1.0.0"))
        rpc("initialize", params)
        initialized = true
    }

    data class McpTool(val name: String, val description: String)

    suspend fun listTools(): List<McpTool> = withContext(Dispatchers.IO) {
        require(isConfigured()) { "No MCP server configured" }
        ensureInitialized()
        val result = rpc("tools/list", null)
        val arr = result.optJSONArray("tools") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val t = arr.getJSONObject(i)
            McpTool(t.optString("name"), t.optString("description"))
        }
    }

    suspend fun callTool(name: String, argumentsJson: String): String = withContext(Dispatchers.IO) {
        require(isConfigured()) { "No MCP server configured" }
        ensureInitialized()
        val args = if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
        val params = JSONObject().put("name", name).put("arguments", args)
        val result = rpc("tools/call", params)
        // MCP returns content as an array of typed parts; concatenate text parts.
        val content = result.optJSONArray("content") ?: return@withContext result.toString()
        buildString {
            for (i in 0 until content.length()) {
                val part = content.getJSONObject(i)
                if (part.optString("type") == "text") append(part.optString("text"))
            }
        }.ifBlank { result.toString() }
    }

    /** Handle `/mcp ...` chat commands. */
    suspend fun handleCommand(args: List<String>): String {
        if (!isConfigured()) return "No MCP server configured. Set one in Settings."
        return try {
            when (args.firstOrNull()?.lowercase()) {
                "list", null -> {
                    val tools = listTools()
                    if (tools.isEmpty()) "MCP server exposes no tools."
                    else "MCP tools:\n" + tools.joinToString("\n") { "• ${it.name} — ${it.description}" }
                }
                "call" -> {
                    val tool = args.getOrNull(1) ?: return "Usage: /mcp call <tool> <json-args>"
                    val jsonArgs = args.drop(2).joinToString(" ")
                    "🔧 " + callTool(tool, jsonArgs)
                }
                else -> "Usage: /mcp list | /mcp call <tool> <json-args>"
            }
        } catch (e: Exception) {
            Log.w(TAG, "MCP command failed", e)
            "MCP request failed: ${e.message}"
        }
    }

    companion object {
        private const val TAG = "McpClient"
    }
}
