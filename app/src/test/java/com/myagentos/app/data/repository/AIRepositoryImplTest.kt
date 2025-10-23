package com.myagentos.app.data.repository

import com.myagentos.app.ExternalAIService
import com.myagentos.app.ModelType
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

/**
 * Unit tests for AIRepositoryImpl
 * 
 * These tests verify that the repository correctly delegates to ExternalAIService
 * and provides a clean interface for AI operations.
 */
@RunWith(MockitoJUnitRunner::class)
class AIRepositoryImplTest {

    @Mock
    private lateinit var mockExternalAIService: ExternalAIService

    private lateinit var repository: AIRepositoryImpl

    @Before
    fun setUp() {
        repository = AIRepositoryImpl(mockExternalAIService)
    }

    @Test
    fun `generateResponse with Grok model delegates to service`() = runTest {
        // Given
        val userMessage = "Hello AI"
        val modelType = ModelType.EXTERNAL_GROK
        val expectedResponse = "Hello! How can I help you?"
        `when`(mockExternalAIService.generateResponse(userMessage, modelType))
            .thenReturn(expectedResponse)

        // When
        val result = repository.generateResponse(userMessage, modelType)

        // Then
        assertEquals(expectedResponse, result)
        verify(mockExternalAIService).generateResponse(userMessage, modelType)
    }

    @Test
    fun `generateResponse with ChatGPT model delegates to service`() = runTest {
        // Given
        val userMessage = "Test message"
        val modelType = ModelType.EXTERNAL_CHATGPT
        val expectedResponse = "ChatGPT integration not implemented yet."
        `when`(mockExternalAIService.generateResponse(userMessage, modelType))
            .thenReturn(expectedResponse)

        // When
        val result = repository.generateResponse(userMessage, modelType)

        // Then
        assertEquals(expectedResponse, result)
        verify(mockExternalAIService).generateResponse(userMessage, modelType)
    }

    @Test
    fun `generateResponse handles empty message`() = runTest {
        // Given
        val userMessage = ""
        val modelType = ModelType.EXTERNAL_GROK
        val expectedResponse = "Please provide a message"
        `when`(mockExternalAIService.generateResponse(userMessage, modelType))
            .thenReturn(expectedResponse)

        // When
        val result = repository.generateResponse(userMessage, modelType)

        // Then
        assertEquals(expectedResponse, result)
    }

    @Test
    fun `generateResponseWithHistory includes conversation context`() = runTest {
        // Given
        val userMessage = "Continue the conversation"
        val modelType = ModelType.EXTERNAL_GROK
        val history = listOf(
            "user" to "Hello",
            "assistant" to "Hi there!",
            "user" to "How are you?"
        )
        val expectedResponse = "I'm doing great, thank you!"
        `when`(mockExternalAIService.generateResponseWithHistory(userMessage, modelType, history))
            .thenReturn(expectedResponse)

        // When
        val result = repository.generateResponseWithHistory(userMessage, modelType, history)

        // Then
        assertEquals(expectedResponse, result)
        verify(mockExternalAIService).generateResponseWithHistory(userMessage, modelType, history)
    }

    @Test
    fun `generateResponseWithHistory handles empty history`() = runTest {
        // Given
        val userMessage = "First message"
        val modelType = ModelType.EXTERNAL_GROK
        val emptyHistory = emptyList<Pair<String, String>>()
        val expectedResponse = "Hello! First message received"
        `when`(mockExternalAIService.generateResponseWithHistory(userMessage, modelType, emptyHistory))
            .thenReturn(expectedResponse)

        // When
        val result = repository.generateResponseWithHistory(userMessage, modelType, emptyHistory)

        // Then
        assertEquals(expectedResponse, result)
    }

    @Test
    fun `generateResponseWithHistory handles long conversation history`() = runTest {
        // Given
        val userMessage = "What did we discuss?"
        val modelType = ModelType.EXTERNAL_GROK
        val longHistory = (1..20).flatMap { i ->
            listOf(
                "user" to "Message $i",
                "assistant" to "Response $i"
            )
        }
        val expectedResponse = "We discussed many topics"
        `when`(mockExternalAIService.generateResponseWithHistory(userMessage, modelType, longHistory))
            .thenReturn(expectedResponse)

        // When
        val result = repository.generateResponseWithHistory(userMessage, modelType, longHistory)

        // Then
        assertEquals(expectedResponse, result)
        assertEquals(40, longHistory.size) // Verify test data is correct
    }

    @Test
    fun `generateResponseWithToolContext delegates with all parameters`() = runTest {
        // Given
        val userMessage = "I want to search for pizza"
        val toolContext = """{"tool_title":"Search","tool_description":"Search for items","input_schema":{},"required_parameters":[]}"""
        val history = listOf("user" to "Start tool")
        val collectedParams = mapOf("query" to "pizza")
        val expectedResponse = "Searching for pizza..."
        `when`(mockExternalAIService.generateResponseWithToolContext(
            userMessage, toolContext, history, collectedParams
        )).thenReturn(expectedResponse)

        // When
        val result = repository.generateResponseWithToolContext(
            userMessage, toolContext, history, collectedParams
        )

        // Then
        assertEquals(expectedResponse, result)
        verify(mockExternalAIService).generateResponseWithToolContext(
            userMessage, toolContext, history, collectedParams
        )
    }

    @Test
    fun `generateResponseWithToolContext handles empty collected params`() = runTest {
        // Given
        val userMessage = "Start collecting parameters"
        val toolContext = """{"tool_title":"Test","tool_description":"Test tool","input_schema":{},"required_parameters":[]}"""
        val history = emptyList<Pair<String, String>>()
        val emptyParams = emptyMap<String, Any>()
        val expectedResponse = "What parameters do you need?"
        `when`(mockExternalAIService.generateResponseWithToolContext(
            userMessage, toolContext, history, emptyParams
        )).thenReturn(expectedResponse)

        // When
        val result = repository.generateResponseWithToolContext(
            userMessage, toolContext, history, emptyParams
        )

        // Then
        assertEquals(expectedResponse, result)
    }

    @Test
    fun `generateResponseWithToolContext handles complex parameters`() = runTest {
        // Given
        val userMessage = "Update the location"
        val toolContext = """{"tool_title":"Weather","tool_description":"Get weather","input_schema":{},"required_parameters":["location","unit"]}"""
        val history = listOf("user" to "Get weather", "assistant" to "Where?")
        val complexParams = mapOf(
            "location" to "San Francisco",
            "unit" to "celsius",
            "includeHourly" to true,
            "days" to 7
        )
        val expectedResponse = "Got it! Checking weather for San Francisco..."
        `when`(mockExternalAIService.generateResponseWithToolContext(
            userMessage, toolContext, history, complexParams
        )).thenReturn(expectedResponse)

        // When
        val result = repository.generateResponseWithToolContext(
            userMessage, toolContext, history, complexParams
        )

        // Then
        assertEquals(expectedResponse, result)
        assertEquals(4, complexParams.size) // Verify test data
    }

    @Test
    fun `repository handles service returning error message`() = runTest {
        // Given
        val userMessage = "Test"
        val modelType = ModelType.EXTERNAL_GROK
        val errorResponse = "Grok API Error (401): Unauthorized"
        `when`(mockExternalAIService.generateResponse(userMessage, modelType))
            .thenReturn(errorResponse)

        // When
        val result = repository.generateResponse(userMessage, modelType)

        // Then
        assertEquals(errorResponse, result)
        assertTrue(result.contains("Error"))
    }

    @Test
    fun `repository handles service returning network error`() = runTest {
        // Given
        val userMessage = "Test"
        val modelType = ModelType.EXTERNAL_GROK
        val networkError = "Network error calling Grok API: Connection refused"
        `when`(mockExternalAIService.generateResponse(userMessage, modelType))
            .thenReturn(networkError)

        // When
        val result = repository.generateResponse(userMessage, modelType)

        // Then
        assertEquals(networkError, result)
        assertTrue(result.contains("Network error"))
    }

    @Test
    fun `repository handles service returning API key error`() = runTest {
        // Given
        val userMessage = "Test"
        val modelType = ModelType.EXTERNAL_GROK
        val apiKeyError = "Grok API key not configured. Please check your local.properties file."
        `when`(mockExternalAIService.generateResponse(userMessage, modelType))
            .thenReturn(apiKeyError)

        // When
        val result = repository.generateResponse(userMessage, modelType)

        // Then
        assertEquals(apiKeyError, result)
        assertTrue(result.contains("API key"))
    }
}

