package com.myagentos.app

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
    val toolArguments: Map<String, Any>? = null
)
