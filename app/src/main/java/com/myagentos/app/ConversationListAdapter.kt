package com.myagentos.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ConversationListAdapter(
    private var conversations: List<ConversationDatabase.Conversation>,
    private val onConversationClick: (ConversationDatabase.Conversation) -> Unit,
    private val onConversationDelete: (ConversationDatabase.Conversation) -> Unit
) : RecyclerView.Adapter<ConversationListAdapter.ConversationViewHolder>() {

    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.conversationTitle)
        val previewTextView: TextView = itemView.findViewById(R.id.conversationPreview)
        val timestampTextView: TextView = itemView.findViewById(R.id.conversationTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.conversation_list_item, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversations[position]
        
        holder.titleTextView.text = conversation.title
        holder.previewTextView.text = conversation.lastMessage ?: "No messages"
        
        // Format timestamp
        val timestamp = conversation.updatedAt
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        val formattedTime = when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}d ago"
            else -> {
                val date = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
                date.format(java.util.Date(timestamp))
            }
        }
        holder.timestampTextView.text = formattedTime
        
        // Set click listener
        holder.itemView.setOnClickListener {
            onConversationClick(conversation)
        }
        
        // Set long click listener for delete
        holder.itemView.setOnLongClickListener {
            onConversationDelete(conversation)
            true
        }
    }

    override fun getItemCount(): Int = conversations.size

    fun updateConversations(newConversations: List<ConversationDatabase.Conversation>) {
        conversations = newConversations
        notifyDataSetChanged()
    }
}
