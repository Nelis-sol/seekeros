package com.myagentos.app.presentation.adapter

import com.myagentos.app.R
import com.myagentos.app.domain.model.MessageType
import com.myagentos.app.data.model.BlinkMetadata
import com.myagentos.app.data.model.BlinkParameter
import com.myagentos.app.data.model.BlinkError
import com.myagentos.app.data.model.BlinkResponse
import com.myagentos.app.data.model.BlinkAction
import com.myagentos.app.data.model.BlinkLinkedAction
import com.myagentos.app.data.model.BlinkLinks
import com.myagentos.app.data.model.McpApp
import com.myagentos.app.domain.model.ChatMessage
import com.myagentos.app.presentation.activity.TabType
import com.myagentos.app.presentation.activity.McpToolInvocationActivity
import com.myagentos.app.presentation.widget.McpWebViewBridge
import com.myagentos.app.util.MarkdownFormatter
import com.myagentos.app.data.source.AppDirectory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    private var onBrowseClick: (() -> Unit)? = null,
    private var blinkCard: BlinkMetadata? = null,
    private var onBlinkActionClick: ((actionUrl: String, parameters: Map<String, String>) -> Unit)? = null,
    private var onMcpAppConnectClick: ((appId: String) -> Unit)? = null,
    private var getConnectedApps: (() -> Map<String, McpApp>)? = null,
    private var onMcpToolInvokeClick: ((appId: String, toolName: String) -> Unit)? = null,
    private var onMcpAppDetailsClick: ((appId: String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    // List to store dynamically loaded blinks (separate from the single blinkCard)
    private val dynamicBlinkCards = mutableListOf<BlinkMetadata>()
    
    // Cache for MCP apps (to avoid recreating cards every time)
    private var cachedMcpApps: List<McpApp> = emptyList()
    
    // Favorites manager (set externally)
    private var favoritesManager: com.myagentos.app.data.manager.FavoritesManager? = null

    companion object {
        private const val TYPE_MESSAGE = 0
        private const val TYPE_CARD = 1
        private const val TYPE_WELCOME_HEADER = 2
        private const val TYPE_TRENDING_HEADER = 3
        private const val TYPE_TYPING_INDICATOR = 4
        private const val TYPE_MCP_APP_CARD = 5  // New type for MCP app store-style cards
        private const val TYPE_MCP_WEBVIEW = 6  // New type for inline MCP WebView messages
    }
    
    private var isTyping = false
    private var currentCardType: TabType = TabType.BLINKS  // Track which tab is active

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userMessageLayout: LinearLayout = itemView.findViewById(R.id.userMessageLayout)
        val aiMessageLayout: LinearLayout = itemView.findViewById(R.id.aiMessageLayout)
        val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
        val aiMessageText: TextView = itemView.findViewById(R.id.aiMessageText)
    }

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardBannerContainer: android.widget.FrameLayout = itemView.findViewById(R.id.cardBannerContainer)
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
        val blinkActionsContainer: LinearLayout = itemView.findViewById(R.id.blinkActionsContainer)
        val regularActionsContainer: LinearLayout = itemView.findViewById(R.id.regularActionsContainer)
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
    
    class McpAppCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appBannerContainer: android.widget.FrameLayout = itemView.findViewById(R.id.mcpAppBannerContainer)
        val appIcon: ImageView = itemView.findViewById(R.id.mcpAppIcon)
        val appName: TextView = itemView.findViewById(R.id.mcpAppName)
        val appDescription: TextView = itemView.findViewById(R.id.mcpAppDescription)
        val toolsButtonContainer: com.google.android.flexbox.FlexboxLayout = itemView.findViewById(R.id.mcpToolsButtonContainer)
        val actionButtonsContainer: android.widget.LinearLayout = itemView.findViewById(R.id.mcpActionButtonsContainer)
        val viewButton: android.widget.Button = itemView.findViewById(R.id.mcpViewButton)
        val connectButton: android.widget.Button = itemView.findViewById(R.id.mcpConnectButton)
        val favoriteButton: android.widget.ImageButton = itemView.findViewById(R.id.mcpFavoriteButton)
    }
    
    class McpWebViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val webViewContainer: android.widget.FrameLayout = itemView.findViewById(R.id.webViewContainer)
        val webView: android.webkit.WebView = itemView.findViewById(R.id.mcpInlineWebView)
        val loading: android.widget.ProgressBar = itemView.findViewById(R.id.webViewLoading)
        val expandButton: android.widget.Button = itemView.findViewById(R.id.expandButton)
        
        init {
            // Configure WebView once when holder is created
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
            }
            
            // Set pure black background for WebView
            webView.setBackgroundColor(android.graphics.Color.BLACK)
            webViewContainer.setBackgroundColor(android.graphics.Color.BLACK)
            
            // Add JavaScript interface to capture calls from MCP app (OpenAI Apps SDK spec)
            webView.addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun log(message: String) {
                    android.util.Log.d("McpWebView-JS", "JS LOG: $message")
                }
                
                @android.webkit.JavascriptInterface
                fun callTool(toolName: String, args: String) {
                    android.util.Log.d("McpWebView-JS", "✓ callTool: $toolName with args: $args")
                    // TODO: Implement actual tool invocation
                }
                
                @android.webkit.JavascriptInterface
                fun sendFollowUpMessage(prompt: String) {
                    android.util.Log.d("McpWebView-JS", "✓ sendFollowUpMessage: $prompt")
                    // TODO: Send prompt as user message in chat
                }
                
                @android.webkit.JavascriptInterface
                fun openExternal(href: String) {
                    android.util.Log.d("McpWebView-JS", "✓ openExternal: $href")
                    // Open URL in browser
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(href))
                        itemView.context.startActivity(intent)
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun requestDisplayMode(mode: String) {
                    android.util.Log.d("McpWebView-JS", "✓ requestDisplayMode: $mode")
                    // TODO: Implement fullscreen/pip mode changes
                }
                
                @android.webkit.JavascriptInterface
                fun setWidgetState(stateJson: String) {
                    android.util.Log.d("McpWebView-JS", "✓ setWidgetState: $stateJson")
                    // TODO: Persist widget state and expose to ChatGPT
                }
            }, "AndroidBridge")
            
            // Set WebView client
            webView.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    loading.visibility = View.GONE
                    android.util.Log.d("McpWebView", "WebView loaded successfully")
                }
            }
            
            // Add WebChromeClient to capture console messages
            webView.webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        android.util.Log.d("McpWebView-Console", 
                            "[${it.messageLevel()}] ${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                    }
                    return true
                }
            }
        }
    }
    
    override fun getItemViewType(position: Int): Int {
        val hasBlinkCard = showCards && blinkCard != null
        val blinkCardCount = if (hasBlinkCard) 1 else 0
        
        // On APPS tab, use MCP app card layout
        val isAppsTab = currentCardType == TabType.APPS
        val cardType = if (isAppsTab) TYPE_MCP_APP_CARD else TYPE_CARD
        
        val type = when {
            !showCards && messages.isEmpty() && position == 0 -> TYPE_WELCOME_HEADER  // Show welcome only when no cards, no messages
            showCards && position == 0 -> TYPE_TRENDING_HEADER  // Show trending header at position 0 when cards are showing
            hasBlinkCard && position == 1 -> cardType  // Blink/App as a card at position 1 (right after trending header)
            showCards && position >= (1 + blinkCardCount) && position <= (16 + blinkCardCount) -> cardType  // Regular cards after blink
            isTyping && position == itemCount - 1 -> TYPE_TYPING_INDICATOR  // Typing indicator at the end
            else -> {
                // Determine if this is a message or blink
                val messageIndex = if (showCards) {
                    position - (17 + blinkCardCount)  // Trending header (1) + blink (0 or 1) + cards (16) = 17 or 18
                } else {
                    position
                }
                if (messageIndex >= 0 && messageIndex < messages.size) {
                    val message = messages[messageIndex]
                    android.util.Log.d("SimpleChatAdapter", "Message at index $messageIndex: messageType=${message.messageType}, hasWebViewData=${message.mcpWebViewData != null}")
                    if (message.messageType == MessageType.MCP_WEBVIEW) {
                        android.util.Log.d("SimpleChatAdapter", "Returning TYPE_MCP_WEBVIEW for message at index $messageIndex")
                        TYPE_MCP_WEBVIEW
                    } else {
                        android.util.Log.d("SimpleChatAdapter", "Returning TYPE_MESSAGE for message at index $messageIndex")
                        TYPE_MESSAGE
                    }
                } else {
                    TYPE_MESSAGE
                }
            }
        }
        android.util.Log.e("SimpleChatAdapter", "getItemViewType(position=$position): type=$type (showCards=$showCards, messages.size=${messages.size}, isTyping=$isTyping, hasBlinkCard=$hasBlinkCard)")
        return type
    }
    
    fun getSpanSize(position: Int, spanCount: Int): Int {
        return when (getItemViewType(position)) {
            TYPE_WELCOME_HEADER -> spanCount // Full width
            TYPE_TRENDING_HEADER -> spanCount // Full width
            TYPE_CARD -> spanCount // Full width (1 column)
            TYPE_MCP_APP_CARD -> spanCount // Full width (1 column)
            TYPE_MESSAGE -> spanCount // Full width
            TYPE_MCP_WEBVIEW -> spanCount // Full width
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
            TYPE_MCP_APP_CARD -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_mcp_app_card, parent, false)
                McpAppCardViewHolder(view)
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
            TYPE_MCP_WEBVIEW -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_mcp_webview_message, parent, false)
                McpWebViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val hasBlinkCard = showCards && blinkCard != null
        val blinkCardCount = if (hasBlinkCard) 1 else 0
        
        android.util.Log.e("SimpleChatAdapter", "onBindViewHolder() called for position: $position, holder type: ${holder.javaClass.simpleName}, messages.size: ${messages.size}, showCards: $showCards, hasBlinkCard: $hasBlinkCard")
        when (holder) {
            is MessageViewHolder -> {
                // Calculate the message index based on what's before the messages:
                // - If showCards=true: 1 trending header + blink (0 or 1) + 16 cards come first
                // - If showCards=false and messages.isNotEmpty(): NO welcome header, messages start at position 0
                val messageIndex = if (showCards) {
                    position - (17 + blinkCardCount)  // Trending header (1) + blink (0 or 1) + cards (16)
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
                // Check if this is the blink card at position 1
                if (hasBlinkCard && position == 1) {
                    // Convert blink to card and bind
                    val blinkAsCard = convertBlinkToCard(blinkCard!!)
                    bindCard(holder, blinkAsCard, position)
                } else {
                    // Regular cards start after trending header and optional blink card
                    val cardIndex = position - (1 + blinkCardCount)
                    if (cardIndex >= 0 && cardIndex < getCards().size) {
                        val card = getCards()[cardIndex]
                        bindCard(holder, card, position)
                    }
                }
            }
            is McpAppCardViewHolder -> {
                // MCP App cards (Apps tab)
                val cardIndex = position - (1 + blinkCardCount)
                if (cardIndex >= 0 && cardIndex < getCards().size) {
                    val card = getCards()[cardIndex]
                    bindMcpAppCard(holder, card, position)
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
            is McpWebViewHolder -> {
                // Calculate the message index
                val messageIndex = if (showCards) {
                    position - (17 + blinkCardCount)
                } else {
                    position
                }
                android.util.Log.e("SimpleChatAdapter", "Binding MCP WebView at position $position, messageIndex=$messageIndex")
                if (messageIndex >= 0 && messageIndex < messages.size) {
                    val message = messages[messageIndex]
                    bindMcpWebView(holder, message)
                }
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
            // Show AI message (left aligned) with markdown formatting
            holder.userMessageLayout.visibility = View.GONE
            holder.aiMessageLayout.visibility = View.VISIBLE
            holder.aiMessageText.text = MarkdownFormatter.format(message.text)
            
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
        val context = holder.itemView.context
        
        // Check if this card is a blink (either hardcoded or dynamic) by checking for blinkMetadata
        val isBlinkCard = card.blinkMetadata != null
        
        // For blink cards, show domain below title and hide category badge
        if (isBlinkCard) {
            holder.cardTitle.text = card.title
            holder.cardSubtitle.text = card.subtitle // Show domain as subtitle
            holder.cardCategory.visibility = View.GONE // Hide the category badge overlay
            holder.cardImage.visibility = View.GONE // Hide small icon for blink cards
            holder.companyIcon.visibility = View.GONE // Hide company branding for blink cards
            holder.companyName.visibility = View.GONE
            
            // Load blink image/banner from metadata with proper aspect ratio
            if (card.blinkMetadata != null && card.blinkMetadata.icon.isNotEmpty()) {
                val imageLoader = coil.ImageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(card.blinkMetadata.icon)
                    .target(
                        onSuccess = { result ->
                            holder.cardBanner.setImageDrawable(result)
                            
                            // Calculate proper height based on image aspect ratio
                            holder.cardBannerContainer.post {
                                val cardWidth = holder.cardBannerContainer.width
                                val drawable = result
                                val imageWidth = drawable.intrinsicWidth
                                val imageHeight = drawable.intrinsicHeight
                                
                                if (imageWidth > 0 && imageHeight > 0) {
                                    val aspectRatio = imageHeight.toFloat() / imageWidth.toFloat()
                                    var calculatedHeight = (cardWidth * aspectRatio).toInt()
                                    
                                    // Max height is card width (square)
                                    if (calculatedHeight > cardWidth) {
                                        calculatedHeight = cardWidth
                                    }
                                    
                                    val containerParams = holder.cardBannerContainer.layoutParams
                                    containerParams.height = calculatedHeight
                                    holder.cardBannerContainer.layoutParams = containerParams
                                    holder.cardBannerContainer.requestLayout()
                                }
                            }
                        }
                    )
                    .placeholder(R.drawable.stock_photo_banner)
                    .error(R.drawable.stock_photo_banner)
                    .build()
                imageLoader.enqueue(request)
            } else {
                // Fallback to square if no image
                holder.cardBannerContainer.post {
                    val cardWidth = holder.cardBannerContainer.width
                    val containerParams = holder.cardBannerContainer.layoutParams
                    containerParams.height = cardWidth
                    holder.cardBannerContainer.layoutParams = containerParams
                    holder.cardBannerContainer.requestLayout()
                }
            }
        } else {
            holder.cardTitle.text = card.title
            holder.cardSubtitle.text = card.subtitle
            holder.cardCategory.text = card.category
            holder.cardCategory.visibility = View.VISIBLE
            holder.cardImage.visibility = View.VISIBLE
            holder.companyIcon.visibility = View.VISIBLE
            holder.companyName.visibility = View.VISIBLE
            
            // Standard banner height for regular cards
            val containerParams = holder.cardBannerContainer.layoutParams
            containerParams.height = (120 * context.resources.displayMetrics.density).toInt()
            holder.cardBannerContainer.layoutParams = containerParams
            
            // Load image from URL if available, otherwise use resource
            if (card.imageUrl != null) {
                val imageLoader = coil.ImageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(card.imageUrl)
                    .target(holder.cardImage)
                    .placeholder(card.imageRes)
                    .error(card.imageRes)
                    .build()
                imageLoader.enqueue(request)
            } else {
                holder.cardImage.setImageResource(card.imageRes)
            }
            
            holder.cardBanner.setImageResource(R.drawable.stock_photo_banner)
        }
        
        holder.cardDescription.text = card.description
        holder.actionButton.text = card.actionButtonText
        
        // Set company branding
        holder.companyIcon.setImageResource(R.drawable.ic_company_logo)
        holder.companyName.text = "AgentOS"
        
        // Show banner on all cards
        holder.cardBanner.visibility = View.VISIBLE
        
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
            "solana action" -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#1C1C1E")) // Blink cards
            else -> cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#0A000000"))
        }
        
        // Handle action buttons differently for blink cards
        if (isBlinkCard && card.blinkMetadata != null) {
            // Hide regular buttons and show blink action buttons
            holder.regularActionsContainer.visibility = View.GONE
            holder.blinkActionsContainer.visibility = View.VISIBLE
            holder.blinkActionsContainer.removeAllViews()
            
            // Render all linked actions as buttons
            val linkedActions = card.blinkMetadata.links?.actions ?: emptyList()
            
            if (linkedActions.isNotEmpty()) {
                // Separate quick actions (no params) from parameterized actions (primary)
                val quickActions = linkedActions.filter { it.parameters == null || it.parameters.isEmpty() }
                val primaryActions = linkedActions.filter { it.parameters != null && it.parameters.isNotEmpty() }
                
                // Render quick action buttons (secondary style) - 3 per row
                if (quickActions.isNotEmpty()) {
                    var currentRow: LinearLayout? = null
                    quickActions.forEachIndexed { index, action ->
                        if (index % 3 == 0) {
                            // Create new row
                            currentRow = LinearLayout(context)
                            currentRow!!.orientation = LinearLayout.HORIZONTAL
                            currentRow!!.layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            currentRow!!.gravity = android.view.Gravity.START
                            val rowMargins = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            if (index > 0) rowMargins.topMargin = 8
                            currentRow!!.layoutParams = rowMargins
                            holder.blinkActionsContainer.addView(currentRow)
                        }
                        
                        val button = android.widget.Button(context)
                        button.text = action.label
                        button.setTextColor(android.graphics.Color.WHITE)
                        button.textSize = 11f
                        button.isAllCaps = false
                        button.setBackgroundResource(R.drawable.learn_more_button_background) // Secondary style
                        
                        val layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                        layoutParams.height = 32.dpToPx(context)
                        layoutParams.marginEnd = if (index % 3 < 2) 8.dpToPx(context) else 0
                        button.layoutParams = layoutParams
                        button.minWidth = 0
                        button.setPadding(12.dpToPx(context), 0, 12.dpToPx(context), 0)
                        
                        button.setOnClickListener {
                            android.util.Log.d("SimpleChatAdapter", "Quick action clicked: ${action.label}")
                            // Resolve relative URL against base URL
                            val fullUrl = resolveUrl(card.blinkMetadata?.actionUrl ?: "", action.href)
                            android.util.Log.d("SimpleChatAdapter", "Resolved URL: $fullUrl")
                            // Invoke callback to handle action execution
                            onBlinkActionClick?.invoke(fullUrl, emptyMap())
                        }
                        
                        currentRow?.addView(button)
                    }
                }
                
                // Render parameterized actions (primary style) with input fields
                primaryActions.forEach { action ->
                    val parameters = action.parameters ?: emptyList()
                    
                    if (parameters.isNotEmpty()) {
                        // Create a vertical container for all parameters and the button
                        val actionContainer = LinearLayout(context)
                        actionContainer.orientation = LinearLayout.VERTICAL
                        val containerParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        containerParams.topMargin = 12.dpToPx(context)
                        actionContainer.layoutParams = containerParams
                        
                        // Map to store input fields for validation
                        val parameterInputs = mutableMapOf<String, android.widget.EditText>()
                        
                        // Create input field for each parameter
                        parameters.forEach { param ->
                            val inputField = android.widget.EditText(context)
                            inputField.hint = param.label ?: param.name
                            inputField.setHintTextColor(android.graphics.Color.parseColor("#888888"))
                            inputField.setTextColor(android.graphics.Color.WHITE)
                            inputField.textSize = 14f
                            inputField.setBackgroundResource(R.drawable.blink_input_background)
                            
                            // Set input type based on parameter name/type
                            inputField.inputType = when {
                                param.name.contains("amount", ignoreCase = true) -> 
                                    android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                                else -> 
                                    android.text.InputType.TYPE_CLASS_TEXT
                            }
                            
                            val inputParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            inputParams.bottomMargin = 8.dpToPx(context)
                            inputField.layoutParams = inputParams
                            
                            actionContainer.addView(inputField)
                            parameterInputs[param.name] = inputField
                        }
                        
                        // Create primary action button
                        val button = android.widget.Button(context)
                        button.text = action.label
                        button.setTextColor(android.graphics.Color.WHITE)
                        button.textSize = 11f
                        button.isAllCaps = false
                        button.setBackgroundResource(R.drawable.action_button_background) // Primary style
                        
                        val buttonParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            32.dpToPx(context)
                        )
                        buttonParams.topMargin = 4.dpToPx(context)
                        button.layoutParams = buttonParams
                        button.minWidth = 0
                        button.setPadding(16.dpToPx(context), 0, 16.dpToPx(context), 0)
                        
                        button.setOnClickListener {
                            // Collect all parameter values
                            val collectedParams = mutableMapOf<String, String>()
                            var hasError = false
                            
                            parameters.forEach { param ->
                                val inputField = parameterInputs[param.name]
                                val value = inputField?.text?.toString() ?: ""
                                
                                if (param.required && value.isEmpty()) {
                                    inputField?.error = "Required"
                                    hasError = true
                                } else if (value.isNotEmpty()) {
                                    collectedParams[param.name] = value
                                }
                            }
                            
                            if (hasError) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Please fill in all required fields",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                android.util.Log.d("SimpleChatAdapter", "Primary action clicked: ${action.label} with params: $collectedParams")
                                // Resolve relative URL against base URL
                                val fullUrl = resolveUrl(card.blinkMetadata?.actionUrl ?: "", action.href)
                                android.util.Log.d("SimpleChatAdapter", "Resolved URL: $fullUrl")
                                // Invoke callback to handle action execution
                                onBlinkActionClick?.invoke(fullUrl, collectedParams)
                            }
                        }
                        
                        actionContainer.addView(button)
                        holder.blinkActionsContainer.addView(actionContainer)
                    }
                }
            }
        } else {
            // Show regular buttons for non-blink cards
            holder.regularActionsContainer.visibility = View.VISIBLE
            holder.blinkActionsContainer.visibility = View.GONE
            
            holder.learnMoreButton.visibility = View.VISIBLE
            holder.learnMoreButton.setOnClickListener {
                card.learnMoreAction()
            }
            
            holder.actionButton.setOnClickListener {
                card.actionButtonAction()
            }
        }
    }
    
    private fun Int.dpToPx(context: android.content.Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
    
    private fun resolveUrl(baseUrl: String, href: String): String {
        // If href is already a full URL, return it as-is
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href
        }
        
        // If baseUrl is empty, return href as-is (edge case)
        if (baseUrl.isEmpty()) {
            return href
        }
        
        // Parse base URL to get the protocol and domain
        return try {
            val url = java.net.URL(baseUrl)
            val protocol = url.protocol
            val host = url.host
            val port = if (url.port != -1) ":${url.port}" else ""
            
            // If href starts with /, it's an absolute path on the same domain
            if (href.startsWith("/")) {
                "$protocol://$host$port$href"
            } else {
                // If href is a relative path, resolve it against the base URL's path
                val basePath = url.path.substringBeforeLast('/', "")
                "$protocol://$host$port$basePath/$href"
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatAdapter", "Error resolving URL: $baseUrl + $href", e)
            href // Fallback to returning href as-is
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
    
    private fun bindMcpAppCard(holder: McpAppCardViewHolder, card: SubtleCard, position: Int) {
        val context = holder.itemView.context
        val connectedAppsMap = getConnectedApps?.invoke() ?: emptyMap()
        
        android.util.Log.d("SimpleChatAdapter", "🔄 bindMcpAppCard() called for: ${card.title} at position $position")
        
        // Set app name and description
        holder.appName.text = card.title
        holder.appDescription.text = card.description
        
        // Load app banner with dynamic height calculation (same as Blink cards)
        if (!card.imageUrl.isNullOrEmpty()) {
            val imageLoader = coil.ImageLoader(context)
            val request = coil.request.ImageRequest.Builder(context)
                .data(card.imageUrl)
                .size(coil.size.Size.ORIGINAL)  // Load original size for better quality
                .allowHardware(true)  // Use hardware bitmaps for better performance
                .target(
                    onSuccess = { result ->
                        holder.appIcon.setImageDrawable(result)
                        
                        // Calculate proper height based on image aspect ratio
                        holder.appBannerContainer.post {
                            val cardWidth = holder.appBannerContainer.width
                            val drawable = result
                            val imageWidth = drawable.intrinsicWidth
                            val imageHeight = drawable.intrinsicHeight
                            
                            if (imageWidth > 0 && imageHeight > 0) {
                                val aspectRatio = imageHeight.toFloat() / imageWidth.toFloat()
                                var calculatedHeight = (cardWidth * aspectRatio).toInt()
                                
                                // Max height is card width (square)
                                if (calculatedHeight > cardWidth) {
                                    calculatedHeight = cardWidth
                                }
                                
                                val containerParams = holder.appBannerContainer.layoutParams
                                containerParams.height = calculatedHeight
                                holder.appBannerContainer.layoutParams = containerParams
                                holder.appBannerContainer.requestLayout()
                            }
                        }
                    }
                )
                .placeholder(R.drawable.stock_photo_banner)
                .error(R.drawable.stock_photo_banner)
                .build()
            imageLoader.enqueue(request)
        } else {
            // Fallback to default banner
            holder.appIcon.setImageResource(card.imageRes)
        }
        
        // Find the corresponding McpApp to check connection status and tools
        val allApps = AppDirectory.getFeaturedApps()
        val mcpApp = allApps.find { it.name == card.title || it.description == card.description }
        val connectedApp = mcpApp?.let { connectedAppsMap[it.id] }
        
        // Set up favorite button (always visible)
        // IMPORTANT: Clear any previous click listeners to avoid duplicates during ViewHolder recycling
        holder.favoriteButton.setOnClickListener(null)
        
        // Read FRESH favorite state from manager (defensive copy ensures no stale data)
        val isFavorited = mcpApp?.let { favoritesManager?.isFavorite(it.id) } ?: false
        holder.favoriteButton.setImageResource(
            if (isFavorited) R.drawable.ic_heart_filled
            else R.drawable.ic_heart_outline
        )
        
        android.util.Log.d("SimpleChatAdapter", "❤️ Card for ${mcpApp?.name} - Favorite state: $isFavorited (position $position)")
        
        // Add touch listener for debugging
        holder.favoriteButton.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    android.util.Log.e("SimpleChatAdapter", "❤️ TOUCH DOWN on heart button for ${mcpApp?.name} at (${event.x}, ${event.y})")
                }
                android.view.MotionEvent.ACTION_UP -> {
                    android.util.Log.e("SimpleChatAdapter", "❤️ TOUCH UP on heart button for ${mcpApp?.name}")
                }
            }
            false // Return false to allow click listener to also fire
        }
        
        // Set up NEW click listener for this bind
        holder.favoriteButton.setOnClickListener { v ->
            android.util.Log.e("SimpleChatAdapter", "❤️❤️❤️ CLICK LISTENER FIRED! ❤️❤️❤️")
            mcpApp?.let { app ->
                android.util.Log.d("SimpleChatAdapter", "❤️ Heart button clicked for ${app.name} at position $position")
                android.util.Log.d("SimpleChatAdapter", "❤️ Before toggle - isFavorite: ${favoritesManager?.isFavorite(app.id)}")
                
                val newFavoriteState = favoritesManager?.toggleFavorite(app.id) ?: false
                
                android.util.Log.d("SimpleChatAdapter", "❤️ After toggle - newState: $newFavoriteState")
                android.util.Log.d("SimpleChatAdapter", "❤️ Verify - isFavorite: ${favoritesManager?.isFavorite(app.id)}")
                
                // Update the heart icon immediately (don't wait for rebind)
                holder.favoriteButton.setImageResource(
                    if (newFavoriteState) R.drawable.ic_heart_filled
                    else R.drawable.ic_heart_outline
                )
                
                android.util.Log.d("SimpleChatAdapter", "❤️ Updated heart icon to: ${if (newFavoriteState) "FILLED ❤️" else "OUTLINE 🤍"}")
                
                // Prevent any accidental rebind from reverting the change
                // by forcing this specific card to refresh from the correct state
                holder.favoriteButton.postDelayed({
                    val verifyState = favoritesManager?.isFavorite(app.id) ?: false
                    if (verifyState != newFavoriteState) {
                        android.util.Log.e("SimpleChatAdapter", "⚠️ Heart state mismatch detected! Correcting...")
                        holder.favoriteButton.setImageResource(
                            if (verifyState) R.drawable.ic_heart_filled
                            else R.drawable.ic_heart_outline
                        )
                    }
                }, 100)
            } ?: run {
                android.util.Log.e("SimpleChatAdapter", "❤️ ERROR: mcpApp is null!")
            }
        }
        
        // Set up View button (always visible)
        holder.viewButton.setOnClickListener {
            // Launch app details screen
            mcpApp?.let { app ->
                onMcpAppDetailsClick?.invoke(app.id)
            }
        }
        
        // Handle tool buttons and connect button state based on connection
        if (connectedApp != null && connectedApp.tools.isNotEmpty()) {
            // App is connected - show tool buttons, hide action buttons
            holder.toolsButtonContainer.visibility = View.VISIBLE
            holder.toolsButtonContainer.removeAllViews()
            holder.actionButtonsContainer.visibility = View.GONE
            
            // Create compact inline pill buttons for each tool using TextView for better padding control
            connectedApp.tools.forEach { tool ->
                val toolButton = android.widget.TextView(context)
                toolButton.text = tool.title ?: tool.name
                toolButton.setTextColor(android.graphics.Color.WHITE)
                toolButton.textSize = 13f
                toolButton.background = context.getDrawable(R.drawable.pill_button_background)
                toolButton.isClickable = true
                toolButton.isFocusable = true
                
                // Set exact same padding as Connect button using dp values
                val paddingHorizontal = (12 * context.resources.displayMetrics.density).toInt()
                val paddingVertical = (5 * context.resources.displayMetrics.density).toInt()
                toolButton.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
                
                val params = com.google.android.flexbox.FlexboxLayout.LayoutParams(
                    com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                    com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
                )
                val marginRight = (12 * context.resources.displayMetrics.density).toInt()  // Doubled
                val marginBottom = (12 * context.resources.displayMetrics.density).toInt()  // Doubled
                params.setMargins(0, 0, marginRight, marginBottom)  // Right and bottom margins for spacing
                toolButton.layoutParams = params
                
                toolButton.setOnClickListener {
                    mcpApp?.let { app ->
                        onMcpToolInvokeClick?.invoke(app.id, tool.name)
                    }
                }
                holder.toolsButtonContainer.addView(toolButton)
            }
            
            // Add a "Disconnect" button at the end using TextView for consistency
            val disconnectButton = android.widget.TextView(context)
            disconnectButton.text = "Disconnect"
            disconnectButton.setTextColor(android.graphics.Color.WHITE)
            disconnectButton.textSize = 13f
            disconnectButton.background = context.getDrawable(R.drawable.pill_button_background)
            disconnectButton.isClickable = true
            disconnectButton.isFocusable = true
            
            // Set exact same padding as Connect button (12dp horizontal, 5dp vertical)
            val paddingHorizontal = (12 * context.resources.displayMetrics.density).toInt()
            val paddingVertical = (5 * context.resources.displayMetrics.density).toInt()
            disconnectButton.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
            
            val disconnectParams = com.google.android.flexbox.FlexboxLayout.LayoutParams(
                com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
            )
            val marginRight = (12 * context.resources.displayMetrics.density).toInt()
            val marginBottom = (12 * context.resources.displayMetrics.density).toInt()
            disconnectParams.setMargins(0, 0, marginRight, marginBottom)
            disconnectButton.layoutParams = disconnectParams
            
            disconnectButton.setOnClickListener {
                // Disconnect the app - remove from connected apps
                mcpApp?.let { app ->
                    val connectedAppsMapCurrent = getConnectedApps?.invoke() as? MutableMap<String, McpApp>
                    connectedAppsMapCurrent?.remove(app.id)
                    // Refresh the adapter to show Connect button again
                    notifyDataSetChanged()
                }
            }
            holder.toolsButtonContainer.addView(disconnectButton)
        } else {
            // App not connected - show View + Connect buttons
            holder.toolsButtonContainer.visibility = View.GONE
            holder.actionButtonsContainer.visibility = View.VISIBLE
            holder.connectButton.visibility = View.VISIBLE
            holder.connectButton.setOnClickListener {
                card.actionButtonAction?.invoke()
            }
        }
    }
    
    private fun bindMcpWebView(holder: McpWebViewHolder, message: ChatMessage) {
        val context = holder.itemView.context
        val webViewData = message.mcpWebViewData
        
        android.util.Log.e("McpWebView", "=".repeat(80))
        android.util.Log.e("McpWebView", ">>> bindMcpWebView CALLED!")
        android.util.Log.e("McpWebView", "  - message: $message")
        android.util.Log.e("McpWebView", "  - message.messageType: ${message.messageType}")
        android.util.Log.e("McpWebView", "  - message.mcpWebViewData: $webViewData")
        
        if (webViewData == null) {
            android.util.Log.e("McpWebView", ">>> ERROR: webViewData is NULL! Cannot render WebView")
            return
        }
        
        android.util.Log.e("McpWebView", "  - webViewData.appId: ${webViewData.appId}")
        android.util.Log.e("McpWebView", "  - webViewData.toolName: ${webViewData.toolName}")
        android.util.Log.e("McpWebView", "  - webViewData.htmlContent length: ${webViewData.htmlContent.length}")
        android.util.Log.e("McpWebView", "  - webViewData.htmlContent preview: ${webViewData.htmlContent.take(300)}")
        android.util.Log.e("McpWebView", "  - webViewData.baseUrl: ${webViewData.baseUrl}")
        android.util.Log.e("McpWebView", "  - webViewData.serverUrl: ${webViewData.serverUrl}")
        
        // Show loading initially
        holder.loading.visibility = View.VISIBLE
        
        android.util.Log.e("McpWebView", ">>> Setting up McpWebViewBridge and injecting data...")
        
        // Create and setup bridge
        val bridge = McpWebViewBridge(
            webView = holder.webView,
            onAction = { actionName, arguments ->
                android.util.Log.d("McpWebView", "Action triggered: $actionName with args: $arguments")
                // Could trigger tool invocations or other actions here
            }
        )
        
        // Inject Apps SDK data (toolInput, toolOutput, toolResponseMetadata)
        android.util.Log.e("McpWebView", ">>> Injecting data:")
        android.util.Log.e("McpWebView", "  - toolInput: ${webViewData.toolInput}")
        android.util.Log.e("McpWebView", "  - toolOutput: ${webViewData.toolOutput}")
        android.util.Log.e("McpWebView", "  - toolResponseMetadata: ${webViewData.toolResponseMetadata}")
        
        // Only inject if we have data
        if (webViewData.toolInput != null || webViewData.toolOutput != null) {
            bridge.injectData(
                toolInput = webViewData.toolInput ?: org.json.JSONObject(),
                toolOutput = webViewData.toolOutput ?: org.json.JSONObject(),
                toolResponseMetadata = webViewData.toolResponseMetadata
            )
        }
        
        // Initialize the bridge
        bridge.initialize()
        
        android.util.Log.e("McpWebView", ">>> Loading HTML into WebView...")
        
        // Wrap HTML with dark theme CSS to force dark mode
        val darkThemedHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="dark">
                <style>
                    /* Force dark theme with pure black background */
                    html, body {
                        background-color: #000000 !important;
                        color: #ffffff !important;
                        margin: 0;
                        padding: 0;
                    }
                    
                    /* Force all text to be light colored */
                    *, *::before, *::after {
                        color: #ffffff !important;
                        color-scheme: dark;
                    }
                    
                    /* Ensure headings and paragraphs are white */
                    h1, h2, h3, h4, h5, h6, p, span, div, a, li, td, th, label, button {
                        color: #ffffff !important;
                    }
                    
                    /* Links should be light blue */
                    a {
                        color: #4dabf7 !important;
                    }
                    
                    /* Buttons and inputs */
                    button, input, select, textarea {
                        background-color: #2a2a2a !important;
                        color: #ffffff !important;
                        border-color: #444 !important;
                    }
                </style>
            </head>
            <body>
                ${webViewData.htmlContent}
            </body>
            </html>
        """.trimIndent()
        
        // Load the HTML content (WebView already configured in holder init)
        holder.webView.loadDataWithBaseURL(
            webViewData.baseUrl,
            darkThemedHtml,
            "text/html",
            "UTF-8",
            null
        )
        android.util.Log.e("McpWebView", ">>> WebView.loadDataWithBaseURL() called!")
        android.util.Log.e("McpWebView", "=".repeat(80))
        
        // Handle expand button - open fullscreen activity
        holder.expandButton.setOnClickListener {
            val webViewData = message.mcpWebViewData
            if (webViewData != null) {
                val intent = android.content.Intent(context, McpToolInvocationActivity::class.java).apply {
                    putExtra("APP_ID", webViewData.appId)
                    putExtra("TOOL_NAME", webViewData.toolName)
                    putExtra("HTML_CONTENT", webViewData.htmlContent)
                    putExtra("BASE_URL", webViewData.baseUrl)
                    putExtra("DISPLAY_MODE", "fullscreen") // Request fullscreen version
                    putExtra("SERVER_URL", webViewData.serverUrl)
                    putExtra("OUTPUT_TEMPLATE", webViewData.outputTemplate)
                    
                    // Pass tool arguments as JSON string
                    if (webViewData.toolArguments != null) {
                        val argsJson = org.json.JSONObject(webViewData.toolArguments as Map<*, *>).toString()
                        putExtra("TOOL_ARGUMENTS", argsJson)
                    }
                }
                context.startActivity(intent)
            }
        }
    }
    
    // Removed old blink message binding methods - we only use blink cards now
    
    
    private fun convertBlinkToCard(metadata: BlinkMetadata): SubtleCard {
        // Get the first linked action as the primary action button text
        val firstActionLabel = metadata.links?.actions?.firstOrNull()?.label ?: metadata.label ?: "Execute"
        
        // Extract domain from actionUrl for display
        val domain = try {
            val url = java.net.URL(metadata.actionUrl)
            url.host
        } catch (e: Exception) {
            "solana-action"
        }
        
        return SubtleCard(
            title = metadata.title,
            subtitle = domain, // Show domain as subtitle
            category = "Solana Action",
            description = metadata.description,
            imageRes = R.drawable.ic_apps, // Placeholder, will load from URL
            actionButtonText = firstActionLabel,
            learnMoreAction = { /* No-op for blinks */ },
            actionButtonAction = {
                // Placeholder for executing the action
                android.util.Log.d("SimpleChatAdapter", "Blink action clicked: ${metadata.title}")
            },
            imageUrl = metadata.icon, // Store the icon URL for loading
            blinkMetadata = metadata // Store full metadata for rendering all action buttons
        )
    }

    private fun getHardcodedBlinkCards(): List<SubtleCard> {
        // Hardcoded test blinks that always show
        val jupiterBlink = BlinkMetadata(
            title = "Buy JUP",
            icon = "https://ucarecdn.com/09c80208-f27c-45dd-b716-75e1e55832c4/-/preview/1000x981/-/quality/smart/-/format/auto/",
            description = "Buy JUP, the token for Jupiter, Solana's leading DEX aggregator.",
            label = "Buy JUP",
            disabled = false,
            links = BlinkLinks(
                actions = listOf(
                    BlinkLinkedAction(
                        href = "/swap/SOL-JUP/0.1",
                        label = "0.1 SOL",
                        parameters = null
                    ),
                    BlinkLinkedAction(
                        href = "/swap/SOL-JUP/0.5",
                        label = "0.5 SOL",
                        parameters = null
                    ),
                    BlinkLinkedAction(
                        href = "/swap/SOL-JUP/1",
                        label = "1 SOL",
                        parameters = null
                    ),
                    BlinkLinkedAction(
                        href = "/swap/SOL-JUP/{amount}",
                        label = "Buy JUP",
                        parameters = listOf(
                            BlinkParameter(
                                name = "amount",
                                label = "Enter a custom SOL amount",
                                required = false
                            )
                        )
                    )
                )
            ),
            error = null,
            actionUrl = "https://jupiter.dial.to/swap/SOL-JUP",
            parameters = null,
            image = null
        )
        
        val transferBlink = BlinkMetadata(
            title = "Solana Transfer",
            icon = "https://solana.dial.to/transfer_blink.png",
            description = "Send SOL or SPL tokens to any Solana wallet address.",
            label = "Send",
            disabled = false,
            links = BlinkLinks(
                actions = listOf(
                    BlinkLinkedAction(
                        href = "/api/actions/transfer?toWallet={toWallet}&token={token}&amount={amount}",
                        label = "Send",
                        parameters = listOf(
                            BlinkParameter(
                                name = "toWallet",
                                label = "Enter recipient wallet address",
                                required = true
                            ),
                            BlinkParameter(
                                name = "token",
                                label = "Enter the token you want to send",
                                required = true
                            ),
                            BlinkParameter(
                                name = "amount",
                                label = "Enter the amount you want to send",
                                required = true
                            )
                        )
                    )
                )
            ),
            error = null,
            actionUrl = "https://solana.dial.to/api/actions/transfer",
            parameters = null,
            image = null
        )
        
        return listOf(
            convertBlinkToCard(jupiterBlink),
            convertBlinkToCard(transferBlink)
        )
    }
    
    private fun getCards(): List<SubtleCard> {
        // Filter cards based on current tab type
        return when (currentCardType) {
            TabType.BLINKS -> getBlinkCards()
            TabType.APPS -> getMcpAppCards()
        }
    }
    
    private fun getBlinkCards(): List<SubtleCard> {
        // Start with hardcoded blinks
        val hardcodedBlinks = getHardcodedBlinkCards()
        
        // Add regular cards
        val regularCards = listOf(
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
        
        // Convert dynamic blinks to cards
        val dynamicBlinkCardsList = dynamicBlinkCards.map { convertBlinkToCard(it) }
        
        // Combine hardcoded blinks, dynamic blinks, then regular cards
        return hardcodedBlinks + dynamicBlinkCardsList + regularCards
    }
    
    private fun getMcpAppCards(): List<SubtleCard> {
        // Get available MCP apps from AppDirectory
        val allApps = AppDirectory.getFeaturedApps()
        val connectedAppsMap = getConnectedApps?.invoke() ?: emptyMap()
        
        android.util.Log.d("SimpleChatAdapter", "getMcpAppCards() called - ${allApps.size} total apps, ${connectedAppsMap.size} connected")
        
        val cards = mutableListOf<SubtleCard>()
        
        for (app in allApps) {
            val connectedApp = connectedAppsMap[app.id]
            val isConnected = connectedApp != null && connectedApp.tools.isNotEmpty()
            
            // Always create ONE card per app (tools will be shown as chips within the card)
            android.util.Log.d("SimpleChatAdapter", "App ${app.name} - connected: $isConnected, tools: ${connectedApp?.tools?.size ?: 0}")
            
            cards.add(SubtleCard(
                title = app.name,
                subtitle = if (isConnected) "${connectedApp!!.tools.size} tools available" else "Mini App",
                category = "MCP App",
                description = app.description,
                imageRes = R.drawable.ic_apps,
                actionButtonText = if (isConnected) "Connected" else "Connect",
                learnMoreAction = { /* Show app details */ },
                actionButtonAction = { 
                    if (!isConnected) {
                        onMcpAppConnectClick?.invoke(app.id)
                    }
                },
                imageUrl = app.icon,
                blinkMetadata = null
            ))
        }
        
        return cards
    }

    override fun getItemCount(): Int {
        val hasBlinkCard = showCards && blinkCard != null
        val blinkCardCount = if (hasBlinkCard) 1 else 0
        
        // 1 trending header + 2 hardcoded blinks + N dynamic blinks + 14 regular cards
        // + optional dynamic blink card = variable cards total
        val dynamicBlinkCount = if (showCards) dynamicBlinkCards.size else 0
        val baseCardsCount = 17 // 1 trending header + 2 hardcoded blinks + 14 regular cards
        val totalCardsCount = baseCardsCount + dynamicBlinkCount + blinkCardCount
        
        val count = messages.size + 
                   (if (showCards) totalCardsCount else if (messages.isEmpty()) 1 else 0) + 
                   (if (isTyping) 1 else 0) // Add 1 for typing indicator
        android.util.Log.e("SimpleChatAdapter", "getItemCount() returning: $count (messages.size=${messages.size}, showCards=$showCards, isTyping=$isTyping, hasBlinkCard=$hasBlinkCard, dynamicBlinkCount=$dynamicBlinkCount)")
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
    
    fun setCardType(cardType: TabType) {
        if (currentCardType != cardType) {
            currentCardType = cardType
            android.util.Log.d("SimpleChatAdapter", "Card type changed to: $cardType")
            // Refresh cards when tab changes
            notifyDataSetChanged()
        }
    }
    
    fun addDynamicBlinkCard(metadata: BlinkMetadata) {
        dynamicBlinkCards.add(metadata)
        notifyDataSetChanged()
    }
    
    fun clearDynamicBlinkCards() {
        dynamicBlinkCards.clear()
        notifyDataSetChanged()
    }
    
    fun getShowCards(): Boolean {
        return showCards
    }
    
    fun getMessages(): MutableList<ChatMessage> {
        return messages
    }
    
    /**
     * Set the FavoritesManager for handling MCP app favorites
     */
    fun setFavoritesManager(manager: com.myagentos.app.data.manager.FavoritesManager) {
        this.favoritesManager = manager
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
            isTyping = false
            notifyDataSetChanged() // Use full refresh to avoid animation conflicts
        }
    }
    
    fun setBlinkCard(metadata: BlinkMetadata?) {
        blinkCard = metadata
        notifyDataSetChanged()
    }
}
