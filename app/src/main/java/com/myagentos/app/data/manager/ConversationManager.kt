package com.myagentos.app.data.manager

import com.myagentos.app.R
import com.myagentos.app.data.database.ConversationDatabase
import com.myagentos.app.domain.model.ChatMessage

import android.content.Context
import android.util.Log

class ConversationManager(private val context: Context) {
    private val database = ConversationDatabase(context)
    private var currentConversationId: Long? = null
    private var currentMessages: MutableList<ChatMessage> = mutableListOf()

    companion object {
        private const val TAG = "ConversationManager"
    }

    // Start a new conversation
    fun startNewConversation() {
        currentConversationId = null
        currentMessages.clear()
        Log.d(TAG, "Started new conversation")
    }

    // Add a message to the current conversation
    fun addMessage(message: ChatMessage) {
        currentMessages.add(message)
        Log.d(TAG, "Added message: ${message.text.take(50)}...")
    }

    // Save the current conversation
    fun saveCurrentConversation(): Long {
        if (currentMessages.isEmpty()) {
            Log.d(TAG, "No messages to save")
            return -1
        }

        val firstMessage = currentMessages.firstOrNull { it.isUser }?.text
        val lastMessage = currentMessages.lastOrNull()?.text
        val title = generateConversationTitle(firstMessage)

        val conversation = ConversationDatabase.Conversation(
            id = currentConversationId ?: 0,
            title = title,
            firstMessage = firstMessage,
            lastMessage = lastMessage,
            messages = currentMessages.toList(),
            createdAt = if (currentConversationId == null) System.currentTimeMillis() else 0,
            updatedAt = System.currentTimeMillis()
        )

        val id = if (currentConversationId == null) {
            // Create new conversation
            database.saveConversation(conversation)
        } else {
            // Update existing conversation
            database.updateConversation(currentConversationId!!, conversation)
            currentConversationId!!
        }

        currentConversationId = id
        Log.d(TAG, "Saved conversation with ID: $id")
        
        // Clean up old conversations
        database.cleanupOldConversations()
        
        return id
    }

    // Load a conversation by ID
    fun loadConversation(id: Long): List<ChatMessage>? {
        val conversation = database.getConversation(id)
        if (conversation != null) {
            currentConversationId = id
            currentMessages = conversation.messages.toMutableList()
            Log.d(TAG, "Loaded conversation: ${conversation.title}")
            return conversation.messages
        }
        Log.w(TAG, "Conversation not found: $id")
        return null
    }

    // Get all conversations for history
    fun getAllConversations(): List<ConversationDatabase.Conversation> {
        return database.getAllConversations()
    }

    // Delete a conversation
    fun deleteConversation(id: Long) {
        database.deleteConversation(id)
        if (currentConversationId == id) {
            startNewConversation()
        }
        Log.d(TAG, "Deleted conversation: $id")
    }

    // Get current conversation ID
    fun getCurrentConversationId(): Long? = currentConversationId

    // Check if we have a current conversation
    fun hasCurrentConversation(): Boolean = currentConversationId != null

    // Get current messages count
    fun getCurrentMessagesCount(): Int = currentMessages.size

    // Generate a conversation title from the first user message
    private fun generateConversationTitle(firstMessage: String?): String {
        if (firstMessage.isNullOrBlank()) {
            return "New Conversation"
        }
        
        // Truncate and clean up the title
        val cleanTitle = firstMessage.trim()
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
        
        return if (cleanTitle.length > 50) {
            cleanTitle.take(47) + "..."
        } else {
            cleanTitle
        }
    }

    // Format timestamp for display
    fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}d ago"
            else -> {
                val date = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
                date.format(java.util.Date(timestamp))
            }
        }
    }
}
