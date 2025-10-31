package com.myagentos.app.data.source

import com.myagentos.app.R

import com.myagentos.app.data.model.McpApp
import com.myagentos.app.data.model.ConnectionStatus

/**
 * App Directory - Registry of available MCP apps
 * 
 * Manages the list of featured MCP apps that users can connect to.
 * In production, this would be fetched from a remote registry or marketplace.
 */
object AppDirectory {
    
    /**
     * Get list of featured MCP apps
     * 
     * @return List of MCP apps available for connection
     */
    fun getFeaturedApps(): List<McpApp> = listOf(
        McpApp(
            id = "pizzaz-app",
            name = "Pizzaz Demo",
            description = "OpenAI's reference MCP app demonstrating pizza maps, carousels, albums, and lists. Perfect for testing MCP functionality with interactive pizza-themed tools.",
            icon = "android.resource://com.myagentos.app/drawable/pizzas_demo_bg",
            serverUrl = "https://your-pizzaz-app.fly.dev/mcp",
            tools = emptyList(),
            connectionStatus = ConnectionStatus.DISCONNECTED
        )
    )
    
    /**
     * Get app by ID
     * 
     * @param id App identifier
     * @return App if found, null otherwise
     */
    fun getAppById(id: String): McpApp? {
        return getFeaturedApps().find { it.id == id }
    }
    
    /**
     * Search apps by name or description
     * 
     * @param query Search query
     * @return List of matching apps
     */
    fun searchApps(query: String): List<McpApp> {
        val lowerQuery = query.lowercase()
        return getFeaturedApps().filter { app ->
            app.name.lowercase().contains(lowerQuery) ||
            app.description.lowercase().contains(lowerQuery)
        }
    }
}

