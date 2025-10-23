package com.myagentos.app.domain.model

import com.myagentos.app.data.model.BlinkMetadata

enum class MessageType {
    TEXT,
    BLINK,
    MCP_WEBVIEW
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.TEXT,
    val blinkMetadata: BlinkMetadata? = null,
    val mcpWebViewData: McpWebViewData? = null
)

data class McpWebViewData(
    val appId: String,
    val toolName: String,
    val htmlContent: String,
    val baseUrl: String = "https://persistent.oaistatic.com/",
    val serverUrl: String? = null,
    val outputTemplate: String? = null,
    val toolArguments: Map<String, Any>? = null,
    // Apps SDK required data
    val toolInput: org.json.JSONObject? = null,           // Arguments passed to tool
    val toolOutput: org.json.JSONObject? = null,          // Structured result
    val toolResponseMetadata: Map<String, Any>? = null    // _meta from server
)
