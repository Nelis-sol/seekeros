package com.myagentos.app.data.repository

import com.myagentos.app.ChatMessage
import com.myagentos.app.ConversationDatabase
import com.myagentos.app.ConversationManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

/**
 * Unit tests for ConversationRepositoryImpl
 * 
 * These tests verify that the repository correctly delegates to ConversationManager
 * and provides a clean interface for conversation operations.
 */
@RunWith(MockitoJUnitRunner::class)
class ConversationRepositoryImplTest {

    @Mock
    private lateinit var mockConversationManager: ConversationManager

    private lateinit var repository: ConversationRepositoryImpl

    @Before
    fun setUp() {
        repository = ConversationRepositoryImpl(mockConversationManager)
    }

    @Test
    fun `startNewConversation delegates to manager`() {
        // When
        repository.startNewConversation()

        // Then
        verify(mockConversationManager).startNewConversation()
    }

    @Test
    fun `addMessage delegates to manager`() {
        // Given
        val message = ChatMessage("Test", isUser = true, timestamp = System.currentTimeMillis())

        // When
        repository.addMessage(message)

        // Then
        verify(mockConversationManager).addMessage(message)
    }

    @Test
    fun `saveCurrentConversation returns conversation ID from manager`() {
        // Given
        val expectedId = 42L
        `when`(mockConversationManager.saveCurrentConversation()).thenReturn(expectedId)

        // When
        val result = repository.saveCurrentConversation()

        // Then
        assertEquals(expectedId, result)
        verify(mockConversationManager).saveCurrentConversation()
    }

    @Test
    fun `saveCurrentConversation returns -1 when save fails`() {
        // Given
        `when`(mockConversationManager.saveCurrentConversation()).thenReturn(-1)

        // When
        val result = repository.saveCurrentConversation()

        // Then
        assertEquals(-1, result)
    }

    @Test
    fun `loadConversation returns messages from manager`() {
        // Given
        val conversationId = 123L
        val messages = listOf(
            ChatMessage("Hello", isUser = true, timestamp = System.currentTimeMillis()),
            ChatMessage("Hi", isUser = false, timestamp = System.currentTimeMillis())
        )
        `when`(mockConversationManager.loadConversation(conversationId)).thenReturn(messages)

        // When
        val result = repository.loadConversation(conversationId)

        // Then
        assertEquals(messages, result)
        verify(mockConversationManager).loadConversation(conversationId)
    }

    @Test
    fun `loadConversation returns null when conversation not found`() {
        // Given
        val conversationId = 999L
        `when`(mockConversationManager.loadConversation(conversationId)).thenReturn(null)

        // When
        val result = repository.loadConversation(conversationId)

        // Then
        assertNull(result)
    }

    @Test
    fun `getAllConversations returns list from manager`() {
        // Given
        val conversations = listOf(
            ConversationDatabase.Conversation(
                id = 1,
                title = "Test 1",
                firstMessage = "Hello",
                lastMessage = "Bye",
                messages = emptyList(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        `when`(mockConversationManager.getAllConversations()).thenReturn(conversations)

        // When
        val result = repository.getAllConversations()

        // Then
        assertEquals(conversations, result)
        verify(mockConversationManager).getAllConversations()
    }

    @Test
    fun `getAllConversations returns empty list when no conversations`() {
        // Given
        `when`(mockConversationManager.getAllConversations()).thenReturn(emptyList())

        // When
        val result = repository.getAllConversations()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deleteConversation delegates to manager`() {
        // Given
        val conversationId = 456L

        // When
        repository.deleteConversation(conversationId)

        // Then
        verify(mockConversationManager).deleteConversation(conversationId)
    }

    @Test
    fun `getCurrentConversationId returns ID from manager`() {
        // Given
        val expectedId = 789L
        `when`(mockConversationManager.getCurrentConversationId()).thenReturn(expectedId)

        // When
        val result = repository.getCurrentConversationId()

        // Then
        assertEquals(expectedId, result)
    }

    @Test
    fun `getCurrentConversationId returns null when no current conversation`() {
        // Given
        `when`(mockConversationManager.getCurrentConversationId()).thenReturn(null)

        // When
        val result = repository.getCurrentConversationId()

        // Then
        assertNull(result)
    }

    @Test
    fun `hasCurrentConversation returns true when conversation exists`() {
        // Given
        `when`(mockConversationManager.hasCurrentConversation()).thenReturn(true)

        // When
        val result = repository.hasCurrentConversation()

        // Then
        assertTrue(result)
    }

    @Test
    fun `hasCurrentConversation returns false when no conversation`() {
        // Given
        `when`(mockConversationManager.hasCurrentConversation()).thenReturn(false)

        // When
        val result = repository.hasCurrentConversation()

        // Then
        assertFalse(result)
    }

    @Test
    fun `getCurrentMessagesCount returns count from manager`() {
        // Given
        val expectedCount = 5
        `when`(mockConversationManager.getCurrentMessagesCount()).thenReturn(expectedCount)

        // When
        val result = repository.getCurrentMessagesCount()

        // Then
        assertEquals(expectedCount, result)
    }

    @Test
    fun `getCurrentMessagesCount returns zero for empty conversation`() {
        // Given
        `when`(mockConversationManager.getCurrentMessagesCount()).thenReturn(0)

        // When
        val result = repository.getCurrentMessagesCount()

        // Then
        assertEquals(0, result)
    }

    @Test
    fun `formatTimestamp delegates to manager`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val expectedFormat = "5m ago"
        `when`(mockConversationManager.formatTimestamp(timestamp)).thenReturn(expectedFormat)

        // When
        val result = repository.formatTimestamp(timestamp)

        // Then
        assertEquals(expectedFormat, result)
        verify(mockConversationManager).formatTimestamp(timestamp)
    }
}

