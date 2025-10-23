package com.myagentos.app.presentation.viewmodel

import com.myagentos.app.ConnectionStatus
import com.myagentos.app.McpApp
import com.myagentos.app.McpCapabilities
import com.myagentos.app.McpContent
import com.myagentos.app.McpResource
import com.myagentos.app.McpTool
import com.myagentos.app.McpToolResult
import com.myagentos.app.ToolsCapability
import com.myagentos.app.domain.repository.McpRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Unit tests for McpViewModel
 * 
 * Tests MCP functionality coordination.
 * McpRepository itself is already tested (4 unit tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class McpViewModelTest {

    @Mock
    private lateinit var mockMcpRepository: McpRepository

    private lateinit var viewModel: McpViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = McpViewModel(mockMcpRepository)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializeConnection succeeds and updates status`() = runTest {
        // Given
        val serverUrl = "https://mcp.example.com"
        val capabilities = McpCapabilities(tools = ToolsCapability())
        whenever(mockMcpRepository.initialize(serverUrl)).thenReturn(capabilities)

        // When
        viewModel.initializeConnection(serverUrl)
        advanceUntilIdle()

        // Then
        assertEquals(ConnectionStatus.CONNECTED, viewModel.connectionStatus.value)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `initializeConnection fails and shows error`() = runTest {
        // Given
        val serverUrl = "https://mcp.example.com"
        whenever(mockMcpRepository.initialize(serverUrl)).thenReturn(null)

        // When
        viewModel.initializeConnection(serverUrl)
        advanceUntilIdle()

        // Then
        assertEquals(ConnectionStatus.ERROR, viewModel.connectionStatus.value)
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `initializeConnection handles exceptions`() = runTest {
        // Given
        val serverUrl = "https://mcp.example.com"
        whenever(mockMcpRepository.initialize(serverUrl))
            .thenThrow(RuntimeException("Network error"))

        // When
        viewModel.initializeConnection(serverUrl)
        advanceUntilIdle()

        // Then
        assertEquals(ConnectionStatus.ERROR, viewModel.connectionStatus.value)
        assertTrue(viewModel.error.value?.contains("Network error") == true)
    }

    @Test
    fun `listTools fetches and updates available tools`() = runTest {
        // Given
        val serverUrl = "https://mcp.example.com"
        val tools = listOf(
            McpTool(
                name = "search",
                title = "Search",
                description = "Search tool",
                inputSchema = JSONObject(),
                outputSchema = null,
                _meta = null
            )
        )
        whenever(mockMcpRepository.listTools(serverUrl, null)).thenReturn(tools)

        // When
        viewModel.listTools(serverUrl)
        advanceUntilIdle()

        // Then
        assertEquals(tools, viewModel.availableTools.value)
        assertNull(viewModel.error.value)
        verify(mockMcpRepository).listTools(serverUrl, null)
    }

    @Test
    fun `listTools with cursor passes cursor to repository`() = runTest {
        // Given
        val serverUrl = "https://mcp.example.com"
        val cursor = "cursor123"
        whenever(mockMcpRepository.listTools(serverUrl, cursor)).thenReturn(emptyList())

        // When
        viewModel.listTools(serverUrl, cursor)
        advanceUntilIdle()

        // Then
        verify(mockMcpRepository).listTools(serverUrl, cursor)
    }

    @Test
    fun `callTool invokes tool and stores result`() = runTest {
        // Given
        val serverUrl = "https://mcp.example.com"
        val toolName = "search"
        val arguments = mapOf("query" to "test")
        val result = McpToolResult(
            content = listOf(McpContent.Text(text = "Result")),
            structuredContent = null,
            isError = false,
            _meta = null
        )
        whenever(mockMcpRepository.callTool(serverUrl, toolName, arguments, null))
            .thenReturn(result)

        // When
        viewModel.callTool(serverUrl, toolName, arguments)
        advanceUntilIdle()

        // Then
        assertEquals(result, viewModel.lastToolResult.value)
        assertFalse(result.isError)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `callTool handles error results`() = runTest {
        // Given
        val serverUrl = "https://mcp.example.com"
        val toolName = "search"
        val arguments = mapOf("query" to "test")
        val errorResult = McpToolResult(
            content = listOf(McpContent.Text(text = "Error")),
            structuredContent = null,
            isError = true,
            _meta = null
        )
        whenever(mockMcpRepository.callTool(serverUrl, toolName, arguments, null))
            .thenReturn(errorResult)

        // When
        viewModel.callTool(serverUrl, toolName, arguments)
        advanceUntilIdle()

        // Then
        assertEquals(errorResult, viewModel.lastToolResult.value)
        assertTrue(errorResult.isError)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `readResource fetches and stores resource`() = runTest {
        // Given
        val serverUrl = "https://mcp.example.com"
        val uri = "resource://test"
        val resource = McpResource(
            uri = uri,
            mimeType = "text/html",
            text = "<html>Content</html>",
            blob = null
        )
        whenever(mockMcpRepository.readResource(serverUrl, uri, "inline"))
            .thenReturn(resource)

        // When
        viewModel.readResource(serverUrl, uri)
        advanceUntilIdle()

        // Then
        assertEquals(resource, viewModel.currentResource.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `readResource shows error when resource not found`() = runTest {
        // Given
        val serverUrl = "https://mcp.example.com"
        val uri = "resource://notfound"
        whenever(mockMcpRepository.readResource(serverUrl, uri, "inline"))
            .thenReturn(null)

        // When
        viewModel.readResource(serverUrl, uri)
        advanceUntilIdle()

        // Then
        assertNull(viewModel.currentResource.value)
        assertTrue(viewModel.error.value?.contains("not found") == true)
    }

    @Test
    fun `connectToApp successfully connects and fetches tools`() = runTest {
        // Given
        val app = McpApp(
            id = "app1",
            name = "Test App",
            description = "Test app description",
            icon = null,
            serverUrl = "https://mcp.example.com",
            connectionStatus = ConnectionStatus.DISCONNECTED
        )
        val capabilities = McpCapabilities(tools = ToolsCapability())
        val tools = listOf(
            McpTool(
                name = "tool1",
                title = "Tool 1",
                description = "Test tool",
                inputSchema = JSONObject(),
                outputSchema = null,
                _meta = null
            )
        )
        
        whenever(mockMcpRepository.initialize(app.serverUrl)).thenReturn(capabilities)
        whenever(mockMcpRepository.listTools(app.serverUrl)).thenReturn(tools)

        // When
        viewModel.connectToApp(app)
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.connectedApps.value.containsKey(app.id))
        assertEquals(ConnectionStatus.CONNECTED, viewModel.connectionStatus.value)
        assertEquals(tools, viewModel.availableTools.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `connectToApp handles connection failure`() = runTest {
        // Given
        val app = McpApp(
            id = "app2",
            name = "Test App",
            description = "Test app description",
            icon = null,
            serverUrl = "https://mcp.example.com",
            connectionStatus = ConnectionStatus.DISCONNECTED
        )
        whenever(mockMcpRepository.initialize(app.serverUrl)).thenReturn(null)

        // When
        viewModel.connectToApp(app)
        advanceUntilIdle()

        // Then
        assertEquals(ConnectionStatus.ERROR, viewModel.connectionStatus.value)
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun `disconnectFromApp removes app from connected list`() = runTest {
        // Given
        val app = McpApp(
            id = "app3",
            name = "Test App",
            description = "Test app description",
            icon = null,
            serverUrl = "https://mcp.example.com",
            connectionStatus = ConnectionStatus.CONNECTED
        )
        val capabilities = McpCapabilities(tools = ToolsCapability())
        whenever(mockMcpRepository.initialize(app.serverUrl)).thenReturn(capabilities)
        whenever(mockMcpRepository.listTools(app.serverUrl)).thenReturn(emptyList())
        
        viewModel.connectToApp(app)
        advanceUntilIdle()
        
        // When
        viewModel.disconnectFromApp(app.id)
        advanceUntilIdle()

        // Then
        assertFalse(viewModel.connectedApps.value.containsKey(app.id))
        assertEquals(ConnectionStatus.DISCONNECTED, viewModel.connectionStatus.value)
    }

    @Test
    fun `resetConnections clears all state`() = runTest {
        // When
        viewModel.resetConnections()
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.connectedApps.value.isEmpty())
        assertTrue(viewModel.availableTools.value.isEmpty())
        assertEquals(ConnectionStatus.DISCONNECTED, viewModel.connectionStatus.value)
        assertNull(viewModel.lastToolResult.value)
        assertNull(viewModel.currentResource.value)
        verify(mockMcpRepository).resetConnections()
    }

    @Test
    fun `setSessionId delegates to repository`() {
        // Given
        val sessionId = "session123"

        // When
        viewModel.setSessionId(sessionId)

        // Then
        verify(mockMcpRepository).setSessionId(sessionId)
    }

    @Test
    fun `getSessionId returns session from repository`() {
        // Given
        val sessionId = "session456"
        whenever(mockMcpRepository.getSessionId()).thenReturn(sessionId)

        // When
        val result = viewModel.getSessionId()

        // Then
        assertEquals(sessionId, result)
    }

    @Test
    fun `clearError clears error state`() = runTest {
        // Given - set an error first
        whenever(mockMcpRepository.initialize(any())).thenReturn(null)
        
        viewModel.initializeConnection("https://test.com")
        advanceUntilIdle()
        assertNotNull(viewModel.error.value)

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.error.value)
    }

    @Test
    fun `clearResource clears current resource`() = runTest {
        // Given - set a resource first
        val resource = McpResource(
            uri = "resource://test",
            mimeType = "text/html",
            text = "Content",
            blob = null
        )
        whenever(mockMcpRepository.readResource(any(), any(), any()))
            .thenReturn(resource)
        
        viewModel.readResource("https://test.com", "resource://test")
        advanceUntilIdle()
        assertNotNull(viewModel.currentResource.value)

        // When
        viewModel.clearResource()

        // Then
        assertNull(viewModel.currentResource.value)
    }
}

