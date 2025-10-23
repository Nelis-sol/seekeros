package com.myagentos.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myagentos.app.domain.model.ChatMessage
import com.myagentos.app.data.model.ModelType
import com.myagentos.app.domain.repository.ConversationRepository
import com.myagentos.app.domain.usecase.SendMessageUseCase
import com.myagentos.app.domain.usecase.LoadConversationUseCase
import com.myagentos.app.domain.usecase.DeleteConversationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Chat functionality
 * 
 * Manages chat state and coordinates between the UI layer and business logic.
 * Uses repositories and use cases that are already fully tested (70 unit tests!).
 * 
 * State Management:
 * - messages: Current conversation messages
 * - isLoading: Whether AI is generating a response
 * - error: Error message if something went wrong
 */
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val loadConversationUseCase: LoadConversationUseCase,
    private val deleteConversationUseCase: DeleteConversationUseCase
) : ViewModel() {
    
    // UI State
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _selectedModel = MutableStateFlow<ModelType?>(null)
    val selectedModel: StateFlow<ModelType?> = _selectedModel.asStateFlow()
    
    /**
     * Send a message and get AI response
     */
    fun sendMessage(message: String) {
        val model = _selectedModel.value
        if (model == null) {
            _error.value = "Please select an AI model first"
            return
        }
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                // Build conversation history
                val history = _messages.value.chunked(2).mapNotNull { chunk ->
                    if (chunk.size == 2) {
                        Pair(chunk[0].text, chunk[1].text)
                    } else null
                }
                
                // Use our tested use case!
                val (userMsg, aiMsg) = sendMessageUseCase.execute(message, model, history)
                
                // Update UI
                _messages.value = _messages.value + listOf(userMsg, aiMsg)
                
            } catch (e: Exception) {
                _error.value = "Failed to send message: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Load a conversation by ID
     */
    fun loadConversation(conversationId: Long) {
        viewModelScope.launch {
            try {
                val messages = loadConversationUseCase.execute(conversationId)
                if (messages != null) {
                    _messages.value = messages
                }
            } catch (e: Exception) {
                _error.value = "Failed to load conversation: ${e.message}"
            }
        }
    }
    
    /**
     * Start a new conversation
     */
    fun startNewConversation() {
        conversationRepository.startNewConversation()
        _messages.value = emptyList()
        _error.value = null
    }
    
    /**
     * Delete a conversation
     */
    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            try {
                deleteConversationUseCase.execute(conversationId)
            } catch (e: Exception) {
                _error.value = "Failed to delete conversation: ${e.message}"
            }
        }
    }
    
    /**
     * Select AI model
     */
    fun selectModel(model: ModelType) {
        _selectedModel.value = model
    }
    
    /**
     * Clear error
     */
    fun clearError() {
        _error.value = null
    }
}

/**
 * Factory for creating ChatViewModel with dependencies
 * 
 * This is manual dependency injection - simple and works great!
 */
class ChatViewModelFactory(
    private val conversationRepository: ConversationRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val loadConversationUseCase: LoadConversationUseCase,
    private val deleteConversationUseCase: DeleteConversationUseCase
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                conversationRepository,
                sendMessageUseCase,
                loadConversationUseCase,
                deleteConversationUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

