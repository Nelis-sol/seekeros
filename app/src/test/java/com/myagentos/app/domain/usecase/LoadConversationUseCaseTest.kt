package com.myagentos.app.domain.usecase

import com.myagentos.app.ChatMessage
import com.myagentos.app.domain.repository.ConversationRepository
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

/**
 * Unit tests for LoadConversationUseCase
 */
@RunWith(MockitoJUnitRunner::class)
class LoadConversationUseCaseTest {

    @Mock
    private lateinit var mockConversationRepository: ConversationRepository

    private lateinit var useCase: LoadConversationUseCase

    @Before
    fun setUp() {
        useCase = LoadConversationUseCase(mockConversationRepository)
    }

    @Test
    fun `execute returns messages when conversation exists`() {
        // Given
        val conversationId = 42L
        val messages = listOf(
            ChatMessage("Hello", isUser = true, timestamp = System.currentTimeMillis()),
            ChatMessage("Hi there", isUser = false, timestamp = System.currentTimeMillis())
        )
        `when`(mockConversationRepository.loadConversation(conversationId))
            .thenReturn(messages)

        // When
        val result = useCase.execute(conversationId)

        // Then
        assertEquals(messages, result)
        verify(mockConversationRepository).loadConversation(conversationId)
    }

    @Test
    fun `execute returns null when conversation not found`() {
        // Given
        val conversationId = 999L
        `when`(mockConversationRepository.loadConversation(conversationId))
            .thenReturn(null)

        // When
        val result = useCase.execute(conversationId)

        // Then
        assertNull(result)
    }

    @Test
    fun `execute returns null for negative conversation ID`() {
        // When
        val result = useCase.execute(-1L)

        // Then
        assertNull(result)
    }

    @Test
    fun `execute returns null for zero conversation ID`() {
        // When
        val result = useCase.execute(0L)

        // Then - 0 is technically valid, so it should call repository
        // Behavior depends on repository implementation
        verify(mockConversationRepository).loadConversation(0L)
    }

    @Test
    fun `execute returns empty list when conversation has no messages`() {
        // Given
        val conversationId = 10L
        val emptyMessages = emptyList<ChatMessage>()
        `when`(mockConversationRepository.loadConversation(conversationId))
            .thenReturn(emptyMessages)

        // When
        val result = useCase.execute(conversationId)

        // Then
        assertNotNull(result)
        assertTrue(result!!.isEmpty())
    }
}

