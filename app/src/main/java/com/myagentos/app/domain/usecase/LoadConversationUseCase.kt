package com.myagentos.app.domain.usecase

import com.myagentos.app.domain.model.ChatMessage
import com.myagentos.app.domain.repository.ConversationRepository

/**
 * Use case for loading a conversation by ID
 * 
 * Encapsulates the business logic for:
 * - Loading conversation from repository
 * - Validating conversation exists
 * - Returning messages
 * 
 * This separates business logic from UI, making it testable.
 */
class LoadConversationUseCase(
    private val conversationRepository: ConversationRepository
) {
    
    /**
     * Execute the load conversation operation
     * 
     * @param conversationId The ID of the conversation to load
     * @return List of ChatMessage if found, null if not found
     */
    fun execute(conversationId: Long): List<ChatMessage>? {
        if (conversationId < 0) {
            return null
        }
        
        return conversationRepository.loadConversation(conversationId)
    }
}

