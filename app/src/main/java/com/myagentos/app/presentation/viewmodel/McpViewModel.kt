package com.myagentos.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myagentos.app.data.model.ConnectionStatus
import com.myagentos.app.data.model.McpApp
import com.myagentos.app.data.model.McpCapabilities
import com.myagentos.app.data.model.McpResource
import com.myagentos.app.data.model.McpTool
import com.myagentos.app.data.model.McpToolResult
import com.myagentos.app.domain.repository.McpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for MCP (Model Context Protocol) functionality
 * 
 * Manages MCP app connections, tool invocations, and resource fetching.
 * Coordinates between the UI layer and McpRepository.
 * 
 * State Management:
 * - connectedApps: Map of connected MCP apps by ID
 * - availableTools: List of available tools from connected apps
 * - connectionStatus: Status of MCP connections
 * - lastToolResult: Result from last tool invocation
 * - isLoading: Whether an operation is in progress
 * - error: Error message if something went wrong
 */
class McpViewModel(
    private val mcpRepository: McpRepository
) : ViewModel() {
    
    // UI State
    private val _connectedApps = MutableStateFlow<Map<String, McpApp>>(emptyMap())
    val connectedApps: StateFlow<Map<String, McpApp>> = _connectedApps.asStateFlow()
    
    private val _availableTools = MutableStateFlow<List<McpTool>>(emptyList())
    val availableTools: StateFlow<List<McpTool>> = _availableTools.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    private val _lastToolResult = MutableStateFlow<McpToolResult?>(null)
    val lastToolResult: StateFlow<McpToolResult?> = _lastToolResult.asStateFlow()
    
    private val _currentResource = MutableStateFlow<McpResource?>(null)
    val currentResource: StateFlow<McpResource?> = _currentResource.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    init {
        // Set up connection loss listener
        mcpRepository.setOnConnectionLost { serverUrl ->
            handleConnectionLost(serverUrl)
        }
    }
    
    /**
     * Initialize connection to MCP server
     */
    fun initializeConnection(serverUrl: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _connectionStatus.value = ConnectionStatus.CONNECTING
                
                val capabilities = mcpRepository.initialize(serverUrl)
                
                if (capabilities != null) {
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    _error.value = null
                } else {
                    _connectionStatus.value = ConnectionStatus.ERROR
                    _error.value = "Failed to initialize connection"
                }
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.ERROR
                _error.value = "Connection error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * List available tools from MCP server
     */
    fun listTools(serverUrl: String, cursor: String? = null) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val tools = mcpRepository.listTools(serverUrl, cursor)
                _availableTools.value = tools
                _error.value = null
                
            } catch (e: Exception) {
                _error.value = "Failed to list tools: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Call/invoke a tool on the MCP server
     */
    fun callTool(
        serverUrl: String,
        toolName: String,
        arguments: Map<String, Any>,
        metadata: Map<String, Any>? = null
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val result = mcpRepository.callTool(serverUrl, toolName, arguments, metadata)
                _lastToolResult.value = result
                
                if (result?.isError == true) {
                    _error.value = "Tool execution error"
                } else {
                    _error.value = null
                }
                
            } catch (e: Exception) {
                _error.value = "Failed to call tool: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Read a resource from the MCP server
     */
    fun readResource(
        serverUrl: String,
        uri: String,
        displayMode: String = "inline"
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val resource = mcpRepository.readResource(serverUrl, uri, displayMode)
                _currentResource.value = resource
                
                if (resource == null) {
                    _error.value = "Resource not found"
                } else {
                    _error.value = null
                }
                
            } catch (e: Exception) {
                _error.value = "Failed to read resource: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * List available resources from MCP server
     */
    fun listResources(serverUrl: String, cursor: String? = null) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                val resources = mcpRepository.listResources(serverUrl, cursor)
                // Could store in state if needed
                _error.value = null
                
            } catch (e: Exception) {
                _error.value = "Failed to list resources: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Connect to an MCP app
     */
    fun connectToApp(app: McpApp) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _connectionStatus.value = ConnectionStatus.CONNECTING
                
                // Initialize connection
                val capabilities = mcpRepository.initialize(app.serverUrl)
                
                if (capabilities != null) {
                    // Fetch tools
                    val tools = mcpRepository.listTools(app.serverUrl)
                    
                    // Update connected app
                    val connectedApp = app.copy(
                        tools = tools,
                        connectionStatus = ConnectionStatus.CONNECTED
                    )
                    
                    _connectedApps.value = _connectedApps.value + (app.id to connectedApp)
                    _availableTools.value = tools
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    _error.value = null
                } else {
                    _connectionStatus.value = ConnectionStatus.ERROR
                    _error.value = "Failed to connect to ${app.name}"
                }
            } catch (e: Exception) {
                _connectionStatus.value = ConnectionStatus.ERROR
                _error.value = "Connection failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Disconnect from an MCP app
     */
    fun disconnectFromApp(appId: String) {
        viewModelScope.launch {
            try {
                val updatedApps = _connectedApps.value.toMutableMap()
                updatedApps.remove(appId)
                _connectedApps.value = updatedApps
                
                // If no more apps connected, update status
                if (updatedApps.isEmpty()) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    _availableTools.value = emptyList()
                }
            } catch (e: Exception) {
                _error.value = "Failed to disconnect: ${e.message}"
            }
        }
    }
    
    /**
     * Reset all connections
     */
    fun resetConnections() {
        viewModelScope.launch {
            try {
                mcpRepository.resetConnections()
                _connectedApps.value = emptyMap()
                _availableTools.value = emptyList()
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _lastToolResult.value = null
                _currentResource.value = null
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to reset connections: ${e.message}"
            }
        }
    }
    
    /**
     * Set session ID
     */
    fun setSessionId(sessionId: String) {
        mcpRepository.setSessionId(sessionId)
    }
    
    /**
     * Get current session ID
     */
    fun getSessionId(): String? {
        return mcpRepository.getSessionId()
    }
    
    /**
     * Clear error
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Clear last tool result
     */
    fun clearToolResult() {
        _lastToolResult.value = null
    }
    
    /**
     * Clear current resource
     */
    fun clearResource() {
        _currentResource.value = null
    }
    
    // Private helper methods
    
    private fun handleConnectionLost(serverUrl: String) {
        viewModelScope.launch {
            // Find and update the app that lost connection
            val updatedApps = _connectedApps.value.mapValues { (_, app) ->
                if (app.serverUrl == serverUrl) {
                    app.copy(connectionStatus = ConnectionStatus.ERROR)
                } else {
                    app
                }
            }
            _connectedApps.value = updatedApps
            _error.value = "Connection lost to: $serverUrl"
        }
    }
}

/**
 * Factory for creating McpViewModel with dependencies
 */
class McpViewModelFactory(
    private val mcpRepository: McpRepository
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(McpViewModel::class.java)) {
            return McpViewModel(mcpRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

