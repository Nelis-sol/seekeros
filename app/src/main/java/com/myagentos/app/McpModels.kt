package com.myagentos.app

import org.json.JSONObject

/**
 * MCP (Model Context Protocol) Data Models
 * Based on MCP specification: https://modelcontextprotocol.io/specification/2025-06-18/server/tools
 * And OpenAI Apps SDK: https://developers.openai.com/apps-sdk/reference
 */

// ============================================================================
// JSON-RPC Protocol Classes
// ============================================================================

/**
 * JSON-RPC 2.0 Request
 */
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: Map<String, Any>? = null
)

/**
 * JSON-RPC 2.0 Response
 */
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int,
    val result: Any? = null,
    val error: JsonRpcError? = null
)

/**
 * JSON-RPC 2.0 Error
 */
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
)

// ============================================================================
// MCP Tool Definition
// ============================================================================

/**
 * Tool definition from MCP server
 * 
 * @param name Unique identifier for the tool
 * @param title Optional human-readable display name
 * @param description Human-readable description of functionality
 * @param inputSchema JSON Schema defining expected parameters
 * @param outputSchema Optional JSON Schema defining expected output structure
 * @param _meta OpenAI-specific metadata (e.g., outputTemplate, security scopes)
 */
data class McpTool(
    val name: String,
    val title: String?,
    val description: String,
    val inputSchema: JSONObject,      // JSON Schema
    val outputSchema: JSONObject?,    // Optional JSON Schema
    val _meta: Map<String, Any>?      // OpenAI extensions
)

// Common _meta fields:
// - "openai/outputTemplate": "ui://widget/map.html" - HTML resource to render
// - "openai/toolInvocation/invoking": "Loading map..." - Loading message
// - "openai/toolInvocation/invoked": "Map loaded!" - Success message
// - "openai/security/scope": "read:location" - Security scope

/**
 * Annotations for content (audience, priority, timestamps)
 */
data class McpAnnotations(
    val audience: List<String>?,      // ["user", "assistant"]
    val priority: Float?,              // 0.0 to 1.0
    val lastModified: String?          // ISO 8601 timestamp
)

// ============================================================================
// MCP Tool Result
// ============================================================================

/**
 * Result from calling a tool
 * 
 * @param content Array of content items (text, images, resources, etc.)
 * @param structuredContent Optional structured JSON data for web components
 * @param isError True if tool execution failed
 * @param _meta Metadata not exposed to AI model (e.g., outputTemplate)
 */
data class McpToolResult(
    val content: List<McpContent>,
    val structuredContent: JSONObject?,
    val isError: Boolean = false,
    val _meta: Map<String, Any>?
)

/**
 * Content types that tools can return
 */
sealed class McpContent {
    /**
     * Text content
     */
    data class Text(
        val type: String = "text",
        val text: String,
        val annotations: McpAnnotations? = null
    ) : McpContent()
    
    /**
     * Image content (base64 encoded)
     */
    data class Image(
        val type: String = "image",
        val data: String,                // Base64 encoded
        val mimeType: String,            // e.g., "image/png"
        val annotations: McpAnnotations? = null
    ) : McpContent()
    
    /**
     * Audio content (base64 encoded)
     */
    data class Audio(
        val type: String = "audio",
        val data: String,                // Base64 encoded
        val mimeType: String,            // e.g., "audio/wav"
        val annotations: McpAnnotations? = null
    ) : McpContent()
    
    /**
     * Resource link (reference to a resource)
     */
    data class ResourceLink(
        val type: String = "resource_link",
        val uri: String,
        val name: String?,
        val description: String?,
        val mimeType: String?,
        val annotations: McpAnnotations? = null
    ) : McpContent()
    
    /**
     * Embedded resource
     */
    data class EmbeddedResource(
        val type: String = "resource",
        val resource: McpResource
    ) : McpContent()
}

// ============================================================================
// MCP Resource
// ============================================================================

/**
 * Resource definition (e.g., HTML components for UI)
 * 
 * @param uri Resource URI (e.g., "ui://widget/map.html")
 * @param mimeType MIME type (e.g., "text/html+skybridge" for UI components)
 * @param text Text content (for HTML, JSON, etc.)
 * @param blob Base64 encoded binary content
 * @param name Optional resource name
 * @param description Optional description
 * @param annotations Optional metadata
 */
data class McpResource(
    val uri: String,
    val mimeType: String,
    val text: String?,
    val blob: String?,
    val name: String? = null,
    val description: String? = null,
    val annotations: McpAnnotations? = null
)

// ============================================================================
// MCP App Metadata
// ============================================================================

/**
 * MCP application metadata
 * 
 * @param id Unique app identifier
 * @param name App display name
 * @param description App description
 * @param icon App icon URL or data URI
 * @param serverUrl MCP server endpoint URL
 * @param tools List of available tools (populated after connection)
 * @param connectionStatus Current connection status
 */
data class McpApp(
    val id: String,
    val name: String,
    val description: String,
    val icon: String?,
    val serverUrl: String,
    val tools: List<McpTool> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
)

/**
 * Connection status for MCP apps
 */
enum class ConnectionStatus {
    CONNECTED,      // Successfully connected, tools loaded
    CONNECTING,     // Currently establishing connection
    DISCONNECTED,   // Not connected
    ERROR           // Connection/communication error
}

/**
 * MCP server capabilities
 */
data class McpCapabilities(
    val tools: ToolsCapability? = null,
    val resources: ResourcesCapability? = null,
    val prompts: PromptsCapability? = null
)

/**
 * Tools capability
 */
data class ToolsCapability(
    val listChanged: Boolean = false  // Server will notify when tool list changes
)

/**
 * Resources capability
 */
data class ResourcesCapability(
    val subscribe: Boolean = false,   // Server supports resource subscriptions
    val listChanged: Boolean = false  // Server will notify when resource list changes
)

/**
 * Prompts capability
 */
data class PromptsCapability(
    val listChanged: Boolean = false  // Server will notify when prompt list changes
)

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Parse JSON-RPC response from string
 */
fun parseJsonRpcResponse(json: String): JsonRpcResponse {
    val jsonObj = JSONObject(json)
    return JsonRpcResponse(
        jsonrpc = jsonObj.getString("jsonrpc"),
        id = jsonObj.getInt("id"),
        result = jsonObj.opt("result"),
        error = if (jsonObj.has("error")) {
            val errorObj = jsonObj.getJSONObject("error")
            JsonRpcError(
                code = errorObj.getInt("code"),
                message = errorObj.getString("message"),
                data = errorObj.opt("data")
            )
        } else null
    )
}

/**
 * Convert JsonRpcRequest to JSON string
 */
fun JsonRpcRequest.toJson(): String {
    val json = JSONObject()
    json.put("jsonrpc", jsonrpc)
    json.put("id", id)
    json.put("method", method)
    params?.let { json.put("params", JSONObject(it)) }
    return json.toString()
}

