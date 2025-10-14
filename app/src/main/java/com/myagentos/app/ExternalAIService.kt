package com.myagentos.app

import android.net.Uri
import com.myagentos.app.BuildConfig
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ExternalAIService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateResponse(userMessage: String, modelType: ModelType): String {
        return when (modelType) {
            ModelType.EXTERNAL_CHATGPT -> {
                generateChatGPTResponse(userMessage)
            }
            ModelType.EXTERNAL_GROK -> {
                generateGrokResponse(userMessage)
            }
        }
    }

    private suspend fun generateChatGPTResponse(message: String): String {
        // For now, return a simple message about ChatGPT
        delay(1000)
        return "ChatGPT integration not implemented yet. Please use Grok AI."
    }
    
    private suspend fun generateGrokResponse(message: String): String {
        val apiKey = BuildConfig.GROK_API_KEY
        
        if (apiKey.isNullOrEmpty()) {
            return "Grok API key not configured. Please check your local.properties file."
        }
        
        return makeGrokAPICall(message, apiKey)
    }
    
    private suspend fun makeGrokAPICall(message: String, apiKey: String): String {
        try {
            // Use the actual Grok API endpoint
            val url = "https://api.x.ai/v1/chat/completions"
            
            val json = JSONObject().apply {
                put("model", "grok-4-fast-non-reasoning")
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                })
                put("max_tokens", 2000)
                put("temperature", 0.7)
            }
            
            android.util.Log.d("ExternalAIService", "Sending request to Grok API, message length: ${message.length}")
            
            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response: Response = client.newCall(request).execute()
            
            android.util.Log.d("ExternalAIService", "Received response from Grok API, status: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody ?: "")
                
                // Parse the response
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val messageObj = firstChoice.getJSONObject("message")
                    val content = messageObj.getString("content")
                    android.util.Log.d("ExternalAIService", "Successfully got response, length: ${content.length}")
                    return content
                }
            } else {
                val errorBody = response.body?.string()
                android.util.Log.e("ExternalAIService", "Grok API Error (${response.code}): $errorBody")
                return "Grok API Error (${response.code}): $errorBody"
            }
            
            return "No response from Grok API"
            
        } catch (e: IOException) {
            android.util.Log.e("ExternalAIService", "IOException calling Grok API: ${e.message}", e)
            return "Network error calling Grok API: ${e.message}. Please check your internet connection."
        } catch (e: Exception) {
            android.util.Log.e("ExternalAIService", "Error calling Grok API: ${e.message}", e)
            return "Error calling Grok API: ${e.message}"
        }
    }
}