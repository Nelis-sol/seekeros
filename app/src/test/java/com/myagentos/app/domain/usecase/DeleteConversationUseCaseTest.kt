package com.myagentos.app.domain.usecase

import com.myagentos.app.domain.repository.ConversationRepository
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any

/**
 * Unit tests for DeleteConversationUseCase
 */
@RunWith(MockitoJUnitRunner::class)
class DeleteConversationUseCaseTest {

    @Mock
    private lateinit var mockConversationRepository: ConversationRepository

    private lateinit var useCase: DeleteConversationUseCase

    @Before
    fun setUp() {
        useCase = DeleteConversationUseCase(mockConversationRepository)
    }

    @Test
    fun `execute deletes conversation with valid ID`() {
        // Given
        val conversationId = 42L

        // When
        useCase.execute(conversationId)

        // Then
        verify(mockConversationRepository).deleteConversation(conversationId)
    }

    @Test
    fun `execute deletes conversation with ID zero`() {
        // Given
        val conversationId = 0L

        // When
        useCase.execute(conversationId)

        // Then - 0 is technically valid
        verify(mockConversationRepository).deleteConversation(conversationId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `execute throws exception for negative ID`() {
        // When/Then - should throw
        useCase.execute(-1L)
    }

    @Test
    fun `execute does not call repository for invalid ID`() {
        // When
        try {
            useCase.execute(-5L)
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Then
        verify(mockConversationRepository, never()).deleteConversation(any())
    }

    @Test
    fun `execute handles large conversation IDs`() {
        // Given
        val largeId = Long.MAX_VALUE

        // When
        useCase.execute(largeId)

        // Then
        verify(mockConversationRepository).deleteConversation(largeId)
    }
}

