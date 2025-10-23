package com.myagentos.app.data.service

import com.myagentos.app.R
import com.myagentos.app.data.model.BlinkMetadata
import com.myagentos.app.data.model.BlinkParameter
import com.myagentos.app.data.model.BlinkError
import com.myagentos.app.data.model.BlinkResponse
import com.myagentos.app.data.model.BlinkAction
import com.myagentos.app.data.model.BlinkLinkedAction
import com.myagentos.app.data.model.BlinkLinks

import android.net.Uri
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Service for handling Solana Actions/Blinks
 */
class BlinkService {
    
    companion object {
        private const val TAG = "BlinkService"
        private const val TIMEOUT_SECONDS = 30L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Parse a blink URL to extract the action endpoint URL
     * Supports:
     * - solana-action:https://example.com/api/action
     * - solana-action%3Ahttps%3A%2F%2Fexample.com%2Fapi%2Faction (URL encoded)
     */
    fun parseBlinkUrl(url: String): String? {
        return try {
            Log.d(TAG, "Parsing blink URL: $url")
            
            // Decode URL if it's encoded
            val decodedUrl = URLDecoder.decode(url, "UTF-8")
            Log.d(TAG, "Decoded URL: $decodedUrl")
            
            // Extract the action URL from solana-action: scheme
            if (decodedUrl.startsWith("solana-action:", ignoreCase = true)) {
                val actionUrl = decodedUrl.substring("solana-action:".length)
                Log.d(TAG, "Extracted action URL: $actionUrl")
                
                // Validate it's a proper HTTPS URL
                if (actionUrl.startsWith("https://", ignoreCase = true)) {
                    return actionUrl
                } else {
                    Log.e(TAG, "Action URL does not start with https://")
                    return null
                }
            }
            
            Log.e(TAG, "URL does not start with solana-action:")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing blink URL: ${e.message}", e)
            null
        }
    }
    
    /**
     * Fetch blink metadata from action endpoint (GET request)
     */
    suspend fun fetchBlinkMetadata(actionUrl: String): BlinkMetadata? {
        return try {
            Log.d(TAG, "Fetching blink metadata from: $actionUrl")
            val startTime = System.currentTimeMillis()
            
            val request = Request.Builder()
                .url(actionUrl)
                .addHeader("Accept", "application/json")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val fetchTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Fetch completed in ${fetchTime}ms, status code: ${response.code}")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch blink metadata: HTTP ${response.code}")
                return null
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Response body is null")
                return null
            }
            
            Log.d(TAG, "Response body: $responseBody")
            
            // Parse JSON response
            val json = JSONObject(responseBody)
            
            // Parse parameters if present
            val parameters = parseParameters(json.optJSONArray("parameters"))
            
            val iconUrl = json.optString("icon", "")
            val imageUrl = json.optString("image", null)
            
            Log.e(TAG, "Raw JSON icon: $iconUrl")
            Log.e(TAG, "Raw JSON image: $imageUrl")
            Log.e(TAG, "Raw JSON has links: ${json.has("links")}")
            if (json.has("links")) {
                Log.e(TAG, "Links JSON: ${json.getJSONObject("links")}")
            }
            
            val metadata = BlinkMetadata(
                title = json.optString("title", "Untitled Action"),
                icon = iconUrl,
                description = json.optString("description", ""),
                label = json.optString("label", null),
                disabled = json.optBoolean("disabled", false),
                links = parseLinks(json.optJSONObject("links")),
                error = parseError(json.optJSONObject("error")),
                actionUrl = actionUrl,
                parameters = parameters,
                image = imageUrl
            )
            
            Log.e(TAG, "Successfully parsed metadata: title=${metadata.title}, icon=${metadata.icon}, image=${metadata.image}, parameters=${parameters?.size ?: 0}")
            metadata
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching blink metadata: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching blink metadata: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parse links object from JSON
     */
    private fun parseLinks(linksJson: JSONObject?): BlinkLinks? {
        if (linksJson == null) return null
        
        return try {
            val actionsArray = linksJson.optJSONArray("actions")
            val actions = mutableListOf<BlinkLinkedAction>()
            
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val actionJson = actionsArray.getJSONObject(i)
                    val actionParameters = parseParameters(actionJson.optJSONArray("parameters"))
                    actions.add(
                        BlinkLinkedAction(
                            href = actionJson.getString("href"),
                            label = actionJson.getString("label"),
                            parameters = actionParameters
                        )
                    )
                }
            }
            
            BlinkLinks(actions = actions)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing links: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parse error object from JSON
     */
    private fun parseError(errorJson: JSONObject?): BlinkError? {
        if (errorJson == null) return null
        
        return try {
            BlinkError(message = errorJson.getString("message"))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing error object: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parse parameters array from JSON
     */
    private fun parseParameters(parametersArray: org.json.JSONArray?): List<BlinkParameter>? {
        if (parametersArray == null || parametersArray.length() == 0) return null
        
        return try {
            val parameters = mutableListOf<BlinkParameter>()
            for (i in 0 until parametersArray.length()) {
                val paramJson = parametersArray.getJSONObject(i)
                parameters.add(
                    BlinkParameter(
                        name = paramJson.getString("name"),
                        label = paramJson.optString("label", paramJson.getString("name")),
                        required = paramJson.optBoolean("required", false)
                    )
                )
            }
            parameters
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing parameters: ${e.message}", e)
            null
        }
    }
    
    /**
     * Execute a blink action via POST request to get a transaction
     * @param actionUrl The action endpoint URL
     * @param account The user's wallet account public key (base58 encoded)
     * @param parameters Optional parameters for the action
     * @return Base64 encoded transaction string, or null on error
     */
    suspend fun executeBlinkAction(
        actionUrl: String,
        account: String,
        parameters: Map<String, String> = emptyMap()
    ): String? {
        return try {
            Log.e(TAG, "Executing blink action: $actionUrl with account: $account")
            
            // Build POST request body
            val json = JSONObject().apply {
                put("account", account)
            }
            
            val requestBody = json.toString()
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(actionUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.e(TAG, "POST Response code: ${response.code}")
            Log.e(TAG, "POST Response body: $responseBody")
            
            if (!response.isSuccessful || responseBody == null) {
                Log.e(TAG, "POST request failed: ${response.code}")
                return null
            }
            
            val responseJson = JSONObject(responseBody)
            
            // Extract transaction from response
            val transaction = responseJson.optString("transaction", null)
            
            if (transaction == null) {
                Log.e(TAG, "No transaction field in POST response")
                return null
            }
            
            Log.e(TAG, "Successfully retrieved transaction (${transaction.length} chars)")
            transaction
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error executing blink action: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error executing blink action: ${e.message}", e)
            null
        }
    }
    
    /**
     * Submit a signed transaction back to the action endpoint
     * @param actionUrl The action endpoint URL
     * @param signedTransaction The signed transaction in base64 format
     * @return The transaction signature if successful, null otherwise
     */
    suspend fun submitSignedTransaction(
        actionUrl: String,
        signedTransaction: String,
        account: String
    ): String? {
        return try {
            Log.d(TAG, "Submitting signed transaction to: $actionUrl")
            
            // Build POST request body with signed transaction
            val json = JSONObject().apply {
                put("account", account)
                put("transaction", signedTransaction)
            }
            
            val requestBody = json.toString()
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(actionUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Submit response code: ${response.code}")
            Log.d(TAG, "Submit response body: $responseBody")
            
            if (!response.isSuccessful || responseBody == null) {
                Log.e(TAG, "Failed to submit signed transaction: HTTP ${response.code}")
                return null
            }
            
            val responseJson = JSONObject(responseBody)
            
            // Extract signature from response
            val signature = responseJson.optString("signature", null)
            
            if (signature == null) {
                Log.e(TAG, "No signature field in submit response")
                return null
            }
            
            Log.d(TAG, "Transaction submitted successfully: $signature")
            signature
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error submitting signed transaction: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting signed transaction: ${e.message}", e)
            null
        }
    }
}

