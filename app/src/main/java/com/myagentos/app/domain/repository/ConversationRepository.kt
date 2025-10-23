package com.myagentos.app.domain.repository

import com.myagentos.app.domain.model.ChatMessage
import com.myagentos.app.data.database.ConversationDatabase

/**
 * Repository interface for conversation management
 * 
 * Provides abstraction over conversation data sources, enabling:
 * - Testability (easy to mock)
 * - Flexibility (can swap implementations)
 * - Clean separation of concerns
 */
interface ConversationRepository {
    
    /**
     * Start a new conversation
     * Clears current conversation state
     */
    fun startNewConversation()
    
    /**
     * Add a message to the current conversation
     * @param message The chat message to add
     */
    fun addMessage(message: ChatMessage)
    
    /**
     * Save the current conversation to persistent storage
     * @return The conversation ID, or -1 if save failed
     */
    fun saveCurrentConversation(): Long
    
    /**
     * Load a conversation by ID
     * @param id The conversation ID
     * @return List of messages in the conversation, or null if not found
     */
    fun loadConversation(id: Long): List<ChatMessage>?
    
    /**
     * Get all conversations from history
     * @return List of all conversations, sorted by most recent first
     */
    fun getAllConversations(): List<ConversationDatabase.Conversation>
    
    /**
     * Delete a conversation
     * @param id The conversation ID to delete
     */
    fun deleteConversation(id: Long)
    
    /**
     * Get the current conversation ID
     * @return The conversation ID, or null if no current conversation
     */
    fun getCurrentConversationId(): Long?
    
    /**
     * Check if there's an active conversation
     * @return true if there's a current conversation, false otherwise
     */
    fun hasCurrentConversation(): Boolean
    
    /**
     * Get the number of messages in the current conversation
     * @return The message count
     */
    fun getCurrentMessagesCount(): Int
    
    /**
     * Format a timestamp for display
     * @param timestamp The timestamp in milliseconds
     * @return Formatted string (e.g., "5m ago", "2h ago", "3d ago")
     */
    fun formatTimestamp(timestamp: Long): String
}

