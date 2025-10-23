package com.myagentos.app.presentation.viewmodel

import app.cash.turbine.test
import com.myagentos.app.ChatMessage
import com.myagentos.app.ModelType
import com.myagentos.app.domain.repository.ConversationRepository
import com.myagentos.app.domain.usecase.DeleteConversationUseCase
import com.myagentos.app.domain.usecase.LoadConversationUseCase
import com.myagentos.app.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
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
 * Unit tests for ChatViewModel
 * 
 * Tests the coordination logic between UI and business layer.
 * Business logic itself is already tested in use case tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class ChatViewModelTest {

    @Mock
    private lateinit var mockConversationRepository: ConversationRepository

    @Mock
    private lateinit var mockSendMessageUseCase: SendMessageUseCase

    @Mock
    private lateinit var mockLoadConversationUseCase: LoadConversationUseCase

    @Mock
    private lateinit var mockDeleteConversationUseCase: DeleteConversationUseCase

    private lateinit var viewModel: ChatViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChatViewModel(
            mockConversationRepository,
            mockSendMessageUseCase,
            mockLoadConversationUseCase,
            mockDeleteConversationUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sendMessage without model selection shows error`() = runTest {
        // When
        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        // Then
        assertEquals("Please select an AI model first", viewModel.error.value)
        verify(mockSendMessageUseCase, never()).execute(any(), any(), any())
    }

    @Test
    fun `sendMessage with model calls use case and updates messages`() = runTest {
        // Given
        val model = ModelType.EXTERNAL_GROK
        val userMsg = ChatMessage("Hello", isUser = true)
        val aiMsg = ChatMessage("Hi there!", isUser = false)
        whenever(mockSendMessageUseCase.execute(any(), any(), any()))
            .thenReturn(Pair(userMsg, aiMsg))

        // When
        viewModel.selectModel(model)
        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        // Then
        assertEquals(listOf(userMsg, aiMsg), viewModel.messages.value)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `sendMessage sets loading state correctly`() = runTest {
        // Given
        val model = ModelType.EXTERNAL_GROK
        val userMsg = ChatMessage("Hello", isUser = true)
        val aiMsg = ChatMessage("Hi!", isUser = false)
        whenever(mockSendMessageUseCase.execute(any(), any(), any()))
            .thenReturn(Pair(userMsg, aiMsg))

        viewModel.selectModel(model)

        // When
        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        // Then
        assertFalse(viewModel.isLoading.value) // Should be false after completion
    }

    @Test
    fun `sendMessage handles errors gracefully`() = runTest {
        // Given
        val model = ModelType.EXTERNAL_GROK
        whenever(mockSendMessageUseCase.execute(any(), any(), any()))
            .thenThrow(RuntimeException("Network error"))

        viewModel.selectModel(model)

        // When
        viewModel.sendMessage("Hello")
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.error.value?.contains("Network error") == true)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `loadConversation updates messages`() = runTest {
        // Given
        val conversationId = 123L
        val messages = listOf(
            ChatMessage("Hello", isUser = true),
            ChatMessage("Hi!", isUser = false)
        )
        whenever(mockLoadConversationUseCase.execute(conversationId))
            .thenReturn(messages)

        // When
        viewModel.loadConversation(conversationId)
        advanceUntilIdle()

        // Then
        assertEquals(messages, viewModel.messages.value)
    }

    @Test
    fun `startNewConversation clears messages`() {
        // When
        viewModel.startNewConversation()

        // Then
        assertTrue(viewModel.messages.value.isEmpty())
        verify(mockConversationRepository).startNewConversation()
    }

    @Test
    fun `deleteConversation calls use case`() = runTest {
        // Given
        val conversationId = 456L

        // When
        viewModel.deleteConversation(conversationId)
        advanceUntilIdle()

        // Then
        verify(mockDeleteConversationUseCase).execute(conversationId)
    }

    @Test
    fun `selectModel updates selected model`() {
        // Given
        val model = ModelType.EXTERNAL_CHATGPT

        // When
        viewModel.selectModel(model)

        // Then
        assertEquals(model, viewModel.selectedModel.value)
    }

    @Test
    fun `clearError clears error state`() = runTest {
        // Given - set an error first
        viewModel.selectModel(ModelType.EXTERNAL_GROK)
        whenever(mockSendMessageUseCase.execute(any(), any(), any()))
            .thenThrow(RuntimeException("Test error"))
        
        viewModel.sendMessage("Test")
        advanceUntilIdle()
        assertNotNull(viewModel.error.value)

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.error.value)
    }
}

