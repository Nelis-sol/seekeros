package com.myagentos.app.data.service

import com.myagentos.app.R
import com.myagentos.app.data.model.McpContent
import com.myagentos.app.data.model.McpAnnotations
import com.myagentos.app.data.model.ToolsCapability
import com.myagentos.app.data.model.ResourcesCapability
import com.myagentos.app.data.model.PromptsCapability
import com.myagentos.app.data.model.JsonRpcResponse
import com.myagentos.app.data.model.JsonRpcRequest
import com.myagentos.app.data.model.McpCapabilities
import com.myagentos.app.data.model.McpResource
import com.myagentos.app.data.model.McpToolResult
import com.myagentos.app.data.model.McpTool
import com.myagentos.app.data.model.parseJsonRpcResponse
import com.myagentos.app.data.model.toJson

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * MCP Service - JSON-RPC client for Model Context Protocol
 * 
 * Implements the MCP protocol specification:
 * https://modelcontextprotocol.io/specification/2025-06-18/server/tools
 * 
 * Handles:
 * - Tool discovery (tools/list)
 * - Tool invocation (tools/call)
 * - Resource fetching (resources/read)
 * - Connection management
 * 
 * This is a singleton to share SSE connections across activities
 */
class McpService private constructor() {
    
    companion object {
        private const val TAG = "McpService"
        private const val TIMEOUT_SECONDS = 30L
        private const val JSON_RPC_VERSION = "2.0"
        
        @Volatile
        private var instance: McpService? = null
        
        fun getInstance(): McpService {
            return instance ?: synchronized(this) {
                instance ?: McpService().also { instance = it }
            }
        }
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // No read timeout for SSE streaming
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private var requestId = 1
    
    // Session ID will be set from server response during initialization
    private var sessionId: String? = null
    
    // Track current server URL
    private var currentServerUrl: String? = null
    
    // For SSE servers: store pending requests waiting for responses
    private val pendingRequests = mutableMapOf<Int, CompletableDeferred<JsonRpcResponse>>()
    
    // SSE response reader
    private var sseReader: Response? = null
    
    // Track SSE connection state
    private var sseConnectionActive = false
    
    // Connection status callback (receives server URL)
    private var onConnectionLost: ((String) -> Unit)? = null
    
    /**
     * Set callback for when connection is lost
     */
    fun setOnConnectionLost(callback: (String) -> Unit) {
        onConnectionLost = callback
    }
    
    /**
     * Set the session ID manually (for sharing session across service instances)
     */
    fun setSessionId(id: String) {
        sessionId = id
        Log.d(TAG, "Session ID set manually: $id")
    }
    
    /**
     * Get the current session ID
     */
    fun getSessionId(): String? = sessionId
    
    /**
     * Reset all connection state (call when app restarts)
     */
    fun resetConnections() {
        Log.d(TAG, "Resetting all MCP connections")
        
        // Close SSE connection if open
        sseReader?.close()
        sseReader = null
        sseConnectionActive = false
        
        // Clear session and server URL
        sessionId = null
        currentServerUrl = null
        
        // Clear pending requests
        pendingRequests.values.forEach { deferred ->
            deferred.cancel()
        }
        pendingRequests.clear()
        
        Log.d(TAG, "✓ All MCP connections reset")
    }
    
    /**
     * Initialize connection to MCP server and get capabilities
     * 
     * @param serverUrl The MCP server endpoint URL
     * @return Server capabilities
     */
    suspend fun initialize(serverUrl: String): McpCapabilities? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing connection to: $serverUrl")
            
            // Store server URL for connection tracking
            currentServerUrl = serverUrl
            
            // For SSE-style servers (like fly.dev), establish SSE connection first
            val isSseServer = serverUrl.endsWith("/mcp")
            if (isSseServer) {
                Log.d(TAG, "Detected SSE server, establishing SSE connection")
                establishSseConnection(serverUrl)
            }
            
            val response = sendJsonRpc(
                serverUrl = serverUrl,
                method = "initialize",
                params = mapOf(
                    "protocolVersion" to JSON_RPC_VERSION,
                    "capabilities" to emptyMap<String, Any>(),
                    "clientInfo" to mapOf(
                        "name" to "AgentOS",
                        "version" to "1.0.0"
                    )
                ),
                includeSessionId = false  // Don't include session ID for initialization
            )
            
            if (response.error != null) {
                Log.e(TAG, "Initialize error: ${response.error.message}")
                return@withContext null
            }
            
            // Parse capabilities from result
            val result = response.result as? Map<*, *>
            val capabilitiesMap = result?.get("capabilities") as? Map<*, *>
            
            if (capabilitiesMap != null) {
                parseCapabilities(capabilitiesMap)
            } else {
                // Default empty capabilities
                McpCapabilities()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MCP server: ${e.message}", e)
            null
        }
    }
    
    /**
     * Establish SSE connection for fly.dev style servers
     * Keeps the connection open and reads responses in the background
     */
    private suspend fun establishSseConnection(serverUrl: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Establishing SSE connection to: $serverUrl")
            
            val request = Request.Builder()
                .url(serverUrl)
                .get()
                .addHeader("Accept", "text/event-stream")
                .build()
            
            val response = client.newCall(request).execute()
            
            Log.d(TAG, "SSE response code: ${response.code}")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to establish SSE connection: ${response.code}")
                return@withContext
            }
            
            // Store the response to keep connection alive
            sseReader = response
            sseConnectionActive = true
            Log.d(TAG, "✓ SSE connection is now ACTIVE")
            
            val source = response.body?.source()
            if (source == null) {
                Log.e(TAG, "No response body for SSE connection")
                sseConnectionActive = false
                return@withContext
            }
            
            // Read first event which contains the endpoint with session ID
            // Format: "event: endpoint\ndata: /mcp/messages?sessionId=<id>\n\n"
            val firstLine = source.readUtf8Line() ?: ""
            val secondLine = source.readUtf8Line() ?: ""
            
            Log.d(TAG, "SSE first line: $firstLine")
            Log.d(TAG, "SSE second line: $secondLine")
            
            if (secondLine.startsWith("data: ")) {
                val endpointUrl = secondLine.removePrefix("data: ").trim()
                Log.d(TAG, "SSE endpoint: $endpointUrl")
                
                // Extract sessionId parameter
                val sessionIdMatch = Regex("sessionId=([^&\\s]+)").find(endpointUrl)
                if (sessionIdMatch != null) {
                    sessionId = sessionIdMatch.groupValues[1]
                    Log.d(TAG, "✓ Extracted session ID from SSE stream: $sessionId")
                }
            }
            
            // Skip empty line
            source.readUtf8Line()
            
            // Start background coroutine to read SSE responses
            @Suppress("OPT_IN_USAGE")
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d(TAG, "Starting SSE response reader")
                    readSseResponses(source)
                } catch (e: Exception) {
                    Log.e(TAG, "SSE reader error: ${e.message}", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error establishing SSE connection: ${e.message}", e)
        }
    }
    
    /**
     * Read SSE responses from the stream and route them to pending requests
     */
    private fun readSseResponses(source: BufferedSource) {
        try {
            Log.d(TAG, "SSE reader loop starting...")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                
                if (line.isEmpty()) {
                    // Empty lines are normal in SSE, skip them
                    continue
                }
                
                if (line.startsWith("event: message")) {
                    // Next line should be "data: {json}"
                    val dataLine = source.readUtf8Line() ?: break
                    
                    if (dataLine.startsWith("data: ")) {
                        val jsonData = dataLine.removePrefix("data: ").trim()
                        Log.e(TAG, "=".repeat(80))
                        Log.e(TAG, ">>> SSE MESSAGE RECEIVED FROM MCP SERVER")
                        Log.e(TAG, "Full JSON data length: ${jsonData.length}")
                        Log.e(TAG, "Full JSON data: $jsonData")
                        Log.e(TAG, "=".repeat(80))
                        
                        try {
                            val response = parseJsonRpcResponse(jsonData)
                            Log.e(TAG, ">>> Parsed JsonRpcResponse:")
                            Log.e(TAG, "  - id: ${response.id}")
                            Log.e(TAG, "  - result: ${response.result}")
                            Log.e(TAG, "  - error: ${response.error}")
                            
                            // Route response to the pending request
                            val deferred = pendingRequests.remove(response.id)
                            if (deferred != null) {
                                deferred.complete(response)
                                Log.e(TAG, "✓ Routed response for request ID ${response.id}")
                            } else {
                                Log.w(TAG, "⚠ No pending request for ID ${response.id}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing SSE message: ${e.message}", e)
                            e.printStackTrace()
                        }
                    }
                    // Skip empty line after data
                    source.readUtf8Line()
                }
            }
            Log.w(TAG, "⚠ SSE stream ended - connection closed!")
            sseConnectionActive = false
            
            // Notify UI that connection was lost (pass server URL)
            val serverUrl = currentServerUrl
            if (serverUrl != null) {
                Log.d(TAG, "Notifying UI of connection loss for: $serverUrl")
                onConnectionLost?.invoke(serverUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SSE responses: ${e.message}", e)
            sseConnectionActive = false
            
            // Notify UI that connection was lost (pass server URL)
            val serverUrl = currentServerUrl
            if (serverUrl != null) {
                Log.d(TAG, "Notifying UI of connection loss for: $serverUrl")
                onConnectionLost?.invoke(serverUrl)
            }
            
            // Complete all pending requests with error
            pendingRequests.values.forEach { deferred ->
                deferred.completeExceptionally(e)
            }
            pendingRequests.clear()
        }
    }
    
    /**
     * List available tools from MCP server
     * 
     * Method: tools/list
     * 
     * @param serverUrl The MCP server endpoint URL
     * @param cursor Optional pagination cursor
     * @return List of available tools
     */
    suspend fun listTools(
        serverUrl: String,
        cursor: String? = null
    ): List<McpTool> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Listing tools from: $serverUrl")
            
            val params = if (cursor != null) {
                mapOf("cursor" to cursor)
            } else {
                null
            }
            
            val response = sendJsonRpc(
                serverUrl = serverUrl,
                method = "tools/list",
                params = params
            )
            
            if (response.error != null) {
                Log.e(TAG, "List tools error: ${response.error.message}")
                return@withContext emptyList()
            }
            
            // Parse tools from result
            Log.d(TAG, "Result type: ${response.result?.javaClass?.name}")
            Log.d(TAG, "Result value: ${response.result}")
            
            // Handle JSONObject result
            val toolsArray = when (val result = response.result) {
                is JSONObject -> {
                    if (result.has("tools")) {
                        val jsonArray = result.getJSONArray("tools")
                        (0 until jsonArray.length()).map { jsonArray.get(it) }
                    } else null
                }
                is Map<*, *> -> result["tools"] as? List<*>
                else -> null
            }
            
            Log.d(TAG, "Tools array size: ${toolsArray?.size ?: 0}")
            
            if (toolsArray != null) {
                val tools = parseTools(toolsArray)
                Log.d(TAG, "Parsed ${tools.size} tools")
                tools
            } else {
                Log.w(TAG, "No tools array found in result")
                emptyList()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error listing tools: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Call a tool on MCP server
     * 
     * Method: tools/call
     * 
     * @param serverUrl The MCP server endpoint URL
     * @param toolName Name of the tool to invoke
     * @param arguments Tool arguments as key-value map
     * @param metadata Optional metadata for the call
     * @return Tool result with content and structured data
     */
    suspend fun callTool(
        serverUrl: String,
        toolName: String,
        arguments: Map<String, Any>,
        metadata: Map<String, Any>? = null
    ): McpToolResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Calling tool: $toolName on $serverUrl with args: $arguments")
            
            val params = mutableMapOf<String, Any>(
                "name" to toolName,
                "arguments" to arguments
            )
            
            metadata?.let {
                params["_meta"] = it
            }
            
            val response = sendJsonRpc(
                serverUrl = serverUrl,
                method = "tools/call",
                params = params
            )
            
            if (response.error != null) {
                Log.e(TAG, "Call tool error: ${response.error.message}")
                Log.e(TAG, "Error code: ${response.error.code}")
                Log.e(TAG, "Error data: ${response.error.data}")
                
                // Return error as tool result (preserve error data for payment requirements)
                return@withContext McpToolResult(
                    content = listOf(McpContent.Text(text = "Error: ${response.error.message}")),
                    structuredContent = null,
                    isError = true,
                    _meta = null,
                    errorData = response.error.data  // Preserve error data (contains paymentRequirements for -32001 errors)
                )
            }
            
            // Parse tool result - handle both JSONObject and Map
            val result = when (val res = response.result) {
                is JSONObject -> {
                    // Convert JSONObject to Map
                    val map = mutableMapOf<String, Any?>()
                    res.keys().forEach { key ->
                        map[key] = res.get(key)
                    }
                    map
                }
                is Map<*, *> -> res
                else -> null
            }
            
            if (result != null) {
                parseToolResult(result)
            } else {
                Log.w(TAG, "Could not parse tool result, response.result type: ${response.result?.javaClass?.name}")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calling tool: ${e.message}", e)
            McpToolResult(
                content = listOf(McpContent.Text(text = "Error: ${e.message}")),
                structuredContent = null,
                isError = true,
                _meta = null
            )
        }
    }
    
    /**
     * Read a resource from MCP server
     * 
     * Method: resources/read
     * 
     * @param serverUrl The MCP server endpoint URL
     * @param uri Resource URI (e.g., "ui://widget/map.html")
     * @return Resource with content
     */
    suspend fun readResource(
        serverUrl: String,
        uri: String,
        displayMode: String = "inline"
    ): McpResource? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Reading resource: $uri from $serverUrl with displayMode: $displayMode")
            
            // Include context with display mode so server can return appropriate UI
            val params = mutableMapOf<String, Any>("uri" to uri)
            
            // Add context if reading a UI resource
            if (uri.startsWith("ui://")) {
                params["context"] = mapOf(
                    "displayMode" to displayMode,
                    "platform" to "mobile",
                    "theme" to "dark",
                    "device" to mapOf(
                        "type" to "mobile",
                        "os" to "android",
                        "capabilities" to mapOf(
                            "touch" to true,
                            "mobile" to true,
                            "desktop" to false,
                            "hover" to false
                        )
                    )
                )
            }
            
            val response = sendJsonRpc(
                serverUrl = serverUrl,
                method = "resources/read",
                params = params
            )
            
            if (response.error != null) {
                Log.e(TAG, "Read resource error: ${response.error.message}")
                return@withContext null
            }
            
            Log.d(TAG, "Result type: ${response.result?.javaClass?.name}")
            Log.d(TAG, "Result value: ${response.result}")
            
            // Parse resource from result (handle both JSONObject and Map)
            val contentsArray = when (val result = response.result) {
                is JSONObject -> {
                    if (result.has("contents")) {
                        val jsonArray = result.getJSONArray("contents")
                        (0 until jsonArray.length()).map { jsonArray.get(it) }
                    } else null
                }
                is Map<*, *> -> result["contents"] as? List<*>
                else -> null
            }
            
            Log.d(TAG, "Contents array size: ${contentsArray?.size ?: 0}")
            
            if (contentsArray != null && contentsArray.isNotEmpty()) {
                val firstContent = when (val item = contentsArray[0]) {
                    is JSONObject -> item
                    is Map<*, *> -> item
                    else -> null
                }
                parseResource(firstContent)
            } else {
                Log.w(TAG, "No contents array found in result")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading resource: ${e.message}", e)
            null
        }
    }
    
    /**
     * List available resources from MCP server
     * 
     * Method: resources/list
     * 
     * @param serverUrl The MCP server endpoint URL
     * @param cursor Optional pagination cursor
     * @return List of available resources
     */
    suspend fun listResources(
        serverUrl: String,
        cursor: String? = null
    ): List<McpResource> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Listing resources from: $serverUrl")
            
            val params = if (cursor != null) {
                mapOf("cursor" to cursor)
            } else {
                null
            }
            
            val response = sendJsonRpc(
                serverUrl = serverUrl,
                method = "resources/list",
                params = params
            )
            
            if (response.error != null) {
                Log.e(TAG, "List resources error: ${response.error.message}")
                return@withContext emptyList()
            }
            
            // Parse resources from result
            val result = response.result as? Map<*, *>
            val resourcesArray = result?.get("resources") as? List<*>
            
            if (resourcesArray != null) {
                resourcesArray.mapNotNull { item ->
                    parseResource(item as? Map<*, *>)
                }
            } else {
                emptyList()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error listing resources: ${e.message}", e)
            emptyList()
        }
    }
    
    // ========================================================================
    // Private Helper Methods
    // ========================================================================
    
    /**
     * Send JSON-RPC request to MCP server
     */
    private suspend fun sendJsonRpc(
        serverUrl: String,
        method: String,
        params: Map<String, Any>?,
        includeSessionId: Boolean = true
    ): JsonRpcResponse = withContext(Dispatchers.IO) {
        val id = requestId++
        
        // Build JSON-RPC request
        val request = JsonRpcRequest(
            jsonrpc = JSON_RPC_VERSION,
            id = id,
            method = method,
            params = params
        )
        
        val requestBody = request.toJson()
            .toRequestBody("application/json".toMediaType())
        
        Log.d(TAG, "Sending JSON-RPC request: $method to $serverUrl")
        Log.d(TAG, "Request body: ${request.toJson()}")
        
        // Determine if this is a fly.dev style SSE server (has /mcp/messages endpoint)
        val isSseServer = serverUrl.contains("/mcp/messages") || serverUrl.endsWith("/mcp")
        
        // For SSE servers, adjust the URL
        val targetUrl = if (isSseServer) {
            val baseUrl = if (serverUrl.endsWith("/mcp")) {
                serverUrl.removeSuffix("/mcp")
            } else {
                serverUrl
            }
            
            // Even for initialization (includeSessionId=false), if we have a sessionId, use it
            // This is because we establish SSE connection first and get sessionId before calling initialize
            if (sessionId != null) {
                "$baseUrl/mcp/messages?sessionId=$sessionId"
            } else {
                // No session ID yet - this shouldn't happen for SSE servers
                Log.w(TAG, "⚠ SSE server but no session ID available")
                serverUrl
            }
        } else {
            serverUrl
        }
        
        Log.d(TAG, "Target URL: $targetUrl")
        
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
        
        // Only add session ID header for non-SSE servers (SSE uses query param)
        if (includeSessionId && sessionId != null && !isSseServer) {
            requestBuilder.addHeader("Mcp-Session-Id", sessionId!!)
        }
        
        val httpRequest = requestBuilder.build()
        
        // For SSE servers, responses come through the SSE stream, not HTTP response
        if (isSseServer) {
            // Check if SSE connection is still active
            if (!sseConnectionActive) {
                Log.w(TAG, "⚠ WARNING: SSE connection not active before sending request!")
                Log.w(TAG, "  Session ID: $sessionId")
                Log.w(TAG, "  SSE Reader: ${sseReader != null}")
            } else {
                Log.d(TAG, "✓ SSE connection is active, sending request")
            }
            
            // Create a deferred to wait for the response from SSE stream
            val deferred = CompletableDeferred<JsonRpcResponse>()
            pendingRequests[id] = deferred
            
            Log.d(TAG, "Registered pending request ID: $id")
            
            // POST the request (response will come via SSE stream)
            val response = client.newCall(httpRequest).execute()
            Log.d(TAG, "Posted request, response code: ${response.code}")
            response.close()
            
            // Wait for response from SSE stream (with timeout)
            try {
                return@withContext withTimeout(TIMEOUT_SECONDS * 1000) {
                    deferred.await()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                pendingRequests.remove(id)
                throw IOException("Request timed out waiting for SSE response")
            }
        } else {
            // Non-SSE server: synchronous request/response
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "Response code: ${response.code}")
            Log.d(TAG, "Response body: $responseBody")
            
            if (!response.isSuccessful || responseBody == null) {
                throw IOException("HTTP request failed: ${response.code}")
            }
            
            // Extract session ID from response headers (for initialization)
            if (!includeSessionId) {
                val sessionIdHeader = response.header("mcp-session-id")
                Log.d(TAG, "Looking for session ID in headers, found: $sessionIdHeader")
                if (sessionIdHeader != null) {
                    sessionId = sessionIdHeader
                    Log.d(TAG, "✓ Extracted and stored session ID: $sessionId")
                } else {
                    Log.w(TAG, "⚠ No session ID found in response headers")
                }
            } else {
                Log.d(TAG, "Using existing session ID: $sessionId")
            }
            
            // Handle Server-Sent Events (SSE) format (for Cloudflare Workers style)
            val jsonResponse = if (responseBody.startsWith("event: message\ndata: ")) {
                responseBody.substringAfter("data: ")
            } else {
                responseBody
            }
            
            return@withContext parseJsonRpcResponse(jsonResponse)
        }
    }
    
    /**
     * Parse capabilities from server response
     */
    private fun parseCapabilities(capabilitiesMap: Map<*, *>): McpCapabilities {
        val toolsMap = capabilitiesMap["tools"] as? Map<*, *>
        val resourcesMap = capabilitiesMap["resources"] as? Map<*, *>
        val promptsMap = capabilitiesMap["prompts"] as? Map<*, *>
        
        return McpCapabilities(
            tools = toolsMap?.let {
                ToolsCapability(
                    listChanged = it["listChanged"] as? Boolean ?: false
                )
            },
            resources = resourcesMap?.let {
                ResourcesCapability(
                    subscribe = it["subscribe"] as? Boolean ?: false,
                    listChanged = it["listChanged"] as? Boolean ?: false
                )
            },
            prompts = promptsMap?.let {
                PromptsCapability(
                    listChanged = it["listChanged"] as? Boolean ?: false
                )
            }
        )
    }
    
    /**
     * Parse tools array from response
     */
    private fun parseTools(toolsArray: List<*>): List<McpTool> {
        return toolsArray.mapNotNull { item ->
            when (item) {
                is JSONObject -> {
                    McpTool(
                        name = item.optString("name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                        title = item.optString("title").takeIf { it.isNotEmpty() },
                        description = item.optString("description", ""),
                        inputSchema = if (item.has("inputSchema")) item.getJSONObject("inputSchema") else JSONObject(),
                        outputSchema = if (item.has("outputSchema")) item.getJSONObject("outputSchema") else null,
                        _meta = if (item.has("_meta")) {
                            val metaObj = item.getJSONObject("_meta")
                            metaObj.keys().asSequence().associateWith { metaObj.get(it) }
                        } else null,
                        payment = if (item.has("payment")) {
                            parsePaymentInfo(item.getJSONObject("payment"))
                        } else null
                    )
                }
                is Map<*, *> -> {
                    McpTool(
                        name = item["name"] as? String ?: return@mapNotNull null,
                        title = item["title"] as? String,
                        description = item["description"] as? String ?: "",
                        inputSchema = JSONObject(item["inputSchema"] as? Map<*, *> ?: emptyMap<String, Any>()),
                        outputSchema = (item["outputSchema"] as? Map<*, *>)?.let { JSONObject(it) },
                        _meta = item["_meta"] as? Map<String, Any>,
                        payment = (item["payment"] as? Map<*, *>)?.let { parsePaymentInfoFromMap(it) }
                    )
                }
                else -> null
            }
        }
    }
    
    /**
     * Parse payment info from JSONObject
     */
    private fun parsePaymentInfo(paymentObj: JSONObject): com.myagentos.app.domain.model.PaymentInfo {
        return com.myagentos.app.domain.model.PaymentInfo(
            required = paymentObj.optBoolean("required", false),
            price = paymentObj.optDouble("price", 0.0),
            currency = paymentObj.optString("currency", "USDC"),
            description = paymentObj.optString("description", ""),
            recipient = paymentObj.optString("recipient", null),
            expiresAt = paymentObj.optString("expiresAt", null),
            maxSlippage = if (paymentObj.has("maxSlippage")) paymentObj.optDouble("maxSlippage") else null,
            pricing = paymentObj.optString("pricing", null)
        )
    }
    
    /**
     * Parse payment info from Map
     */
    private fun parsePaymentInfoFromMap(paymentMap: Map<*, *>): com.myagentos.app.domain.model.PaymentInfo {
        return com.myagentos.app.domain.model.PaymentInfo(
            required = paymentMap["required"] as? Boolean ?: false,
            price = (paymentMap["price"] as? Number)?.toDouble() ?: 0.0,
            currency = paymentMap["currency"] as? String ?: "USDC",
            description = paymentMap["description"] as? String ?: "",
            recipient = paymentMap["recipient"] as? String,
            expiresAt = paymentMap["expiresAt"] as? String,
            maxSlippage = (paymentMap["maxSlippage"] as? Number)?.toDouble(),
            pricing = paymentMap["pricing"] as? String
        )
    }
    
    /**
     * Parse tool result from response
     */
    private fun parseToolResult(resultMap: Map<*, *>): McpToolResult {
        // Handle content array - can be JSONArray or List
        val contentArray = when (val content = resultMap["content"]) {
            is JSONArray -> (0 until content.length()).map { content.get(it) }
            is List<*> -> content
            else -> emptyList()
        }
        
        // Handle structured content - can be JSONObject or Map
        val structuredContent = when (val sc = resultMap["structuredContent"]) {
            is JSONObject -> sc
            is Map<*, *> -> JSONObject(sc)
            else -> null
        }
        
        val isError = resultMap["isError"] as? Boolean ?: false
        
        // Handle _meta - can be JSONObject or Map (CRITICAL FOR WIDGET TEMPLATES!)
        val meta = when (val m = resultMap["_meta"]) {
            is JSONObject -> {
                // Convert JSONObject to Map<String, Any>
                val map = mutableMapOf<String, Any>()
                m.keys().forEach { key ->
                    m.get(key)?.let { value ->
                        map[key] = value
                    }
                }
                android.util.Log.e(TAG, ">>> PARSED _meta from JSONObject: $map")
                map
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val typedMap = m as? Map<String, Any> ?: emptyMap()
                android.util.Log.e(TAG, ">>> PARSED _meta from Map: $typedMap")
                typedMap
            }
            else -> {
                android.util.Log.e(TAG, ">>> ERROR: _meta is null or unknown type: ${m?.javaClass?.name}")
                null
            }
        }
        
        val content = contentArray.mapNotNull { item ->
            when (item) {
                is JSONObject -> parseContent(item)
                is Map<*, *> -> parseContent(item)
                else -> null
            }
        }
        
        return McpToolResult(
            content = content,
            structuredContent = structuredContent,
            isError = isError,
            _meta = meta
        )
    }
    
    /**
     * Parse content item from response - JSONObject overload
     */
    private fun parseContent(contentObj: JSONObject): McpContent? {
        val type = contentObj.optString("type").takeIf { it.isNotEmpty() } ?: return null
        
        return when (type) {
            "text" -> McpContent.Text(
                text = contentObj.optString("text", ""),
                annotations = if (contentObj.has("annotations")) {
                    parseAnnotations(contentObj.getJSONObject("annotations"))
                } else null
            )
            "image" -> McpContent.Image(
                data = contentObj.optString("data", ""),
                mimeType = contentObj.optString("mimeType", "image/png"),
                annotations = if (contentObj.has("annotations")) {
                    parseAnnotations(contentObj.getJSONObject("annotations"))
                } else null
            )
            "audio" -> McpContent.Audio(
                data = contentObj.optString("data", ""),
                mimeType = contentObj.optString("mimeType", "audio/wav"),
                annotations = if (contentObj.has("annotations")) {
                    parseAnnotations(contentObj.getJSONObject("annotations"))
                } else null
            )
            "resource_link" -> McpContent.ResourceLink(
                uri = contentObj.optString("uri", ""),
                name = contentObj.optString("name").takeIf { it.isNotEmpty() },
                description = contentObj.optString("description").takeIf { it.isNotEmpty() },
                mimeType = contentObj.optString("mimeType").takeIf { it.isNotEmpty() },
                annotations = if (contentObj.has("annotations")) {
                    parseAnnotations(contentObj.getJSONObject("annotations"))
                } else null
            )
            else -> null
        }
    }
    
    /**
     * Parse content item from response - Map overload
     */
    private fun parseContent(contentMap: Map<*, *>?): McpContent? {
        if (contentMap == null) return null
        
        val type = contentMap["type"] as? String ?: return null
        
        return when (type) {
            "text" -> McpContent.Text(
                text = contentMap["text"] as? String ?: "",
                annotations = parseAnnotations(contentMap["annotations"] as? Map<*, *>)
            )
            "image" -> McpContent.Image(
                data = contentMap["data"] as? String ?: "",
                mimeType = contentMap["mimeType"] as? String ?: "image/png",
                annotations = parseAnnotations(contentMap["annotations"] as? Map<*, *>)
            )
            "audio" -> McpContent.Audio(
                data = contentMap["data"] as? String ?: "",
                mimeType = contentMap["mimeType"] as? String ?: "audio/wav",
                annotations = parseAnnotations(contentMap["annotations"] as? Map<*, *>)
            )
            "resource_link" -> McpContent.ResourceLink(
                uri = contentMap["uri"] as? String ?: "",
                name = contentMap["name"] as? String,
                description = contentMap["description"] as? String,
                mimeType = contentMap["mimeType"] as? String,
                annotations = parseAnnotations(contentMap["annotations"] as? Map<*, *>)
            )
            else -> null
        }
    }
    
    /**
     * Parse annotations - JSONObject overload
     */
    private fun parseAnnotations(annotationsObj: JSONObject): McpAnnotations? {
        if (annotationsObj.length() == 0) return null
        
        val audience = if (annotationsObj.has("audience")) {
            val arr = annotationsObj.getJSONArray("audience")
            (0 until arr.length()).map { arr.getString(it) }
        } else null
        
        val priority = if (annotationsObj.has("priority")) {
            annotationsObj.getDouble("priority").toFloat()
        } else null
        
        val lastModified = annotationsObj.optString("lastModified").takeIf { it.isNotEmpty() }
        
        return McpAnnotations(
            audience = audience,
            priority = priority,
            lastModified = lastModified
        )
    }
    
    /**
     * Parse resource from response
     */
    private fun parseResource(resource: Any?): McpResource? {
        return when (resource) {
            is JSONObject -> parseResourceFromJson(resource)
            is Map<*, *> -> parseResourceFromMap(resource)
            else -> null
        }
    }
    
    private fun parseResourceFromJson(resourceObj: JSONObject): McpResource? {
        return McpResource(
            uri = resourceObj.optString("uri").takeIf { it.isNotEmpty() } ?: return null,
            mimeType = resourceObj.optString("mimeType", "text/plain"),
            text = resourceObj.optString("text").takeIf { it.isNotEmpty() },
            blob = resourceObj.optString("blob").takeIf { it.isNotEmpty() },
            name = resourceObj.optString("name").takeIf { it.isNotEmpty() },
            description = resourceObj.optString("description").takeIf { it.isNotEmpty() },
            annotations = if (resourceObj.has("annotations")) {
                parseAnnotations(resourceObj.getJSONObject("annotations"))
            } else null
        )
    }
    
    private fun parseResourceFromMap(resourceMap: Map<*, *>?): McpResource? {
        if (resourceMap == null) return null
        
        return McpResource(
            uri = resourceMap["uri"] as? String ?: return null,
            mimeType = resourceMap["mimeType"] as? String ?: "text/plain",
            text = resourceMap["text"] as? String,
            blob = resourceMap["blob"] as? String,
            name = resourceMap["name"] as? String,
            description = resourceMap["description"] as? String,
            annotations = parseAnnotations(resourceMap["annotations"] as? Map<*, *>)
        )
    }
    
    /**
     * Parse annotations from response
     */
    private fun parseAnnotations(annotationsMap: Map<*, *>?): McpAnnotations? {
        if (annotationsMap == null) return null
        
        @Suppress("UNCHECKED_CAST")
        val audience = annotationsMap["audience"] as? List<String>
        val priority = (annotationsMap["priority"] as? Number)?.toFloat()
        val lastModified = annotationsMap["lastModified"] as? String
        
        return McpAnnotations(
            audience = audience,
            priority = priority,
            lastModified = lastModified
        )
    }
}

