package com.myagentos.app.data.repository

import com.myagentos.app.data.service.ExternalAIService
import com.myagentos.app.data.model.ModelType
import com.myagentos.app.domain.repository.AIRepository

/**
 * Implementation of AIRepository
 * 
 * Wraps the existing ExternalAIService to provide a clean repository interface.
 * This allows us to:
 * - Keep existing AI functionality working
 * - Add tests easily (by mocking the interface)
 * - Gradually improve error handling
 * - Potentially add caching or retry logic
 */
class AIRepositoryImpl(
    private val externalAIService: ExternalAIService
) : AIRepository {
    
    override suspend fun generateResponse(userMessage: String, modelType: ModelType): String {
        return externalAIService.generateResponse(userMessage, modelType)
    }
    
    override suspend fun generateResponseWithHistory(
        userMessage: String,
        modelType: ModelType,
        conversationHistory: List<Pair<String, String>>
    ): String {
        return externalAIService.generateResponseWithHistory(userMessage, modelType, conversationHistory)
    }
    
    override suspend fun generateResponseWithToolContext(
        userMessage: String,
        toolContext: String,
        conversationHistory: List<Pair<String, String>>,
        collectedParams: Map<String, Any>
    ): String {
        return externalAIService.generateResponseWithToolContext(
            userMessage,
            toolContext,
            conversationHistory,
            collectedParams
        )
    }
}

