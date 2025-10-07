package com.myagentos.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private var isDarkMode: Boolean = false
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userMessageLayout: LinearLayout = itemView.findViewById(R.id.userMessageLayout)
        val aiMessageLayout: LinearLayout = itemView.findViewById(R.id.aiMessageLayout)
        val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
        val aiMessageText: TextView = itemView.findViewById(R.id.aiMessageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        
        if (message.isUser) {
            // Show user message (right aligned)
            holder.userMessageLayout.visibility = View.VISIBLE
            holder.aiMessageLayout.visibility = View.GONE
            holder.userMessageText.text = message.text
        } else {
            // Show AI message (left aligned)
            holder.userMessageLayout.visibility = View.GONE
            holder.aiMessageLayout.visibility = View.VISIBLE
            holder.aiMessageText.text = message.text
            
            // Apply theme colors to AI message with rounded corners
            val bubbleDrawable = android.graphics.drawable.GradientDrawable()
            val density = holder.itemView.context.resources.displayMetrics.density
            
            if (isDarkMode) {
                // Dark theme: dark grey bubble, white text
                bubbleDrawable.setColor(android.graphics.Color.parseColor("#2C2C2E"))
                holder.aiMessageText.setTextColor(android.graphics.Color.WHITE)
            } else {
                // Light theme: light grey bubble, dark text
                bubbleDrawable.setColor(android.graphics.Color.parseColor("#F1F1F1"))
                holder.aiMessageText.setTextColor(android.graphics.Color.parseColor("#1C1C1E"))
            }
            
            // Set rounded corners (different corners for AI message)
            bubbleDrawable.cornerRadii = floatArrayOf(
                4 * density, 4 * density,  // top left
                18 * density, 18 * density, // top right
                18 * density, 18 * density, // bottom right
                18 * density, 18 * density  // bottom left
            )
            
            holder.aiMessageLayout.getChildAt(0).background = bubbleDrawable
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    fun setDarkMode(isDark: Boolean) {
        isDarkMode = isDark
        notifyDataSetChanged()
    }
    
    fun getMessages(): MutableList<ChatMessage> {
        return messages
    }
}