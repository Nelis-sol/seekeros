package com.myagentos.app.domain.usecase

import com.myagentos.app.ChatMessage
import com.myagentos.app.ModelType
import com.myagentos.app.domain.repository.AIRepository
import com.myagentos.app.domain.repository.ConversationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull

/**
 * Unit tests for SendMessageUseCase
 * 
 * Tests the business logic of sending messages without UI dependencies
 */
@RunWith(MockitoJUnitRunner::class)
class SendMessageUseCaseTest {

    @Mock
    private lateinit var mockConversationRepository: ConversationRepository

    @Mock
    private lateinit var mockAIRepository: AIRepository

    private lateinit var useCase: SendMessageUseCase

    @Before
    fun setUp() {
        useCase = SendMessageUseCase(mockConversationRepository, mockAIRepository)
    }

    @Test
    fun `execute sends message and gets AI response`() = runTest {
        // Given
        val userMessage = "Hello AI"
        val modelType = ModelType.EXTERNAL_GROK
        val aiResponse = "Hello! How can I help you?"
        `when`(mockAIRepository.generateResponse(userMessage, modelType))
            .thenReturn(aiResponse)
        `when`(mockConversationRepository.saveCurrentConversation())
            .thenReturn(1L)

        // When
        val result = useCase.execute(userMessage, modelType)

        // Then
        assertEquals(userMessage, result.first.text)
        assertTrue(result.first.isUser)
        assertEquals(aiResponse, result.second.text)
        assertFalse(result.second.isUser)
        
        // Verify interactions
        verify(mockConversationRepository, times(2)).addMessage(any())
        verify(mockAIRepository).generateResponse(userMessage, modelType)
        verify(mockConversationRepository).saveCurrentConversation()
    }

    @Test
    fun `execute uses conversation history when provided`() = runTest {
        // Given
        val userMessage = "Continue"
        val modelType = ModelType.EXTERNAL_GROK
        val history = listOf("user" to "Hello", "assistant" to "Hi")
        val aiResponse = "Sure, continuing..."
        `when`(mockAIRepository.generateResponseWithHistory(userMessage, modelType, history))
            .thenReturn(aiResponse)

        // When
        val result = useCase.execute(userMessage, modelType, history)

        // Then
        assertEquals(aiResponse, result.second.text)
        verify(mockAIRepository).generateResponseWithHistory(userMessage, modelType, history)
        verify(mockAIRepository, never()).generateResponse(any(), any())
    }

    @Test
    fun `execute trims whitespace from message`() = runTest {
        // Given
        val messageWithWhitespace = "  Hello  "
        val modelType = ModelType.EXTERNAL_GROK
        `when`(mockAIRepository.generateResponse("Hello", modelType))
            .thenReturn("Hi")

        // When
        val result = useCase.execute(messageWithWhitespace, modelType)

        // Then
        assertEquals("Hello", result.first.text)
        verify(mockAIRepository).generateResponse("Hello", modelType)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `execute throws exception for empty message`() = runTest {
        // When/Then - should throw
        useCase.execute("", ModelType.EXTERNAL_GROK)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `execute throws exception for whitespace-only message`() = runTest {
        // When/Then - should throw
        useCase.execute("   ", ModelType.EXTERNAL_GROK)
    }

    @Test(expected = IllegalStateException::class)
    fun `execute throws exception when model is null`() = runTest {
        // When/Then - should throw
        useCase.execute("Hello", null)
    }

    @Test
    fun `execute adds user message to repository`() = runTest {
        // Given
        val userMessage = "Test"
        val modelType = ModelType.EXTERNAL_GROK
        `when`(mockAIRepository.generateResponse(userMessage, modelType))
            .thenReturn("Response")

        // When
        useCase.execute(userMessage, modelType)

        // Then - verify addMessage was called at least once for user message
        verify(mockConversationRepository, atLeast(1)).addMessage(any())
    }

    @Test
    fun `execute saves conversation after generating response`() = runTest {
        // Given
        val userMessage = "Save test"
        val modelType = ModelType.EXTERNAL_GROK
        `when`(mockAIRepository.generateResponse(userMessage, modelType))
            .thenReturn("Saved")

        // When
        useCase.execute(userMessage, modelType)

        // Then - verify save was called
        verify(mockConversationRepository).saveCurrentConversation()
    }

    @Test
    fun `execute works with different model types`() = runTest {
        // Test with ChatGPT
        val message = "Test ChatGPT"
        val chatGPTModel = ModelType.EXTERNAL_CHATGPT
        `when`(mockAIRepository.generateResponse(message, chatGPTModel))
            .thenReturn("ChatGPT response")

        val result1 = useCase.execute(message, chatGPTModel)
        
        assertEquals("ChatGPT response", result1.second.text)
        verify(mockAIRepository).generateResponse(message, chatGPTModel)
        
        // Test with Grok
        val message2 = "Test Grok"
        val grokModel = ModelType.EXTERNAL_GROK
        `when`(mockAIRepository.generateResponse(message2, grokModel))
            .thenReturn("Grok response")

        val result2 = useCase.execute(message2, grokModel)
        
        assertEquals("Grok response", result2.second.text)
        verify(mockAIRepository).generateResponse(message2, grokModel)
    }

    @Test
    fun `execute handles long messages`() = runTest {
        // Given
        val longMessage = "This is a very long message. ".repeat(100)
        val modelType = ModelType.EXTERNAL_GROK
        `when`(mockAIRepository.generateResponse(longMessage.trim(), modelType))
            .thenReturn("Response to long message")

        // When
        val result = useCase.execute(longMessage, modelType)

        // Then
        assertEquals(longMessage.trim(), result.first.text)
        verify(mockAIRepository).generateResponse(longMessage.trim(), modelType)
    }

    @Test
    fun `execute handles special characters in message`() = runTest {
        // Given
        val specialMessage = "Test with emoji 😀 and symbols !@#$%"
        val modelType = ModelType.EXTERNAL_GROK
        `when`(mockAIRepository.generateResponse(specialMessage, modelType))
            .thenReturn("Got it!")

        // When
        val result = useCase.execute(specialMessage, modelType)

        // Then
        assertEquals(specialMessage, result.first.text)
    }
}

