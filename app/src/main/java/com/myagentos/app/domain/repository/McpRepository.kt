package com.myagentos.app.domain.repository

import com.myagentos.app.data.model.McpCapabilities
import com.myagentos.app.data.model.McpTool
import com.myagentos.app.data.model.McpToolResult
import com.myagentos.app.data.model.McpResource

/**
 * Repository interface for MCP (Model Context Protocol) operations
 * 
 * Provides abstraction over MCP service operations, enabling:
 * - Testability (easy to mock)
 * - Flexibility (can swap MCP implementations)
 * - Clean separation of concerns
 */
interface McpRepository {
    
    /**
     * Set callback for when connection is lost
     * @param callback Function to call with server URL when connection lost
     */
    fun setOnConnectionLost(callback: (String) -> Unit)
    
    /**
     * Set the session ID manually
     * @param id The session ID
     */
    fun setSessionId(id: String)
    
    /**
     * Get the current session ID
     * @return Session ID or null if not set
     */
    fun getSessionId(): String?
    
    /**
     * Reset all connection state
     */
    fun resetConnections()
    
    /**
     * Initialize connection to MCP server
     * @param serverUrl The MCP server endpoint URL
     * @return Server capabilities or null if initialization failed
     */
    suspend fun initialize(serverUrl: String): McpCapabilities?
    
    /**
     * List available tools from MCP server
     * @param serverUrl The MCP server endpoint URL
     * @param cursor Optional cursor for pagination
     * @return List of available tools
     */
    suspend fun listTools(serverUrl: String, cursor: String? = null): List<McpTool>
    
    /**
     * Call/invoke a tool on the MCP server
     * @param serverUrl The MCP server endpoint URL
     * @param toolName The name of the tool to invoke
     * @param arguments Map of arguments to pass to the tool
     * @param metadata Optional metadata for the call
     * @return Tool result
     */
    suspend fun callTool(
        serverUrl: String,
        toolName: String,
        arguments: Map<String, Any>,
        metadata: Map<String, Any>? = null
    ): McpToolResult?
    
    /**
     * Read a resource from the MCP server
     * @param serverUrl The MCP server endpoint URL
     * @param uri The URI of the resource to read
     * @param displayMode Display mode for the resource
     * @return McpResource or null if not found
     */
    suspend fun readResource(
        serverUrl: String,
        uri: String,
        displayMode: String = "inline"
    ): McpResource?
    
    /**
     * List available resources from MCP server
     * @param serverUrl The MCP server endpoint URL
     * @param cursor Optional cursor for pagination
     * @return List of available resources
     */
    suspend fun listResources(
        serverUrl: String,
        cursor: String? = null
    ): List<McpResource>
}

