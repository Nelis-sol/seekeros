package com.myagentos.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class SimpleChatItem {
    data class Message(val chatMessage: ChatMessage) : SimpleChatItem()
    data class Card(val subtleCard: SubtleCard) : SimpleChatItem()
    data class WelcomeHeader(val title: String, val subtitle: String) : SimpleChatItem()
    object TypingIndicator : SimpleChatItem()
}

class SimpleChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private var isDarkMode: Boolean = false,
    private var showCards: Boolean = false,
    private var onBrowseClick: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_MESSAGE = 0
        private const val TYPE_CARD = 1
        private const val TYPE_WELCOME_HEADER = 2
        private const val TYPE_TRENDING_HEADER = 3
        private const val TYPE_TYPING_INDICATOR = 4
    }
    
    private var isTyping = false

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userMessageLayout: LinearLayout = itemView.findViewById(R.id.userMessageLayout)
        val aiMessageLayout: LinearLayout = itemView.findViewById(R.id.aiMessageLayout)
        val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
        val aiMessageText: TextView = itemView.findViewById(R.id.aiMessageText)
    }

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardBanner: ImageView = itemView.findViewById(R.id.cardBanner)
        val cardImage: ImageView = itemView.findViewById(R.id.cardImage)
        val cardTitle: TextView = itemView.findViewById(R.id.cardTitle)
        val cardSubtitle: TextView = itemView.findViewById(R.id.cardSubtitle)
        val cardCategory: TextView = itemView.findViewById(R.id.cardCategory)
        val cardDescription: TextView = itemView.findViewById(R.id.cardDescription)
        val actionButton: android.widget.Button = itemView.findViewById(R.id.actionButton)
        val learnMoreButton: android.widget.Button = itemView.findViewById(R.id.learnMoreButton)
        val companyIcon: ImageView = itemView.findViewById(R.id.companyIcon)
        val companyName: TextView = itemView.findViewById(R.id.companyName)
    }

    class WelcomeHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val welcomeTitle: TextView = itemView.findViewById(R.id.welcomeTitle)
        val welcomeSubtitle: TextView = itemView.findViewById(R.id.welcomeSubtitle)
        val browsePill: LinearLayout = itemView.findViewById(R.id.browsePill)
    }
    
    class TypingIndicatorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dot1: TextView = itemView.findViewById(R.id.dot1)
        val dot2: TextView = itemView.findViewById(R.id.dot2)
        val dot3: TextView = itemView.findViewById(R.id.dot3)
    }

    class TrendingHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val trendingTitle: TextView = itemView.findViewById(R.id.trendingTitle)
    }

    override fun getItemViewType(position: Int): Int {
        val type = when {
            !showCards && messages.isEmpty() && position == 0 -> TYPE_WELCOME_HEADER  // Show welcome only when no cards, no messages
            showCards && position == 0 -> TYPE_TRENDING_HEADER  // Show trending header at position 0 when cards are showing
            showCards && position >= 1 && position <= 16 -> TYPE_CARD  // Cards start at position 1 when trending header is at 0
            isTyping && position == itemCount - 1 -> TYPE_TYPING_INDICATOR  // Typing indicator at the end
            else -> TYPE_MESSAGE
        }
        android.util.Log.e("SimpleChatAdapter", "getItemViewType(position=$position): type=$type (showCards=$showCards, messages.size=${messages.size}, isTyping=$isTyping)")
        return type
    }
    
    fun getSpanSize(position: Int, spanCount: Int): Int {
        return when (getItemViewType(position)) {
            TYPE_WELCOME_HEADER -> spanCount // Full width
            TYPE_TRENDING_HEADER -> spanCount // Full width
            TYPE_CARD -> spanCount // Full width (1 column)
            TYPE_MESSAGE -> spanCount // Full width
            else -> spanCount
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_MESSAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message, parent, false)
                MessageViewHolder(view)
            }
            TYPE_CARD -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_subtle_card, parent, false)
                CardViewHolder(view)
            }
            TYPE_WELCOME_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_welcome_header, parent, false)
                WelcomeHeaderViewHolder(view)
            }
            TYPE_TRENDING_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_trending_header, parent, false)
                TrendingHeaderViewHolder(view)
            }
            TYPE_TYPING_INDICATOR -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_typing_indicator, parent, false)
                TypingIndicatorViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        android.util.Log.e("SimpleChatAdapter", "onBindViewHolder() called for position: $position, holder type: ${holder.javaClass.simpleName}, messages.size: ${messages.size}, showCards: $showCards")
        when (holder) {
            is MessageViewHolder -> {
                // Calculate the message index based on what's before the messages:
                // - If showCards=true: 1 trending header + 16 cards come first, so messageIndex = position - 17
                // - If showCards=false but messages.isEmpty(): 1 welcome header at position 0 (but we never get here since messages.isEmpty() means no MessageViewHolder)
                // - If showCards=false and messages.isNotEmpty(): NO welcome header, messages start at position 0
                val messageIndex = if (showCards) {
                    position - 17  // Trending header (1) + cards (16) = 17, messages start at 17
                } else {
                    position  // No cards, no welcome (since we have messages), messages start at 0
                }
                android.util.Log.e("SimpleChatAdapter", "MessageViewHolder: messageIndex=$messageIndex, messages.size=${messages.size}")
                if (messageIndex >= 0 && messageIndex < messages.size) {
                    val message = messages[messageIndex]
                    android.util.Log.e("SimpleChatAdapter", "Binding message at index $messageIndex: isUser=${message.isUser}, text=${message.text.take(30)}")
                    bindMessage(holder, message)
                } else {
                    android.util.Log.e("SimpleChatAdapter", "WARNING: messageIndex $messageIndex out of bounds!")
                }
            }
            is CardViewHolder -> {
                val cardIndex = position - 1  // Cards start at position 1 (after trending header at position 0)
                if (cardIndex >= 0 && cardIndex < getCards().size) {
                    val card = getCards()[cardIndex]
                    bindCard(holder, card, position)
                }
            }
            is WelcomeHeaderViewHolder -> {
                android.util.Log.e("SimpleChatAdapter", "Binding welcome header at position $position")
                bindWelcomeHeader(holder)
            }
            is TrendingHeaderViewHolder -> {
                android.util.Log.e("SimpleChatAdapter", "Binding trending header at position $position")
                bindTrendingHeader(holder)
            }
            is TypingIndicatorViewHolder -> {
                android.util.Log.e("SimpleChatAdapter", "Binding typing indicator at position $position")
                // Typing indicator doesn't need binding, it's just animated dots
            }
        }
    }

    private fun bindMessage(holder: MessageViewHolder, message: ChatMessage) {
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

    private fun bindCard(holder: CardViewHolder, card: SubtleCard, position: Int) {
        holder.cardTitle.text = card.title
        holder.cardSubtitle.text = card.subtitle
        holder.cardCategory.text = card.category
        holder.cardDescription.text = card.description
        holder.cardImage.setImageResource(card.imageRes)
        holder.actionButton.text = card.actionButtonText
        
        // Set company branding
        holder.companyIcon.setImageResource(R.drawable.ic_company_logo)
        holder.companyName.text = "AgentOS"
        
        // Show banner on all cards
        holder.cardBanner.visibility = View.VISIBLE
        holder.cardBanner.setImageResource(R.drawable.stock_photo_banner)
        
        // Apply much more subtle background colors based on category
        val cardView = holder.itemView as androidx.cardview.widget.CardView
        when (card.category.lowercase()) {
            "productivity" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A2A"))
            "system" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A1A"))
            "search" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A1A"))
            "communication" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A1A"))
            "history" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A1A"))
            "recent" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A2A"))
            "contacts" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A1A"))
            "media" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A1A"))
            "entertainment" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A2A"))
            "navigation" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A1A"))
            "weather" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A1A"))
            "files" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A1A"))
            "web" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A1A2A"))
            else -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A000000"))
        }
        
        holder.learnMoreButton.setOnClickListener {
            card.learnMoreAction()
        }
        
        holder.actionButton.setOnClickListener {
            card.actionButtonAction()
        }
    }

    private fun bindWelcomeHeader(holder: WelcomeHeaderViewHolder) {
        holder.welcomeTitle.text = "Welcome to AgentOS"
        holder.welcomeSubtitle.text = "Your AI-powered launcher"
        holder.browsePill.setOnClickListener {
            onBrowseClick?.invoke()
        }
    }

    private fun bindTrendingHeader(holder: TrendingHeaderViewHolder) {
        holder.trendingTitle.text = "Trending actions"
    }

    private fun getCards(): List<SubtleCard> {
        return listOf(
            SubtleCard(
                title = "Smart Apps",
                subtitle = "AI-powered app launcher",
                category = "Productivity",
                description = "Launch your most used apps with intelligent suggestions based on your usage patterns.",
                imageRes = R.drawable.ic_apps,
                actionButtonText = "Launch",
                learnMoreAction = { /* Show app details */ },
                actionButtonAction = { /* Open apps */ }
            ),
            SubtleCard(
                title = "System Settings",
                subtitle = "Quick system access",
                category = "System",
                description = "Access system settings, WiFi, Bluetooth, and other device configurations instantly.",
                imageRes = R.drawable.ic_settings,
                actionButtonText = "Open",
                learnMoreAction = { /* Show settings details */ },
                actionButtonAction = { /* Open settings */ }
            ),
            SubtleCard(
                title = "Global Search",
                subtitle = "Find anything instantly",
                category = "Search",
                description = "Search across apps, files, contacts, and web content with AI-enhanced results.",
                imageRes = R.drawable.ic_search,
                actionButtonText = "Search",
                learnMoreAction = { /* Show search details */ },
                actionButtonAction = { /* Open search */ }
            ),
            SubtleCard(
                title = "Phone & Contacts",
                subtitle = "Communication hub",
                category = "Communication",
                description = "Make calls, send messages, and manage your contacts with smart suggestions.",
                imageRes = R.drawable.ic_phone,
                actionButtonText = "Call",
                learnMoreAction = { /* Show phone details */ },
                actionButtonAction = { /* Open phone */ }
            ),
            SubtleCard(
                title = "Conversation History",
                subtitle = "Your chat archive",
                category = "History",
                description = "View and manage your conversation history with AI assistants and agents.",
                imageRes = R.drawable.ic_history,
                actionButtonText = "View",
                learnMoreAction = { /* Show history details */ },
                actionButtonAction = { /* This will be handled by the parent activity */ }
            ),
            SubtleCard(
                title = "Recent Activity",
                subtitle = "Quick access to recent items",
                category = "Recent",
                description = "Access recently used apps, files, and actions for faster productivity.",
                imageRes = R.drawable.ic_apps,
                actionButtonText = "Open",
                learnMoreAction = { /* Show recent details */ },
                actionButtonAction = { /* This will be handled by the parent activity */ }
            ),
            SubtleCard(
                title = "Contact Manager",
                subtitle = "Smart contact organization",
                category = "Contacts",
                description = "Organize and manage your contacts with AI-powered categorization and search.",
                imageRes = R.drawable.ic_phone,
                actionButtonText = "Manage",
                learnMoreAction = { /* Show contacts details */ },
                actionButtonAction = { /* Open contacts */ }
            ),
            SubtleCard(
                title = "Camera & Photos",
                subtitle = "Visual content creation",
                category = "Media",
                description = "Take photos, record videos, and access your gallery with smart organization.",
                imageRes = R.drawable.ic_search,
                actionButtonText = "Capture",
                learnMoreAction = { /* Show camera details */ },
                actionButtonAction = { /* Open camera */ }
            ),
            SubtleCard(
                title = "Media Gallery",
                subtitle = "Your visual memories",
                category = "Media",
                description = "Browse and organize your photos and videos with AI-powered tagging.",
                imageRes = R.drawable.ic_apps,
                actionButtonText = "Browse",
                learnMoreAction = { /* Show gallery details */ },
                actionButtonAction = { /* Open gallery */ }
            ),
            SubtleCard(
                title = "Music Player",
                subtitle = "Audio entertainment",
                category = "Entertainment",
                description = "Play music, podcasts, and audio content with smart recommendations.",
                imageRes = R.drawable.ic_settings,
                actionButtonText = "Play",
                learnMoreAction = { /* Show music details */ },
                actionButtonAction = { /* Open music */ }
            ),
            SubtleCard(
                title = "Navigation & Maps",
                subtitle = "Location services",
                category = "Navigation",
                description = "Get directions, find places, and navigate with real-time traffic updates.",
                imageRes = R.drawable.ic_search,
                actionButtonText = "Navigate",
                learnMoreAction = { /* Show maps details */ },
                actionButtonAction = { /* Open maps */ }
            ),
            SubtleCard(
                title = "Weather Forecast",
                subtitle = "Current conditions",
                category = "Weather",
                description = "Get current weather conditions and forecasts for your location.",
                imageRes = R.drawable.ic_apps,
                actionButtonText = "Check",
                learnMoreAction = { /* Show weather details */ },
                actionButtonAction = { /* Open weather */ }
            ),
            SubtleCard(
                title = "Calendar & Events",
                subtitle = "Schedule management",
                category = "Productivity",
                description = "Manage your schedule, events, and reminders with smart suggestions.",
                imageRes = R.drawable.ic_settings,
                actionButtonText = "View",
                learnMoreAction = { /* Show calendar details */ },
                actionButtonAction = { /* Open calendar */ }
            ),
            SubtleCard(
                title = "Notes & Documents",
                subtitle = "Text and document editor",
                category = "Productivity",
                description = "Create, edit, and organize notes and documents with AI assistance.",
                imageRes = R.drawable.ic_search,
                actionButtonText = "Create",
                learnMoreAction = { /* Show notes details */ },
                actionButtonAction = { /* Open notes */ }
            ),
            SubtleCard(
                title = "File Manager",
                subtitle = "Storage organization",
                category = "Files",
                description = "Browse, organize, and manage your files and folders efficiently.",
                imageRes = R.drawable.ic_apps,
                actionButtonText = "Browse",
                learnMoreAction = { /* Show files details */ },
                actionButtonAction = { /* Open files */ }
            ),
            SubtleCard(
                title = "Web Browser",
                subtitle = "Internet access",
                category = "Web",
                description = "Browse the web with AI-enhanced search and smart bookmarking.",
                imageRes = R.drawable.ic_search,
                actionButtonText = "Browse",
                learnMoreAction = { /* Show browser details */ },
                actionButtonAction = { /* Open browser */ }
            )
        )
    }

    override fun getItemCount(): Int {
        val count = messages.size + 
                   (if (showCards) 17 else if (messages.isEmpty()) 1 else 0) + 
                   (if (isTyping) 1 else 0) // Add 1 for typing indicator
        android.util.Log.e("SimpleChatAdapter", "getItemCount() returning: $count (messages.size=${messages.size}, showCards=$showCards, isTyping=$isTyping)")
        return count
    }

    fun addMessage(message: ChatMessage) {
        android.util.Log.e("SimpleChatAdapter", "addMessage() called. Current size: ${messages.size}, adding: ${message.text.take(30)}")
        messages.add(message)
        android.util.Log.e("SimpleChatAdapter", "Message added. New size: ${messages.size}")
        notifyDataSetChanged()
        android.util.Log.e("SimpleChatAdapter", "notifyDataSetChanged() called")
    }
    
    fun removeMessage(message: ChatMessage) {
        val index = messages.indexOf(message)
        if (index >= 0) {
            messages.removeAt(index)
            notifyItemRemoved(index)
        }
    }
    
    fun setDarkMode(isDark: Boolean) {
        isDarkMode = isDark
        notifyDataSetChanged()
    }
    
    fun setShowCards(show: Boolean) {
        if (showCards != show) {
            showCards = show
            // Use notifyDataSetChanged() to avoid RecyclerView ViewHolder position inconsistencies
            // when switching between cards (16 items), messages (N items), and welcome header (1 item)
            notifyDataSetChanged()
        }
    }
    
    fun getShowCards(): Boolean {
        return showCards
    }
    
    fun getMessages(): MutableList<ChatMessage> {
        return messages
    }
    
    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }
    
    fun showTypingIndicator() {
        if (!isTyping) {
            isTyping = true
            notifyItemInserted(itemCount - 1)
        }
    }
    
    fun hideTypingIndicator() {
        if (isTyping) {
            val position = itemCount - 1
            isTyping = false
            notifyItemRemoved(position)
        }
    }
}
