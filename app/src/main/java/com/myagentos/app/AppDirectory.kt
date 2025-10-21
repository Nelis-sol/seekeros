package com.myagentos.app

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
            icon = "https://ucarecdn.com/09c80208-f27c-45dd-b716-75e1e55832c4/-/preview/100x100/",
            serverUrl = "https://your-pizzaz-app.fly.dev/mcp",
            tools = emptyList(),
            connectionStatus = ConnectionStatus.DISCONNECTED
        ),
        McpApp(
            id = "weather-app",
            name = "Weather",
            description = "Get current weather forecasts and conditions for any location. Stay informed about weather patterns and plan your day accordingly.",
            icon = "https://example.com/icons/weather.png",  // Placeholder URL
            serverUrl = "https://weather-mcp.example.com",   // Placeholder MCP server
            tools = emptyList(),
            connectionStatus = ConnectionStatus.DISCONNECTED
        ),
        McpApp(
            id = "todo-app",
            name = "Tasks",
            description = "Manage your todo list and tasks with AI assistance. Organize your daily activities and boost your productivity.",
            icon = "https://example.com/icons/todo.png",     // Placeholder URL
            serverUrl = "https://todo-mcp.example.com",      // Placeholder MCP server
            tools = emptyList(),
            connectionStatus = ConnectionStatus.DISCONNECTED
        ),
        McpApp(
            id = "calendar-app",
            name = "Calendar",
            description = "Schedule events and manage your calendar intelligently. Never miss important meetings or appointments again.",
            icon = "https://example.com/icons/calendar.png", // Placeholder URL
            serverUrl = "https://calendar-mcp.example.com",  // Placeholder MCP server
            tools = emptyList(),
            connectionStatus = ConnectionStatus.DISCONNECTED
        ),
        McpApp(
            id = "calculator-app",
            name = "Calculator",
            description = "Perform complex calculations and mathematical operations. Handle everything from basic arithmetic to advanced equations.",
            icon = "https://example.com/icons/calc.png",     // Placeholder URL
            serverUrl = "https://calc-mcp.example.com",      // Placeholder MCP server
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

