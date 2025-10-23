package com.myagentos.app.domain.usecase

import com.myagentos.app.domain.model.ChatMessage
import com.myagentos.app.data.model.ModelType
import com.myagentos.app.domain.repository.AIRepository
import com.myagentos.app.domain.repository.ConversationRepository

/**
 * Use case for sending a chat message
 * 
 * Encapsulates the business logic for:
 * - Validating the message
 * - Saving user message
 * - Generating AI response
 * - Saving AI response
 * - Persisting conversation
 * 
 * This separates business logic from UI concerns, making it easily testable.
 */
class SendMessageUseCase(
    private val conversationRepository: ConversationRepository,
    private val aiRepository: AIRepository
) {
    
    /**
     * Execute the send message operation
     * 
     * @param message The user's message text
     * @param modelType The AI model to use
     * @param conversationHistory Optional conversation history for context
     * @return Pair of (userMessage, aiResponse) as ChatMessage objects
     * @throws IllegalArgumentException if message is empty
     * @throws IllegalStateException if modelType is null
     */
    suspend fun execute(
        message: String,
        modelType: ModelType?,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): Pair<ChatMessage, ChatMessage> {
        // Validation
        val trimmedMessage = message.trim()
        if (trimmedMessage.isEmpty()) {
            throw IllegalArgumentException("Message cannot be empty")
        }
        
        if (modelType == null) {
            throw IllegalStateException("AI model not selected")
        }
        
        // Create user message
        val userMessage = ChatMessage(trimmedMessage, isUser = true)
        
        // Save user message
        conversationRepository.addMessage(userMessage)
        
        // Generate AI response with history if available
        val aiResponseText = if (conversationHistory.isNotEmpty()) {
            aiRepository.generateResponseWithHistory(trimmedMessage, modelType, conversationHistory)
        } else {
            aiRepository.generateResponse(trimmedMessage, modelType)
        }
        
        // Create AI message
        val aiMessage = ChatMessage(aiResponseText, isUser = false)
        
        // Save AI response
        conversationRepository.addMessage(aiMessage)
        
        // Persist conversation
        conversationRepository.saveCurrentConversation()
        
        return Pair(userMessage, aiMessage)
    }
}

