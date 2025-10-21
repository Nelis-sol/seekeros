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
    
    suspend fun generateResponseWithHistory(
        userMessage: String, 
        modelType: ModelType,
        conversationHistory: List<Pair<String, String>>
    ): String {
        return when (modelType) {
            ModelType.EXTERNAL_CHATGPT -> {
                generateChatGPTResponse(userMessage)
            }
            ModelType.EXTERNAL_GROK -> {
                generateGrokResponseWithHistory(userMessage, conversationHistory)
            }
        }
    }
    
    suspend fun generateResponseWithToolContext(
        userMessage: String,
        toolContext: String,
        conversationHistory: List<Pair<String, String>>,
        collectedParams: Map<String, Any>
    ): String {
        val apiKey = BuildConfig.GROK_API_KEY
        
        if (apiKey.isNullOrEmpty()) {
            return "Grok API key not configured. Please check your local.properties file."
        }
        
        return makeGrokAPICallWithToolContext(userMessage, toolContext, conversationHistory, collectedParams, apiKey)
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
    
    private suspend fun generateGrokResponseWithHistory(
        message: String, 
        conversationHistory: List<Pair<String, String>>
    ): String {
        val apiKey = BuildConfig.GROK_API_KEY
        
        if (apiKey.isNullOrEmpty()) {
            return "Grok API key not configured. Please check your local.properties file."
        }
        
        return makeGrokAPICallWithHistory(message, conversationHistory, apiKey)
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
    
    private suspend fun makeGrokAPICallWithHistory(
        message: String, 
        conversationHistory: List<Pair<String, String>>,
        apiKey: String
    ): String {
        try {
            val url = "https://api.x.ai/v1/chat/completions"
            
            val json = JSONObject().apply {
                put("model", "grok-4-fast-non-reasoning")
                put("messages", org.json.JSONArray().apply {
                    // Add system prompt for AgentOS
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are AgentOS Assistant. Be concise and conversational. Don't narrate actions like 'loading' or 'invoking' tools - the user can see them. Keep responses brief unless the user asks for detailed information.")
                    })
                    
                    // Add conversation history
                    for (historyItem in conversationHistory) {
                        put(JSONObject().apply {
                            put("role", historyItem.first)
                            put("content", historyItem.second)
                        })
                    }
                    
                    // Add current user message (if not already in history)
                    if (conversationHistory.isEmpty() || conversationHistory.last().second != message) {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", message)
                        })
                    }
                })
                put("max_tokens", 2000)
                put("temperature", 0.7)
            }
            
            android.util.Log.d("ExternalAIService", "Sending request to Grok API with history (${conversationHistory.size} messages)")
            
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
                
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val messageObj = firstChoice.getJSONObject("message")
                    val content = messageObj.getString("content")
                    android.util.Log.d("ExternalAIService", "Successfully got response with history, length: ${content.length}")
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
            return "Network error calling Grok API: ${e.message}"
        } catch (e: Exception) {
            android.util.Log.e("ExternalAIService", "Error calling Grok API with history: ${e.message}", e)
            return "Error calling Grok API: ${e.message}"
        }
    }
    
    private suspend fun makeGrokAPICallWithToolContext(
        message: String,
        toolContext: String,
        conversationHistory: List<Pair<String, String>>,
        collectedParams: Map<String, Any>,
        apiKey: String
    ): String {
        try {
            val url = "https://api.x.ai/v1/chat/completions"
            
            // Parse tool context to build system message
            val toolContextJson = JSONObject(toolContext)
            val toolTitle = toolContextJson.getString("tool_title")
            val toolDescription = toolContextJson.getString("tool_description")
            val inputSchema = toolContextJson.getJSONObject("input_schema")
            val requiredParams = toolContextJson.getJSONArray("required_parameters")
            
            // Build parameter descriptions
            val paramDescriptions = mutableListOf<String>()
            val properties = if (inputSchema.has("properties")) {
                inputSchema.getJSONObject("properties")
            } else {
                JSONObject()
            }
            
            for (i in 0 until requiredParams.length()) {
                val paramName = requiredParams.getString(i)
                if (properties.has(paramName)) {
                    val paramSchema = properties.getJSONObject(paramName)
                    val paramDesc = paramSchema.optString("description", "")
                    val paramType = paramSchema.optString("type", "string")
                    paramDescriptions.add("- $paramName (${paramType}): $paramDesc")
                } else {
                    paramDescriptions.add("- $paramName")
                }
            }
            
            // Build collected parameters status
            val collectedStatus = if (collectedParams.isEmpty()) {
                "None yet"
            } else {
                collectedParams.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
            }
            
            val stillNeeded = requiredParams.let { params ->
                val paramsList = (0 until params.length()).map { params.getString(it) }
                paramsList.filter { !collectedParams.containsKey(it) }
            }
            
            // Create system prompt with tool context and parameter collection instructions
            val systemPrompt = """You're helping with "$toolTitle".

Required: ${paramDescriptions.joinToString(", ")}
Have: $collectedStatus
Need: ${if (stillNeeded.isEmpty()) "Nothing - ready!" else stillNeeded.joinToString(", ")}

Ask for missing parameters briefly. Be natural. Don't narrate actions - the user can see the tool loading."""

            val json = JSONObject().apply {
                put("model", "grok-4-fast-reasoning")
                put("messages", org.json.JSONArray().apply {
                    // Add system prompt
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    
                    // Add conversation history
                    for (historyItem in conversationHistory) {
                        put(JSONObject().apply {
                            put("role", historyItem.first)
                            put("content", historyItem.second)
                        })
                    }
                    
                    // Add current user message (if not already in history)
                    if (conversationHistory.isEmpty() || conversationHistory.last().second != message) {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", message)
                        })
                    }
                })
                put("max_tokens", 2000)
                put("temperature", 0.7)
            }
            
            android.util.Log.d("ExternalAIService", "Sending tool context request to Grok API")
            android.util.Log.d("ExternalAIService", "System prompt: $systemPrompt")
            
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
                
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val messageObj = firstChoice.getJSONObject("message")
                    val content = messageObj.getString("content")
                    android.util.Log.d("ExternalAIService", "Successfully got response for tool context")
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
            return "Network error calling Grok API: ${e.message}"
        } catch (e: Exception) {
            android.util.Log.e("ExternalAIService", "Error calling Grok API with tool context: ${e.message}", e)
            return "Error calling Grok API: ${e.message}"
        }
    }
}