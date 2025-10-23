package com.myagentos.app.data.repository

import com.myagentos.app.McpService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

/**
 * Unit tests for McpRepositoryImpl
 * 
 * Simplified tests focusing on delegation to McpService
 */
@RunWith(MockitoJUnitRunner::class)
class McpRepositoryImplTest {

    @Mock
    private lateinit var mockMcpService: McpService

    private lateinit var repository: McpRepositoryImpl

    @Before
    fun setUp() {
        repository = McpRepositoryImpl(mockMcpService)
    }

    @Test
    fun `setOnConnectionLost delegates to service`() {
        // Given
        val callback: (String) -> Unit = { }

        // When
        repository.setOnConnectionLost(callback)

        // Then
        verify(mockMcpService).setOnConnectionLost(callback)
    }

    @Test
    fun `setSessionId delegates to service`() {
        // Given
        val sessionId = "session123"

        // When
        repository.setSessionId(sessionId)

        // Then
        verify(mockMcpService).setSessionId(sessionId)
    }

    @Test
    fun `getSessionId calls service`() {
        // When
        repository.getSessionId()

        // Then
        verify(mockMcpService).getSessionId()
    }

    @Test
    fun `resetConnections delegates to service`() {
        // When
        repository.resetConnections()

        // Then
        verify(mockMcpService).resetConnections()
    }
}
