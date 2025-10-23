package com.myagentos.app.data.repository

import com.myagentos.app.domain.model.ChatMessage
import com.myagentos.app.data.database.ConversationDatabase
import com.myagentos.app.data.manager.ConversationManager
import com.myagentos.app.domain.repository.ConversationRepository

/**
 * Implementation of ConversationRepository
 * 
 * Wraps the existing ConversationManager to provide a clean repository interface.
 * This allows us to:
 * - Keep existing functionality working
 * - Add tests easily (by mocking the interface)
 * - Gradually improve the implementation
 */
class ConversationRepositoryImpl(
    private val conversationManager: ConversationManager
) : ConversationRepository {
    
    override fun startNewConversation() {
        conversationManager.startNewConversation()
    }
    
    override fun addMessage(message: ChatMessage) {
        conversationManager.addMessage(message)
    }
    
    override fun saveCurrentConversation(): Long {
        return conversationManager.saveCurrentConversation()
    }
    
    override fun loadConversation(id: Long): List<ChatMessage>? {
        return conversationManager.loadConversation(id)
    }
    
    override fun getAllConversations(): List<ConversationDatabase.Conversation> {
        return conversationManager.getAllConversations()
    }
    
    override fun deleteConversation(id: Long) {
        conversationManager.deleteConversation(id)
    }
    
    override fun getCurrentConversationId(): Long? {
        return conversationManager.getCurrentConversationId()
    }
    
    override fun hasCurrentConversation(): Boolean {
        return conversationManager.hasCurrentConversation()
    }
    
    override fun getCurrentMessagesCount(): Int {
        return conversationManager.getCurrentMessagesCount()
    }
    
    override fun formatTimestamp(timestamp: Long): String {
        return conversationManager.formatTimestamp(timestamp)
    }
}

