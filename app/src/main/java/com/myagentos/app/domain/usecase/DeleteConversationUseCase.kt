package com.myagentos.app.domain.usecase

import com.myagentos.app.domain.repository.ConversationRepository

/**
 * Use case for deleting a conversation
 * 
 * Encapsulates the business logic for:
 * - Validating conversation ID
 * - Deleting conversation from repository
 * 
 * This separates business logic from UI, making it testable.
 */
class DeleteConversationUseCase(
    private val conversationRepository: ConversationRepository
) {
    
    /**
     * Execute the delete conversation operation
     * 
     * @param conversationId The ID of the conversation to delete
     * @throws IllegalArgumentException if conversationId is invalid
     */
    fun execute(conversationId: Long) {
        if (conversationId < 0) {
            throw IllegalArgumentException("Invalid conversation ID: $conversationId")
        }
        
        conversationRepository.deleteConversation(conversationId)
    }
}

