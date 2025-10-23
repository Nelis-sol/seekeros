package com.myagentos.app.data.repository

import com.myagentos.app.data.service.McpService
import com.myagentos.app.data.model.McpCapabilities
import com.myagentos.app.data.model.McpResource
import com.myagentos.app.data.model.McpTool
import com.myagentos.app.data.model.McpToolResult
import com.myagentos.app.domain.repository.McpRepository

/**
 * Implementation of McpRepository
 * 
 * Wraps the existing McpService singleton to provide a clean repository interface.
 */
class McpRepositoryImpl(
    private val mcpService: McpService
) : McpRepository {
    
    override fun setOnConnectionLost(callback: (String) -> Unit) {
        mcpService.setOnConnectionLost(callback)
    }
    
    override fun setSessionId(id: String) {
        mcpService.setSessionId(id)
    }
    
    override fun getSessionId(): String? {
        return mcpService.getSessionId()
    }
    
    override fun resetConnections() {
        mcpService.resetConnections()
    }
    
    override suspend fun initialize(serverUrl: String): McpCapabilities? {
        return mcpService.initialize(serverUrl)
    }
    
    override suspend fun listTools(serverUrl: String, cursor: String?): List<McpTool> {
        return mcpService.listTools(serverUrl, cursor)
    }
    
    override suspend fun callTool(
        serverUrl: String,
        toolName: String,
        arguments: Map<String, Any>,
        metadata: Map<String, Any>?
    ): McpToolResult? {
        return mcpService.callTool(serverUrl, toolName, arguments, metadata)
    }
    
    override suspend fun readResource(
        serverUrl: String,
        uri: String,
        displayMode: String
    ): McpResource? {
        return mcpService.readResource(serverUrl, uri, displayMode)
    }
    
    override suspend fun listResources(serverUrl: String, cursor: String?): List<McpResource> {
        return mcpService.listResources(serverUrl, cursor)
    }
}

