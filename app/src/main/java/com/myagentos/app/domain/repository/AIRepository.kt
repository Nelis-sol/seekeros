package com.myagentos.app.domain.repository

import com.myagentos.app.data.model.ModelType

/**
 * Repository interface for AI service operations
 * 
 * Provides abstraction over AI API calls, enabling:
 * - Testability (easy to mock)
 * - Flexibility (can swap AI providers)
 * - Clean separation of concerns
 * - Error handling consistency
 */
interface AIRepository {
    
    /**
     * Generate AI response for a user message
     * @param userMessage The user's message
     * @param modelType The AI model to use (Grok or ChatGPT)
     * @return AI-generated response
     */
    suspend fun generateResponse(userMessage: String, modelType: ModelType): String
    
    /**
     * Generate AI response with conversation history
     * @param userMessage The user's current message
     * @param modelType The AI model to use
     * @param conversationHistory Previous messages in (role, content) format
     * @return AI-generated response
     */
    suspend fun generateResponseWithHistory(
        userMessage: String,
        modelType: ModelType,
        conversationHistory: List<Pair<String, String>>
    ): String
    
    /**
     * Generate AI response with tool context (for parameter collection)
     * @param userMessage The user's message
     * @param toolContext JSON string describing the tool and parameters
     * @param conversationHistory Previous messages in (role, content) format
     * @param collectedParams Parameters already collected
     * @return AI-generated response to help collect parameters
     */
    suspend fun generateResponseWithToolContext(
        userMessage: String,
        toolContext: String,
        conversationHistory: List<Pair<String, String>>,
        collectedParams: Map<String, Any>
    ): String
}

