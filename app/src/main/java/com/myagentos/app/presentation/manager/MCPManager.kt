package com.myagentos.app.presentation.manager
import com.myagentos.app.data.model.ModelType
import com.myagentos.app.data.model.ConnectionStatus
import com.myagentos.app.data.model.McpTool
import com.myagentos.app.data.model.McpApp
import com.myagentos.app.data.model.McpContent

import android.content.Context
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.myagentos.app.*
import com.myagentos.app.presentation.adapter.SimpleChatAdapter
import com.myagentos.app.data.source.AppDirectory
import com.myagentos.app.domain.repository.McpRepository
import com.myagentos.app.domain.repository.AIRepository
import com.myagentos.app.data.payment.X402PaymentHandler
import com.myagentos.app.presentation.dialog.PaymentConfirmationDialog
import com.myagentos.app.data.storage.WalletStorage
import com.myagentos.app.domain.model.PaymentRequirements
import com.myagentos.app.domain.model.PaymentPrice
import com.myagentos.app.domain.model.PaymentAsset
import com.myagentos.app.domain.model.PaymentInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCPManager - Manages Model Context Protocol (MCP) integration
 * 
 * Responsibilities:
 * - MCP app connection/disconnection
 * - Tool invocation and execution
 * - Parameter collection (conversational)
 * - Tool result handling
 * - MCP context management
 * - Connection state tracking
 * 
 * Extracted from MainActivity to reduce complexity (Phase 4 - Step 1)
 * 
 * This is the BIGGEST extraction (~900 lines!) to significantly reduce MainActivity
 */
class MCPManager(
    private val context: Context,
    private val mcpRepository: McpRepository,
    private val aiRepository: AIRepository,
    private val activityResultSender: com.solana.mobilewalletadapter.clientlib.ActivityResultSender
) {
    
    // State
    private val connectedApps = mutableMapOf<String, McpApp>()
    private var currentMcpAppContext: String? = null
    private var activeParameterCollection: ParameterCollectionState? = null
    private var lastInvokedTool: InvokedToolState? = null
    
    // x402 Payment Handler with real Solana wallet integration
    private val paymentHandler = X402PaymentHandler(context, activityResultSender)
    
    // Callbacks
    private var onToolInvoked: ((String, McpTool) -> Unit)? = null
    private var onConnectionStatusChanged: ((String, ConnectionStatus) -> Unit)? = null
    private var onToolResultReceived: ((String, McpTool, String, com.myagentos.app.data.model.McpToolResult?, Map<String, Any>) -> Unit)? = null
    private var onParameterCollectionStarted: ((String, McpTool) -> Unit)? = null
    private var onNeedToShowToolPills: ((String) -> Unit)? = null
    private var chatAdapter: SimpleChatAdapter? = null
    private var chatRecyclerView: RecyclerView? = null
    
    /**
     * Connect to an MCP app
     */
    fun connectToApp(appId: String, onComplete: (Boolean, String?) -> Unit) {
        android.util.Log.d("MCPManager", "Connecting to MCP app: $appId")
        
        // Find the app in the directory
        val app = AppDirectory.getFeaturedApps().find { it.id == appId }
        if (app == null) {
            android.util.Log.e("MCPManager", "App not found: $appId")
            onComplete(false, "App not found")
            return
        }
        
        Toast.makeText(context, "Connecting to ${app.name}...", Toast.LENGTH_SHORT).show()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Step 1: Initialize connection with MCP server
                val capabilities = withContext(Dispatchers.IO) {
                    mcpRepository.initialize(app.serverUrl)
                }
                
                if (capabilities == null) {
                    throw Exception("Failed to initialize connection")
                }
                
                android.util.Log.d("MCPManager", "Initialized connection to ${app.name}")
                
                // Step 2: Fetch tools from MCP server
                val tools = withContext(Dispatchers.IO) {
                    mcpRepository.listTools(app.serverUrl)
                }
                
                android.util.Log.d("MCPManager", "Fetched ${tools.size} tools from ${app.name}")
                
                // Update connected apps
                val connectedApp = app.copy(
                    tools = tools,
                    connectionStatus = ConnectionStatus.CONNECTED
                )
                connectedApps[appId] = connectedApp
                
                // Set this app as the current MCP context
                currentMcpAppContext = appId
                android.util.Log.d("MCPManager", "Set MCP context to: $appId")
                
                // Notify listeners
                onConnectionStatusChanged?.invoke(appId, ConnectionStatus.CONNECTED)
                
                Toast.makeText(context, "${app.name} connected with ${tools.size} tools!", Toast.LENGTH_SHORT).show()
                onComplete(true, null)
                
            } catch (e: Exception) {
                android.util.Log.e("MCPManager", "Error connecting to ${app.name}: ${e.message}", e)
                Toast.makeText(context, "Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
                onComplete(false, e.message)
            }
        }
    }
    
    /**
     * Disconnect from an MCP app
     */
    fun disconnectFromApp(appId: String) {
        android.util.Log.d("MCPManager", "Disconnecting from MCP app: $appId")
        
        val app = connectedApps[appId]
        if (app == null) {
            android.util.Log.w("MCPManager", "App not connected: $appId")
            return
        }
        
        // Remove from connected apps
        connectedApps.remove(appId)
        
        // Clear context if this was the current context
        if (currentMcpAppContext == appId) {
            currentMcpAppContext = null
            activeParameterCollection = null
            lastInvokedTool = null
        }
        
        // Notify listeners
        onConnectionStatusChanged?.invoke(appId, ConnectionStatus.DISCONNECTED)
        
        Toast.makeText(context, "${app.name} disconnected", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Invoke an MCP tool
     */
    fun invokeTool(
        appId: String, 
        toolName: String, 
        providedParameters: Map<String, Any>? = null,
        onStart: () -> Unit = {},
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        android.util.Log.e("MCPManager", ">>> INVOKING TOOL: $toolName from app: $appId")
        
        // Get the connected app
        val app = connectedApps[appId]
        if (app == null) {
            android.util.Log.e("MCPManager", ">>> ERROR: App not connected: $appId")
            Toast.makeText(context, "App not connected", Toast.LENGTH_SHORT).show()
            onComplete(false, "App not connected")
            return
        }
        
        // Find the tool
        val tool = app.tools.find { it.name == toolName }
        if (tool == null) {
            android.util.Log.e("MCPManager", ">>> ERROR: Tool not found: $toolName")
            Toast.makeText(context, "Tool not found", Toast.LENGTH_SHORT).show()
            onComplete(false, "Tool not found")
            return
        }
        
        android.util.Log.e("MCPManager", ">>> Tool found: ${tool.name}, proceeding with invocation")
        android.util.Log.e("MCPManager", ">>> Tool details: title=${tool.title}, description=${tool.description}")
        
        // Set MCP app context
        currentMcpAppContext = appId
        android.util.Log.e("MCPManager", ">>> Set MCP context to: $appId")
        
        // Notify that tool pills should be shown
        android.util.Log.e("MCPManager", ">>> Notifying tool pills callback")
        onNeedToShowToolPills?.invoke(appId)
        
        // Notify tool invoked
        android.util.Log.e("MCPManager", ">>> Notifying tool invoked callback")
        onToolInvoked?.invoke(appId, tool)
        onStart()
        
        // Check if tool has required parameters
        val requiredParams = getRequiredParameters(tool)
        android.util.Log.e("MCPManager", ">>> Tool has ${requiredParams.size} required parameters: $requiredParams")
        
        // If tool requires payment, skip parameter collection and invoke directly
        // The payment flow will handle the first call, and parameters can be collected after
        if (tool.payment?.required == true) {
            android.util.Log.e("MCPManager", ">>> Tool requires payment, skipping parameter collection")
            // Build default parameters for required params
            val defaultParams = buildDefaultParameters(tool)
            android.util.Log.e("MCPManager", ">>> Using default parameters: $defaultParams")
            executeTool(appId, tool, defaultParams, onComplete)
            return
        }
        
        // If we have required parameters and no provided parameters, start collection
        if (requiredParams.isNotEmpty() && providedParameters == null) {
            android.util.Log.e("MCPManager", ">>> Starting parameter collection")
            startParameterCollection(appId, tool, requiredParams, emptyMap())
            onComplete(true, "Parameter collection started")
            return
        }
        
        // Otherwise, invoke immediately
        android.util.Log.e("MCPManager", ">>> Invoking tool immediately, calling executeTool")
        executeTool(appId, tool, providedParameters, onComplete)
    }
    
    /**
     * Execute tool invocation with parameters
     */
    private fun executeTool(
        appId: String,
        tool: McpTool,
        providedParameters: Map<String, Any>?,
        onComplete: (Boolean, String?) -> Unit
    ) {
        android.util.Log.e("MCPManager", ">>> executeTool called for: ${tool.name}")
        
        val app = connectedApps[appId] ?: return
        
        // Build arguments
        val argumentsMap = providedParameters?.toMutableMap() ?: run {
            val arguments = buildToolArguments(tool)
            val map = mutableMapOf<String, Any>()
            arguments.keys().forEach { key ->
                map[key] = arguments.get(key)
            }
            map
        }
        
        android.util.Log.d("MCPManager", "Final arguments: $argumentsMap")
        
        // Execute tool asynchronously (try first call - may require payment)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.e("MCPManager", ">>> Calling mcpRepository.callTool...")
                android.util.Log.e("MCPManager", "  - serverUrl: ${app.serverUrl}")
                android.util.Log.e("MCPManager", "  - toolName: ${tool.name}")
                android.util.Log.e("MCPManager", "  - arguments: $argumentsMap")
                
                val result = mcpRepository.callTool(app.serverUrl, tool.name, argumentsMap)
                
                android.util.Log.e("MCPManager", ">>> mcpRepository.callTool RETURNED!")
                android.util.Log.e("MCPManager", "  - result: $result")
                android.util.Log.e("MCPManager", "  - result class: ${result?.javaClass?.name}")
                android.util.Log.e("MCPManager", "  - result.content: ${result?.content}")
                android.util.Log.e("MCPManager", "  - result.content size: ${result?.content?.size}")
                
                if (result?.content != null) {
                    result.content.forEachIndexed { index, content ->
                        android.util.Log.e("MCPManager", ">>> Content[$index]:")
                        android.util.Log.e("MCPManager", "  - type: ${content?.javaClass?.simpleName}")
                        android.util.Log.e("MCPManager", "  - content: $content")
                        if (content is McpContent.Text) {
                            android.util.Log.e("MCPManager", "  - text length: ${content.text.length}")
                            android.util.Log.e("MCPManager", "  - text preview: ${content.text.take(500)}")
                        }
                    }
                }
                
                val resultText = result?.content?.firstOrNull()?.let {
                    when (it) {
                        is McpContent.Text -> it.text
                        else -> "Tool executed successfully"
                    }
                } ?: "Tool executed (no result)"
                
                android.util.Log.e("MCPManager", ">>> Extracted resultText: ${resultText.take(200)}")
                android.util.Log.e("MCPManager", ">>> Result isError: ${result?.isError}")
                android.util.Log.e("MCPManager", ">>> Result errorData: ${result?.errorData}")
                
                // Check if result indicates payment required (even though it's a "successful" call)
                if (result?.isError == true && isPaymentRequiredError(resultText)) {
                    android.util.Log.e("MCPManager", ">>> Payment required detected in result!")
                    
                    // Cancel any active parameter collection since we're handling payment
                    if (activeParameterCollection != null) {
                        android.util.Log.e("MCPManager", ">>> Cancelling active parameter collection for payment")
                        activeParameterCollection = null
                    }
                    
                    // Try to extract payment requirements from error data first
                    var paymentRequirements = extractPaymentRequirementsFromErrorData(result.errorData)
                    android.util.Log.e("MCPManager", ">>> Payment requirements from errorData: $paymentRequirements")
                    
                    // If errorData is null but tool has payment metadata, use that as fallback
                    if (paymentRequirements == null && tool.payment != null) {
                        android.util.Log.e("MCPManager", ">>> Using tool payment metadata as fallback")
                        paymentRequirements = convertPaymentInfoToRequirements(tool.payment!!)
                        android.util.Log.e("MCPManager", ">>> Fallback payment requirements: $paymentRequirements")
                    }
                    
                    if (paymentRequirements != null) {
                        android.util.Log.e("MCPManager", ">>> Connecting to wallet directly (no confirmation dialog)...")
                        // Connect to wallet immediately and retry with payment
                        CoroutineScope(Dispatchers.Main).launch {
                            connectWalletAndRetry(appId, app, tool, argumentsMap, paymentRequirements, onComplete)
                        }
                        return@launch
                    } else {
                        android.util.Log.e("MCPManager", ">>> Failed to extract payment requirements from result!")
                        android.util.Log.e("MCPManager", ">>> ErrorData was: ${result.errorData}")
                        android.util.Log.e("MCPManager", ">>> ErrorData type: ${result.errorData?.javaClass?.name}")
                        android.util.Log.e("MCPManager", ">>> Tool payment metadata: ${tool.payment}")
                        
                        // Fall through to show error message
                    }
                }
                
                // Store last invoked tool
                lastInvokedTool = InvokedToolState(
                    appId = appId,
                    tool = tool,
                    parameters = argumentsMap,
                    result = resultText
                )
                
                withContext(Dispatchers.Main) {
                    android.util.Log.e("MCPManager", ">>> Calling onToolResultReceived callback...")
                    android.util.Log.e("MCPManager", "  - Passing tool arguments: $argumentsMap")
                    // Notify listeners (pass full result + arguments for WebView data injection)
                    onToolResultReceived?.invoke(appId, tool, resultText, result, argumentsMap)
                    onComplete(true, resultText)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MCPManager", "Tool invocation failed: ${e.message}", e)
                
                // Check if this is a payment required error (-32001)
                android.util.Log.e("MCPManager", ">>> Checking if payment required error...")
                android.util.Log.e("MCPManager", ">>> Error message: '${e.message}'")
                android.util.Log.e("MCPManager", ">>> Error class: ${e.javaClass.name}")
                
                if (isPaymentRequiredError(e)) {
                    android.util.Log.e("MCPManager", ">>> Payment required for tool: ${tool.name}")
                    
                    // Cancel any active parameter collection since we're handling payment
                    if (activeParameterCollection != null) {
                        android.util.Log.e("MCPManager", ">>> Cancelling active parameter collection for payment")
                        activeParameterCollection = null
                    }
                    
                    // Extract payment requirements from error
                    val paymentRequirements = extractPaymentRequirements(e)
                    android.util.Log.e("MCPManager", ">>> Payment requirements extracted: $paymentRequirements")
                    
                    if (paymentRequirements != null) {
                        android.util.Log.e("MCPManager", ">>> Showing payment confirmation dialog...")
                        // Show payment confirmation and retry with payment
                        CoroutineScope(Dispatchers.Main).launch {
                            showPaymentConfirmationAndRetry(appId, app, tool, argumentsMap, paymentRequirements, onComplete)
                        }
                        return@launch
                    } else {
                        android.util.Log.e("MCPManager", ">>> Failed to extract payment requirements!")
                    }
                } else {
                    android.util.Log.e("MCPManager", ">>> Not a payment required error")
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Tool failed: ${e.message}", Toast.LENGTH_LONG).show()
                    onComplete(false, e.message)
                }
            }
        }
    }
    
    /**
     * Start parameter collection for a tool
     */
    private fun startParameterCollection(
        appId: String,
        tool: McpTool,
        requiredParams: List<String>,
        inferredParams: Map<String, Any>
    ) {
        android.util.Log.d("MCPManager", "Starting parameter collection for tool: ${tool.name}")
        
        activeParameterCollection = ParameterCollectionState(
            appId = appId,
            tool = tool,
            requiredParams = requiredParams,
            collectedParams = inferredParams.toMutableMap(),
            conversationHistory = mutableListOf()
        )
        
        // Notify callback
        onParameterCollectionStarted?.invoke(appId, tool)
        
        android.util.Log.d("MCPManager", "Parameter collection started. Required: $requiredParams, Inferred: $inferredParams")
    }
    
    /**
     * Continue parameter collection with user message (uses Grok to extract values)
     */
    suspend fun continueParameterCollection(userMessage: String): String {
        val state = activeParameterCollection ?: return "Error: No active parameter collection"
        
        android.util.Log.e("MCPManager", ">>> Continuing parameter collection with: $userMessage")
        
        // Use Grok to extract parameter values from user's natural language response
        val remainingParams = state.requiredParams.filter { !state.collectedParams.containsKey(it) }
        android.util.Log.e("MCPManager", "Remaining params: $remainingParams")
        
        if (remainingParams.isNotEmpty()) {
            // Ask Grok to extract parameter values
            val extractionPrompt = """
                The user is providing parameter values for the "${state.tool.title ?: state.tool.name}" tool.
                
                Required parameters (not yet collected): ${remainingParams.joinToString(", ")}
                Already collected: ${state.collectedParams.keys.joinToString(", ")}
                
                User's response: "$userMessage"
                
                Extract parameter values from the user's message. If the user provided values for the remaining parameters, respond with:
                TOOL_PARAMS: ${org.json.JSONObject().apply {
                    remainingParams.forEach { put(it, "<extracted_value>") }
                }.toString()}
                
                If the user's response doesn't contain the needed information, ask for the missing parameters in a natural way.
            """.trimIndent()
            
            android.util.Log.e("MCPManager", "Sending extraction prompt to AI...")
            val aiResponse = withContext(Dispatchers.IO) {
                aiRepository.generateResponse(extractionPrompt, com.myagentos.app.data.model.ModelType.EXTERNAL_GROK)
            }
            
            android.util.Log.e("MCPManager", "AI response: $aiResponse")
            
            // Check if AI extracted parameters (look for TOOL_PARAMS: format)
            if (aiResponse.contains("TOOL_PARAMS:")) {
                try {
                    val jsonStart = aiResponse.indexOf("{")
                    val jsonEnd = aiResponse.lastIndexOf("}") + 1
                    val jsonStr = aiResponse.substring(jsonStart, jsonEnd)
                    val params = org.json.JSONObject(jsonStr)
                    
                    android.util.Log.e("MCPManager", "Extracted params: $params")
                    
                    // Add extracted parameters to collected params
                    params.keys().forEach { key ->
                        state.collectedParams[key] = params.getString(key)
                        android.util.Log.e("MCPManager", "Collected parameter '$key' = '${params.getString(key)}'")
                    }
                    
                    // Check if all parameters collected
                    if (areAllParametersCollected(state)) {
                        android.util.Log.e("MCPManager", ">>> All parameters collected! Executing tool...")
                        
                        withContext(Dispatchers.Main) {
                            executeTool(state.appId, state.tool, state.collectedParams) { success, result ->
                                // Clear parameter collection
                                activeParameterCollection = null
                                // Refresh pills
                                currentMcpAppContext?.let { onNeedToShowToolPills?.invoke(it) }
                            }
                        }
                        
                        return@continueParameterCollection "" // Don't show any message, tool result will show
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MCPManager", "Error parsing extracted params: ${e.message}", e)
                }
            }
            
            // Return AI's conversational response (asking for more params or clarification)
            return aiResponse.replace("TOOL_PARAMS:.*".toRegex(), "").trim()
        }
        
        return "Error: No remaining parameters"
    }
    
    /**
     * Check if all required parameters are collected
     */
    private fun areAllParametersCollected(state: ParameterCollectionState): Boolean {
        return state.requiredParams.all { state.collectedParams.containsKey(it) }
    }
    
    /**
     * Get active parameter collection state (for UI)
     */
    fun getActiveCollectionState(): ParameterCollectionState? {
        return activeParameterCollection
    }
    
    /**
     * Get required parameters for a tool
     */
    private fun getRequiredParameters(tool: McpTool): List<String> {
        return try {
            val inputSchema = tool.inputSchema
            if (inputSchema.has("required") && inputSchema.get("required") is JSONArray) {
                val requiredArray = inputSchema.getJSONArray("required")
                (0 until requiredArray.length()).map { requiredArray.getString(it) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("MCPManager", "Error getting required parameters", e)
            emptyList()
        }
    }
    
    /**
     * Build default parameters for a tool (for paid tools to skip parameter collection)
     */
    private fun buildDefaultParameters(tool: McpTool): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        
        try {
            val inputSchema = tool.inputSchema
            if (inputSchema.has("properties")) {
                val properties = inputSchema.getJSONObject("properties")
                val requiredParams = getRequiredParameters(tool)
                
                // Only build defaults for required parameters (exclude _payment)
                requiredParams.forEach { paramName ->
                    if (paramName != "_payment" && properties.has(paramName)) {
                        val propertySchema = properties.getJSONObject(paramName)
                        val description = propertySchema.optString("description", "")
                        val propertyType = propertySchema.optString("type", "string")
                        
                        // Generate reasonable default based on parameter name and description
                        val defaultValue = when {
                            paramName.contains("id", ignoreCase = true) -> "default_${System.currentTimeMillis()}"
                            paramName.contains("name", ignoreCase = true) -> "Default Name"
                            paramName.contains("place", ignoreCase = true) -> "Joe's Pizza"
                            else -> when (propertyType) {
                                "string" -> propertySchema.optString("default", "default_value")
                                "number" -> propertySchema.optDouble("default", 0.0)
                                "integer" -> propertySchema.optInt("default", 0)
                                "boolean" -> propertySchema.optBoolean("default", false)
                                else -> "default_value"
                            }
                        }
                        
                        params[paramName] = defaultValue
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MCPManager", "Error building default parameters", e)
        }
        
        return params
    }
    
    /**
     * Build tool arguments from schema (defaults)
     */
    private fun buildToolArguments(tool: McpTool): JSONObject {
        val arguments = JSONObject()
        
        try {
            val inputSchema = tool.inputSchema
            if (inputSchema.has("properties")) {
                val properties = inputSchema.getJSONObject("properties")
                val propertyNames = properties.keys()
                
                while (propertyNames.hasNext()) {
                    val propertyName = propertyNames.next()
                    val propertySchema = properties.getJSONObject(propertyName)
                    val propertyType = propertySchema.optString("type", "string")
                    
                    // Generate default value based on type
                    val defaultValue = when (propertyType) {
                        "string" -> propertySchema.optString("default", "")
                        "number" -> propertySchema.optDouble("default", 0.0)
                        "integer" -> propertySchema.optInt("default", 0)
                        "boolean" -> propertySchema.optBoolean("default", false)
                        else -> ""
                    }
                    
                    arguments.put(propertyName, defaultValue)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MCPManager", "Error building arguments", e)
        }
        
        return arguments
    }
    
    /**
     * Handle message in MCP context
     */
    suspend fun handleContextMessage(userMessage: String, buildConversationHistory: () -> List<Pair<String, String>>): String {
        android.util.Log.d("MCPManager", "Handling message in MCP context: $userMessage")
        
        val app = currentMcpAppContext?.let { connectedApps[it] }
        if (app == null) {
            android.util.Log.e("MCPManager", "MCP context app not found")
            return "Error: App context lost"
        }
        
        // Use AI to intelligently route the message
        val routingDecision = analyzeMessageWithAI(userMessage, app, buildConversationHistory())
        
        return when (routingDecision.action) {
            "invoke_tool" -> {
                android.util.Log.d("MCPManager", "AI decision: Invoke tool '${routingDecision.toolName}'")
                if (routingDecision.toolName != null) {
                    withContext(Dispatchers.Main) {
                        invokeTool(app.id, routingDecision.toolName) { _, _ -> }
                    }
                    ""
                } else {
                    "I couldn't determine which tool to invoke. Available tools: ${app.tools.joinToString(", ") { it.title ?: it.name }}"
                }
            }
            "modify_parameters" -> {
                android.util.Log.d("MCPManager", "AI decision: Modify parameters")
                if (lastInvokedTool != null && routingDecision.parameters != null) {
                    handleParameterModification(userMessage, lastInvokedTool!!)
                } else {
                    generateContextualResponse(userMessage, app, routingDecision.response, buildConversationHistory())
                }
            }
            "respond" -> {
                android.util.Log.d("MCPManager", "AI decision: Generate response")
                generateContextualResponse(userMessage, app, routingDecision.response, buildConversationHistory())
            }
            else -> {
                generateContextualResponse(userMessage, app, null, buildConversationHistory())
            }
        }
    }
    
    /**
     * Analyze user message with AI to determine action
     */
    private suspend fun analyzeMessageWithAI(
        userMessage: String,
        app: McpApp,
        conversationHistory: List<Pair<String, String>>
    ): RoutingDecision {
        // Build context for AI
        val toolsList = app.tools.joinToString("\n") { tool ->
            val toolTitle = tool.title ?: tool.name
            val description = tool.description ?: "No description"
            "- $toolTitle: $description"
        }
        
        val systemPrompt = """
            You are helping route user messages in an MCP app context (${app.name}).
            
            Available tools:
            $toolsList
            
            Your job is to analyze the user's message and decide:
            1. If they want to invoke a specific tool
            2. If they want to modify parameters of the last invoked tool
            3. If they just want to chat about the context
            
            Respond with JSON:
            {
                "action": "invoke_tool" | "modify_parameters" | "respond",
                "toolName": "tool_name" (if action is invoke_tool),
                "parameters": {} (if action is modify_parameters),
                "response": "your response" (if action is respond)
            }
        """.trimIndent()
        
        val prompt = "$systemPrompt\n\nUser message: $userMessage"
        
        return try {
            val response = aiRepository.generateResponse(prompt, ModelType.EXTERNAL_GROK)
            parseRoutingDecision(response)
        } catch (e: Exception) {
            android.util.Log.e("MCPManager", "Error analyzing message", e)
            RoutingDecision("respond", null, null, null)
        }
    }
    
    /**
     * Parse AI routing decision
     */
    private fun parseRoutingDecision(response: String): RoutingDecision {
        return try {
            val json = JSONObject(response)
            RoutingDecision(
                action = json.optString("action", "respond"),
                toolName = json.optString("toolName", null),
                parameters = if (json.has("parameters")) json.getJSONObject("parameters") else null,
                response = json.optString("response", null)
            )
        } catch (e: Exception) {
            android.util.Log.e("MCPManager", "Error parsing routing decision", e)
            RoutingDecision("respond", null, null, response)
        }
    }
    
    /**
     * Generate contextual response
     */
    private suspend fun generateContextualResponse(
        userMessage: String,
        app: McpApp,
        suggestedResponse: String?,
        conversationHistory: List<Pair<String, String>>
    ): String {
        return if (suggestedResponse != null) {
            suggestedResponse
        } else {
            try {
                val context = buildMcpContext(app)
                val prompt = "$context\n\nUser: $userMessage"
                aiRepository.generateResponseWithHistory(userMessage, ModelType.EXTERNAL_GROK, conversationHistory)
            } catch (e: Exception) {
                "Sorry, I encountered an error: ${e.message}"
            }
        }
    }
    
    /**
     * Build MCP context for AI
     */
    private fun buildMcpContext(app: McpApp): String {
        val toolsList = app.tools.joinToString("\n") { tool ->
            "${tool.title ?: tool.name}: ${tool.description ?: "No description"}"
        }
        
        return """
            You are in the context of ${app.name}.
            ${app.description}
            
            Available tools:
            $toolsList
            
            ${if (lastInvokedTool != null) "Last invoked tool: ${lastInvokedTool!!.tool.name}" else ""}
        """.trimIndent()
    }
    
    /**
     * Handle parameter modification
     */
    private suspend fun handleParameterModification(userMessage: String, invokedTool: InvokedToolState): String {
        android.util.Log.d("MCPManager", "Handling parameter modification")
        // For now, re-invoke with same parameters (simplified)
        // In full implementation, would parse which parameter to change
        return "Parameter modification not yet implemented"
    }
    
    /**
     * Check if currently in MCP context
     */
    fun isInMcpContext(): Boolean = currentMcpAppContext != null
    
    /**
     * Get current MCP context app ID
     */
    fun getCurrentContext(): String? = currentMcpAppContext
    
    /**
     * Set current MCP context
     */
    fun setContext(appId: String?) {
        currentMcpAppContext = appId
        if (appId == null) {
            activeParameterCollection = null
            lastInvokedTool = null
        }
    }
    
    /**
     * Get connected apps
     */
    fun getConnectedApps(): Map<String, McpApp> = connectedApps.toMap()
    
    /**
     * Check if app is connected
     */
    fun isAppConnected(appId: String): Boolean = connectedApps.containsKey(appId)
    
    /**
     * Get connected app
     */
    fun getConnectedApp(appId: String): McpApp? = connectedApps[appId]
    
    /**
     * Check if in parameter collection mode
     */
    fun isCollectingParameters(): Boolean = activeParameterCollection != null
    
    /**
     * Get active parameter collection state
     */
    fun getParameterCollectionState(): ParameterCollectionState? = activeParameterCollection
    
    /**
     * Clear all MCP state
     */
    fun clearState() {
        currentMcpAppContext = null
        activeParameterCollection = null
        lastInvokedTool = null
    }
    
    /**
     * Set callbacks
     */
    fun setCallbacks(
        onToolInvoked: (String, McpTool) -> Unit,
        onConnectionStatusChanged: (String, ConnectionStatus) -> Unit,
        onToolResultReceived: (String, McpTool, String, com.myagentos.app.data.model.McpToolResult?, Map<String, Any>) -> Unit,
        onParameterCollectionStarted: (String, McpTool) -> Unit,
        onNeedToShowToolPills: (String) -> Unit,
        chatAdapter: SimpleChatAdapter,
        chatRecyclerView: RecyclerView
    ) {
        this.onToolInvoked = onToolInvoked
        this.onConnectionStatusChanged = onConnectionStatusChanged
        this.onToolResultReceived = onToolResultReceived
        this.onParameterCollectionStarted = onParameterCollectionStarted
        this.onNeedToShowToolPills = onNeedToShowToolPills
        this.chatAdapter = chatAdapter
        this.chatRecyclerView = chatRecyclerView
    }
    
    /**
     * Check if error is a payment required error (-32001)
     */
    private fun isPaymentRequiredError(e: Exception): Boolean {
        // Check if error message contains payment required indicators
        val message = e.message ?: ""
        return isPaymentRequiredError(message)
    }
    
    /**
     * Check if message indicates payment required
     */
    private fun isPaymentRequiredError(message: String): Boolean {
        return message.contains("Payment required") || 
               message.contains("-32001") ||
               message.contains("paymentRequirements")
    }
    
    /**
     * Convert PaymentInfo (from tool metadata) to PaymentRequirements
     */
    private fun convertPaymentInfoToRequirements(paymentInfo: PaymentInfo): PaymentRequirements {
        return PaymentRequirements(
            price = PaymentPrice(
                amount = (paymentInfo.price * 1_000_000).toLong().toString(), // Convert to micro-units
                asset = PaymentAsset(
                    address = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU" // USDC devnet
                )
            ),
            recipient = paymentInfo.recipient ?: "BuXm6nD1tWAHwB18AitXdCkYA5Yu3QKoPxJp2Rn7VjGt",
            description = paymentInfo.description,
            currency = paymentInfo.currency,
            network = "solana-devnet"
        )
    }
    
    /**
     * Extract payment requirements from error data
     * 
     * Parses the JSON structure from MCP server error.data:
     * {
     *   "paymentRequirements": {
     *     "price": { "amount": "15000000", "asset": { "address": "..." } },
     *     "recipient": "...",
     *     "description": "...",
     *     "currency": "USDC",
     *     "network": "solana-devnet"
     *   }
     * }
     */
    private fun extractPaymentRequirementsFromErrorData(errorData: Any?): PaymentRequirements? {
        try {
            android.util.Log.d("MCPManager", "Extracting payment requirements from error data: $errorData")
            
            if (errorData == null) {
                android.util.Log.w("MCPManager", "Error data is null")
                return null
            }
            
            // Convert error data to JSONObject if needed
            val dataObj = when (errorData) {
                is JSONObject -> errorData
                is String -> JSONObject(errorData)
                is Map<*, *> -> JSONObject(errorData as Map<String, Any>)
                else -> {
                    android.util.Log.w("MCPManager", "Unknown error data type: ${errorData.javaClass.name}")
                    return null
                }
            }
            
            // Extract paymentRequirements object
            if (!dataObj.has("paymentRequirements")) {
                android.util.Log.w("MCPManager", "No paymentRequirements in error data")
                return null
            }
            
            val paymentReqs = dataObj.getJSONObject("paymentRequirements")
            
            // Extract price object
            val priceObj = paymentReqs.getJSONObject("price")
            val amount = priceObj.getString("amount")
            val assetObj = priceObj.getJSONObject("asset")
            val assetAddress = assetObj.getString("address")
            
            // Extract other fields
            val recipient = paymentReqs.getString("recipient")
            val description = paymentReqs.getString("description")
            val currency = paymentReqs.getString("currency")
            val network = paymentReqs.getString("network")
            
            android.util.Log.d("MCPManager", "Parsed payment requirements: amount=$amount, recipient=$recipient, currency=$currency")
            
            return PaymentRequirements(
                price = PaymentPrice(
                    amount = amount,
                    asset = PaymentAsset(address = assetAddress)
                ),
                recipient = recipient,
                description = description,
                currency = currency,
                network = network
            )
        } catch (ex: Exception) {
            android.util.Log.e("MCPManager", "Failed to extract payment requirements from error data: ${ex.message}", ex)
            return null
        }
    }
    
    /**
     * Extract payment requirements from exception
     * (Fallback for cases where error is thrown rather than returned as McpToolResult)
     */
    private fun extractPaymentRequirements(e: Exception): PaymentRequirements? {
        try {
            val message = e.message ?: ""
            android.util.Log.d("MCPManager", "Extracting payment requirements from exception: $message")
            
            // Try to parse the error message as JSON
            // In some cases, the entire error might be thrown as exception
            if (message.contains("{") && message.contains("paymentRequirements")) {
                val jsonStart = message.indexOf("{")
                val jsonEnd = message.lastIndexOf("}") + 1
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    val jsonStr = message.substring(jsonStart, jsonEnd)
                    return extractPaymentRequirementsFromErrorData(jsonStr)
                }
            }
            
            android.util.Log.w("MCPManager", "Could not extract payment requirements from exception message")
            return null
        } catch (ex: Exception) {
            android.util.Log.e("MCPManager", "Failed to extract payment requirements from exception: ${ex.message}")
            return null
        }
    }
    
    /**
     * Connect to wallet immediately and retry tool call with payment proof (skip dialog)
     */
    private fun connectWalletAndRetry(
        appId: String,
        app: McpApp,
        tool: McpTool,
        argumentsMap: Map<String, Any>,
        paymentRequirements: PaymentRequirements,
        onComplete: (Boolean, String?) -> Unit
    ) {
        // Convert payment requirements to PaymentInfo for wallet
        val paymentInfo = PaymentInfo(
            required = true,
            price = paymentRequirements.price.amount.toDouble() / 1_000_000.0, // Convert micro-units to USDC
            currency = paymentRequirements.currency,
            description = paymentRequirements.description,
            recipient = paymentRequirements.recipient
        )
        
        // Get user's wallet address for payment
        val walletAddress = WalletStorage.getWalletAddress(context) ?: "unknown_wallet"
        
        android.util.Log.e("MCPManager", ">>> Connecting to wallet immediately...")
        
        // Immediately connect to wallet and create payment
        CoroutineScope(Dispatchers.IO).launch {
            try {
                android.util.Log.e("MCPManager", ">>> Creating Solana payment...")
                
                // Create real Solana payment using Mobile Wallet Adapter
                val paymentProof = paymentHandler.createPayment(paymentInfo, walletAddress)
                android.util.Log.e("MCPManager", ">>> Payment proof created: ${paymentProof.transactionSignature}")
                
                // Add payment proof to arguments
                val argumentsWithPayment = argumentsMap.toMutableMap()
                argumentsWithPayment["_payment"] = mapOf(
                    "signature" to paymentProof.transactionSignature,
                    "timestamp" to java.time.Instant.now().toString(),
                    "amount" to paymentRequirements.price.amount,
                    "from" to paymentProof.from
                )
                
                android.util.Log.e("MCPManager", ">>> Retrying tool call with payment proof...")
                
                // Retry tool call with payment proof
                val result = mcpRepository.callTool(app.serverUrl, tool.name, argumentsWithPayment)
                
                val resultText = result?.content?.firstOrNull()?.let {
                    when (it) {
                        is McpContent.Text -> it.text
                        else -> "Tool executed successfully"
                    }
                } ?: "Tool executed (no result)"
                
                // Store last invoked tool
                lastInvokedTool = InvokedToolState(
                    appId = appId,
                    tool = tool,
                    parameters = argumentsWithPayment,
                    result = resultText
                )
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Payment successful! Tool executed.", Toast.LENGTH_SHORT).show()
                    onToolResultReceived?.invoke(appId, tool, resultText, result, argumentsWithPayment)
                    onComplete(true, resultText)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MCPManager", "Error executing paid tool: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    onComplete(false, e.message)
                }
            }
        }
    }
    
    /**
     * Show payment confirmation dialog and retry tool call with payment proof
     */
    private fun showPaymentConfirmationAndRetry(
        appId: String,
        app: McpApp,
        tool: McpTool,
        argumentsMap: Map<String, Any>,
        paymentRequirements: PaymentRequirements,
        onComplete: (Boolean, String?) -> Unit
    ) {
        // Convert payment requirements to PaymentInfo for dialog
        val paymentInfo = PaymentInfo(
            required = true,
            price = paymentRequirements.price.amount.toDouble() / 1_000_000.0, // Convert micro-units to USDC
            currency = paymentRequirements.currency,
            description = paymentRequirements.description,
            recipient = paymentRequirements.recipient
        )
        
        // Get user's wallet address for payment
        val walletAddress = WalletStorage.getWalletAddress(context) ?: "unknown_wallet"
        
        // Create and show payment confirmation dialog
        val dialog = PaymentConfirmationDialog(
            context = context,
            toolName = tool.title ?: tool.name,
            paymentInfo = paymentInfo,
            onPaymentApproved = {
                // User approved payment - create real Solana payment and retry
                android.util.Log.e("MCPManager", ">>> Payment approved by user")
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        android.util.Log.e("MCPManager", ">>> Creating Solana payment...")
                        
                        // Create real Solana payment using Mobile Wallet Adapter
                        val paymentProof = paymentHandler.createPayment(paymentInfo, walletAddress)
                        android.util.Log.e("MCPManager", ">>> Payment proof created: ${paymentProof.transactionSignature}")
                        
                        // Add payment proof to arguments
                        val argumentsWithPayment = argumentsMap.toMutableMap()
                        argumentsWithPayment["_payment"] = mapOf(
                            "signature" to paymentProof.transactionSignature,
                            "timestamp" to java.time.Instant.now().toString(),
                            "amount" to paymentRequirements.price.amount,
                            "from" to paymentProof.from
                        )
                        
                        android.util.Log.e("MCPManager", ">>> Retrying tool call with payment proof...")
                        
                        // Retry tool call with payment proof
                        val result = mcpRepository.callTool(app.serverUrl, tool.name, argumentsWithPayment)
                        
                        val resultText = result?.content?.firstOrNull()?.let {
                            when (it) {
                                is McpContent.Text -> it.text
                                else -> "Tool executed successfully"
                            }
                        } ?: "Tool executed (no result)"
                        
                        // Store last invoked tool
                        lastInvokedTool = InvokedToolState(
                            appId = appId,
                            tool = tool,
                            parameters = argumentsWithPayment,
                            result = resultText
                        )
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Payment successful! Tool executed.", Toast.LENGTH_SHORT).show()
                            onToolResultReceived?.invoke(appId, tool, resultText, result, argumentsWithPayment)
                            onComplete(true, resultText)
                        }
                        
                    } catch (e: Exception) {
                        android.util.Log.e("MCPManager", "Error executing paid tool: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            onComplete(false, e.message)
                        }
                    }
                }
            },
            onPaymentDeclined = {
                // User declined payment
                android.util.Log.e("MCPManager", ">>> Payment declined by user")
                Toast.makeText(context, "Payment declined", Toast.LENGTH_SHORT).show()
                onComplete(false, "Payment declined")
            }
        )
        
        dialog.show()
    }
    
    /**
     * Show payment confirmation dialog and execute tool if approved (x402 protocol)
     */
    private fun showPaymentConfirmationAndExecute(
        appId: String,
        app: McpApp,
        tool: McpTool,
        argumentsMap: Map<String, Any>,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val paymentInfo = tool.payment ?: run {
            android.util.Log.e("MCPManager", "Payment info is null!")
            onComplete(false, "Payment information missing")
            return
        }
        
        // Get user's wallet address for payment
        val walletAddress = WalletStorage.getWalletAddress(context) ?: "unknown_wallet"
        
        // Create and show payment confirmation dialog
        val dialog = PaymentConfirmationDialog(
            context = context,
            toolName = tool.title ?: tool.name,
            paymentInfo = paymentInfo,
            onPaymentApproved = {
                // User approved payment - create REAL payment using Solana wallet
                android.util.Log.e("MCPManager", ">>> Payment approved by user")
                
                // Execute tool with payment proof
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        android.util.Log.e("MCPManager", ">>> Creating Solana payment...")
                        
                        // Create real Solana payment using Mobile Wallet Adapter
                        val paymentProof = paymentHandler.createPayment(paymentInfo, walletAddress)
                        android.util.Log.e("MCPManager", ">>> Payment proof created: ${paymentProof.transactionSignature}")
                        
                        android.util.Log.e("MCPManager", ">>> Executing paid tool...")
                        
                        // TODO: In production, pass payment proof to MCP server in X-PAYMENT header
                        // For now, we just execute the tool normally
                        val result = mcpRepository.callTool(app.serverUrl, tool.name, argumentsMap)
                        
                        val resultText = result?.content?.firstOrNull()?.let {
                            when (it) {
                                is McpContent.Text -> it.text
                                else -> "Tool executed successfully"
                            }
                        } ?: "Tool executed (no result)"
                        
                        // Store last invoked tool
                        lastInvokedTool = InvokedToolState(
                            appId = appId,
                            tool = tool,
                            parameters = argumentsMap,
                            result = resultText
                        )
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Payment successful! Tool executed.", Toast.LENGTH_SHORT).show()
                            onToolResultReceived?.invoke(appId, tool, resultText, result, argumentsMap)
                            onComplete(true, resultText)
                        }
                        
                    } catch (e: Exception) {
                        android.util.Log.e("MCPManager", "Error executing paid tool: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            onComplete(false, e.message)
                        }
                    }
                }
            },
            onPaymentDeclined = {
                // User declined payment
                android.util.Log.e("MCPManager", ">>> Payment declined by user")
                Toast.makeText(context, "Payment declined", Toast.LENGTH_SHORT).show()
                onComplete(false, "Payment declined")
            }
        )
        
        dialog.show()
    }
}

/**
 * Data classes for MCP state management
 */
data class ParameterCollectionState(
    val appId: String,
    val tool: McpTool,
    val requiredParams: List<String>,
    val collectedParams: MutableMap<String, Any>,
    val conversationHistory: MutableList<Pair<String, String>>
)

data class InvokedToolState(
    val appId: String,
    val tool: McpTool,
    val parameters: Map<String, Any>,
    val result: String
)

data class RoutingDecision(
    val action: String,
    val toolName: String?,
    val parameters: JSONObject?,
    val response: String?
)

