package com.myagentos.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ConversationHistoryActivity : AppCompatActivity() {
    
    private lateinit var conversationRecyclerView: RecyclerView
    private lateinit var backButton: ImageButton
    private lateinit var newChatButton: ImageButton
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var conversationManager: ConversationManager
    private lateinit var adapter: ConversationListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_history)
        
        // Force dark background
        val rootView = findViewById<View>(android.R.id.content)
        rootView.setBackgroundColor(android.graphics.Color.BLACK)
        
        conversationManager = ConversationManager(this)
        
        setupUI()
        loadConversations()
    }
    
    private fun setupUI() {
        conversationRecyclerView = findViewById(R.id.conversationRecyclerView)
        backButton = findViewById(R.id.backButton)
        newChatButton = findViewById(R.id.newChatButton)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        
        // Setup back button
        backButton.setOnClickListener {
            finish()
        }
        
        // Setup new chat button
        newChatButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("start_new_chat", true)
            }
            startActivity(intent)
            finish()
        }
        
        // Setup RecyclerView
        conversationRecyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun loadConversations() {
        val conversations = conversationManager.getAllConversations()
        
        if (conversations.isEmpty()) {
            // Show empty state
            conversationRecyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        } else {
            // Show conversations
            conversationRecyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            
            adapter = ConversationListAdapter(
                conversations = conversations,
                onConversationClick = { conversation ->
                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("load_conversation_id", conversation.id)
                    }
                    startActivity(intent)
                    finish()
                },
                onConversationDelete = { conversation ->
                    conversationManager.deleteConversation(conversation.id)
                    // Refresh the list
                    val updatedConversations = conversationManager.getAllConversations()
                    if (updatedConversations.isEmpty()) {
                        conversationRecyclerView.visibility = View.GONE
                        emptyStateLayout.visibility = View.VISIBLE
                    } else {
                        adapter.updateConversations(updatedConversations)
                    }
                    Toast.makeText(this, "Conversation deleted", Toast.LENGTH_SHORT).show()
                }
            )
            
            conversationRecyclerView.adapter = adapter
        }
    }
}
