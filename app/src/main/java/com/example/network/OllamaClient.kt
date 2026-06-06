package com.example.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object OllamaClient {
    private const val TAG = "OllamaClient"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun checkConnection(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (url.endsWith("/")) "${url}api/tags" else "$url/api/tags"
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed for $url: ${e.message}")
            false
        }
    }

    suspend fun getAvailableModels(url: String): List<String> = withContext(Dispatchers.IO) {
        val modelList = mutableListOf<String>()
        try {
            val endpoint = if (url.endsWith("/")) "${url}api/tags" else "$url/api/tags"
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val modelsArray = json.optJSONArray("models") ?: JSONArray()
                    for (i in 0 until modelsArray.length()) {
                        val modelObj = modelsArray.optJSONObject(i)
                        val name = modelObj?.optString("name", "") ?: ""
                        if (name.isNotEmpty()) {
                            modelList.add(name)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching models from $url: ${e.message}")
        }
        modelList
    }

    suspend fun generatePrompt(url: String, model: String, prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val endpoint = if (url.endsWith("/")) "${url}api/generate" else "$url/api/generate"
            val payload = JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("stream", false)
            }
            
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    json.optString("response", "No response content found.")
                } else {
                    "❌ HTTP Error: ${response.code} received from Ollama Server."
                }
            }
        } catch (e: IOException) {
            "❌ Network Error connecting to host. Server appears to be unreachable."
        } catch (e: Exception) {
            "❌ Unexpected exception during call: ${e.message}"
        }
    }
}
