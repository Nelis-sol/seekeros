package com.myagentos.app.presentation.manager
import com.myagentos.app.domain.model.BrowserHistoryItem
import com.myagentos.app.domain.model.BrowserBookmark
import com.myagentos.app.domain.model.BrowserTab
import com.myagentos.app.domain.model.ChatMessage

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.myagentos.app.*
import com.myagentos.app.presentation.adapter.SimpleChatAdapter
import com.myagentos.app.domain.repository.BrowserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BrowserUIManager - Manages browser UI and WebView functionality
 * 
 * Responsibilities:
 * - Browser show/hide with UI transitions
 * - WebView configuration and touch listeners
 * - Browser menu (new tab, bookmarks, history, settings)
 * - Tab switcher
 * - URL detection and navigation
 * - Page content extraction
 * - Chat with webpage functionality
 * 
 * Extracted from MainActivity to reduce complexity
 */
class BrowserUIManager(
    private val context: Context,
    private val webView: android.webkit.WebView,
    private val browserMenuButton: ImageButton,
    private val tabManagerButton: ImageButton,
    private val closeChatButton: ImageButton,
    private val messageInput: EditText,
    private val sendButton: ImageButton,
    private val swipeRefreshLayout: SwipeRefreshLayout,
    private val chatRecyclerView: androidx.recyclerview.widget.RecyclerView,
    private val suggestionsScrollView: android.widget.HorizontalScrollView,
    private var browserRepository: BrowserRepository
) {
    
    // State
    private var isBrowserVisible = false
    private var isInChatMode = false
    private var currentPageUrl: String? = null
    private var webViewStartY = 0f
    private var webViewStartScrollY = 0
    
    // Callbacks
    private var onPageContentExtracted: ((String, (String) -> Unit) -> Unit)? = null
    private var chatAdapter: SimpleChatAdapter? = null
    private var conversationRepository: Any? = null // Using Any to avoid type issues during extraction
    private var aiRepository: Any? = null // Using Any to avoid type issues during extraction
    private var onHideKeyboard: (() -> Unit)? = null
    private var trayManager: TrayManager? = null
    
    /**
     * Initialize browser
     */
    fun setup() {
        // Configure the initial WebView
        configureWebView(webView)
        
        // Browser menu button click listener
        browserMenuButton.setOnClickListener {
            showBrowserMenu()
        }
        
        // Tab manager button click listener
        tabManagerButton.setOnClickListener {
            showTabSwitcher()
        }
        
        // Add touch listener to WebView for pull-down gesture
        setupWebViewTouchListener()
        
        android.util.Log.d("BrowserUIManager", "Browser initialized")
    }
    
    /**
     * Setup WebView touch listener for pull-to-reveal tray
     */
    private fun setupWebViewTouchListener() {
        webView.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    webViewStartY = event.rawY
                    webViewStartScrollY = webView.scrollY
                    false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (webView.scrollY == 0 && webViewStartScrollY == 0) {
                        val deltaY = event.rawY - webViewStartY
                        if (trayManager?.handleWebViewPullDown(webView.scrollY, webViewStartScrollY, deltaY) == true) {
                            return@setOnTouchListener true
                        }
                    }
                    false
                }
                else -> false
            }
        }
    }
    
    /**
     * Show browser
     */
    fun show() {
        isBrowserVisible = true
        
        // Hide chat screen
        swipeRefreshLayout.visibility = View.GONE
        suggestionsScrollView.visibility = View.GONE
        
        // Show browser
        webView.visibility = View.VISIBLE
        
        // Show browser menu button and tab manager button
        browserMenuButton.visibility = View.VISIBLE
        tabManagerButton.visibility = View.VISIBLE
        
        // Make all elements transparent and subtle
        sendButton.background = context.getDrawable(R.drawable.pill_background_compact)
        sendButton.setColorFilter(android.graphics.Color.WHITE)
        
        browserMenuButton.background = context.getDrawable(R.drawable.pill_background_compact)
        browserMenuButton.setColorFilter(android.graphics.Color.WHITE)
        
        tabManagerButton.background = context.getDrawable(R.drawable.pill_background_compact)
        tabManagerButton.setColorFilter(android.graphics.Color.WHITE)
        
        // Make input field transparent and text whiter
        messageInput.background = context.getDrawable(R.drawable.pill_background_compact)
        messageInput.setTextColor(android.graphics.Color.WHITE)
        messageInput.setHintTextColor(android.graphics.Color.parseColor("#CCCCCC"))
        
        // Make input layout transparent
        (messageInput.parent as? LinearLayout)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        // Create initial tab if none exists
        // Note: Tab management simplified for now - handled in MainActivity
        try {
            val repo = browserRepository
            if (repo.getAllTabs().isEmpty()) {
                repo.createNewTab()
                configureWebView(webView)
            }
        } catch (e: Exception) {
            android.util.Log.e("BrowserUIManager", "Error managing tabs: ${e.message}")
        }
        
        // Load default page if webview is empty
        if (webView.url == null || webView.url == "about:blank") {
            android.util.Log.d("BrowserUIManager", "Loading default page: https://www.google.com")
            webView.loadUrl("https://www.google.com")
        } else {
            android.util.Log.d("BrowserUIManager", "WebView already has URL: ${webView.url}")
        }
        
        // Update input hint for URL navigation and make it single line
        messageInput.hint = "Enter URL or type message..."
        messageInput.textSize = 14f
        messageInput.maxLines = 1
        messageInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        messageInput.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
        
        // Hide keyboard
        onHideKeyboard?.invoke()
        
        android.util.Log.d("BrowserUIManager", "Browser shown")
    }
    
    /**
     * Hide browser
     */
    fun hide() {
        android.util.Log.d("BrowserUIManager", "hide() called, isInChatMode=$isInChatMode")
        
        try {
            isBrowserVisible = false
            
            // Hide close chat button
            closeChatButton.visibility = View.GONE
            
            // Exit chat mode if active
            if (isInChatMode) {
                isInChatMode = false
                currentPageUrl = null
                
                // Restore WebView to normal (hidden) position
                val webViewParams = webView.layoutParams as ConstraintLayout.LayoutParams
                webViewParams.height = 0
                webViewParams.topToBottom = R.id.hiddenTray
                webViewParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                webViewParams.topToTop = ConstraintLayout.LayoutParams.UNSET
                webViewParams.bottomToTop = ConstraintLayout.LayoutParams.UNSET
                webViewParams.matchConstraintPercentHeight = 1.0f
                webView.layoutParams = webViewParams
                
                // Restore chat screen to normal position
                val chatParams = swipeRefreshLayout.layoutParams as ConstraintLayout.LayoutParams
                chatParams.topToTop = ConstraintLayout.LayoutParams.UNSET
                chatParams.topToBottom = R.id.hiddenTray
                chatParams.bottomToTop = R.id.inputLayout
                chatParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                chatParams.matchConstraintPercentHeight = 1.0f
                swipeRefreshLayout.layoutParams = chatParams
                
                // Remove click listener from WebView
                webView.setOnClickListener(null)
                
                android.util.Log.d("BrowserUIManager", "Chat mode cleanup completed")
            }
        } catch (e: Exception) {
            android.util.Log.e("BrowserUIManager", "Error in hide chat mode cleanup: ${e.message}", e)
        }
        
        try {
            // Show chat screen
            swipeRefreshLayout.visibility = View.VISIBLE
            
            // Hide browser
            webView.visibility = View.GONE
            
            // Hide browser menu button and tab manager button
            browserMenuButton.visibility = View.GONE
            tabManagerButton.visibility = View.GONE
            
            // Restore all elements to original appearance
            sendButton.background = context.getDrawable(R.drawable.send_button_background)
            sendButton.clearColorFilter()
            
            browserMenuButton.background = context.getDrawable(R.drawable.pill_background_compact)
            browserMenuButton.clearColorFilter()
            
            tabManagerButton.background = context.getDrawable(R.drawable.pill_background_compact)
            tabManagerButton.clearColorFilter()
            
            // Restore input field original background and text colors
            messageInput.background = context.getDrawable(R.drawable.input_background)
            messageInput.setTextColor(ContextCompat.getColor(context, android.R.color.primary_text_light))
            messageInput.setHintTextColor(ContextCompat.getColor(context, android.R.color.secondary_text_light))
            
            // Restore input layout background
            (messageInput.parent as? LinearLayout)?.background = ContextCompat.getDrawable(context, android.R.attr.colorBackground)
            
            // Reset input hint and restore multi-line behavior
            messageInput.hint = "Search apps or ask a question"
            messageInput.textSize = 16f
            messageInput.maxLines = 3
            messageInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            messageInput.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            
            android.util.Log.d("BrowserUIManager", "Browser hidden successfully")
        } catch (e: Exception) {
            android.util.Log.e("BrowserUIManager", "Error in hide UI restoration: ${e.message}", e)
        }
    }
    
    /**
     * Check if text is a URL
     */
    fun isUrl(text: String): Boolean {
        val trimmed = text.trim().lowercase()
        
        // Common TLDs - comprehensive list
        val tlds = listOf(
            // Generic TLDs
            ".com", ".org", ".net", ".edu", ".gov", ".mil", ".int",
            // Country code TLDs (popular ones)
            ".us", ".uk", ".ca", ".au", ".de", ".fr", ".it", ".es", ".nl", ".be", ".ch", ".at",
            ".se", ".no", ".dk", ".fi", ".pl", ".cz", ".ru", ".cn", ".jp", ".kr", ".in", ".br",
            ".mx", ".ar", ".cl", ".co", ".za", ".eg", ".ng", ".ke", ".il", ".sa", ".ae", ".tr",
            ".gr", ".pt", ".ie", ".nz", ".sg", ".my", ".th", ".vn", ".ph", ".id", ".pk", ".bd",
            // New generic TLDs
            ".ai", ".io", ".app", ".dev", ".tech", ".online", ".site", ".website", ".store",
            ".shop", ".blog", ".news", ".media", ".tv", ".video", ".music", ".game", ".games",
            ".social", ".email", ".cloud", ".digital", ".software", ".download", ".link",
            ".xyz", ".top", ".win", ".bid", ".trade", ".webcam", ".date", ".review", ".faith",
            ".science", ".party", ".accountant", ".loan", ".men", ".click", ".racing", ".cricket"
        )
        
        val containsTld = tlds.any { trimmed.contains(it) }
        val ipPattern = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?(/.*)?$""")
        val isIpAddress = ipPattern.matches(trimmed)
        val isLocalhost = trimmed.startsWith("localhost")
        val hasProtocol = trimmed.startsWith("http://") || trimmed.startsWith("https://") || 
                         trimmed.startsWith("ftp://") || trimmed.startsWith("file://")
        
        return containsTld || isIpAddress || isLocalhost || hasProtocol
    }
    
    /**
     * Load URL in browser
     */
    fun loadUrl(url: String) {
        var finalUrl = url.trim()
        
        if (finalUrl.isEmpty()) {
            android.util.Log.d("BrowserUIManager", "Empty URL provided")
            return
        }
        
        // Add https:// if no protocol specified
        if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
            finalUrl = "https://$finalUrl"
        }
        
        android.util.Log.d("BrowserUIManager", "Loading URL: $finalUrl")
        webView.loadUrl(finalUrl)
        
        // Hide keyboard after loading URL
        onHideKeyboard?.invoke()
    }
    
    /**
     * Check if browser is visible
     */
    fun isVisible(): Boolean = isBrowserVisible
    
    /**
     * Check if in chat mode (split screen)
     */
    fun isInChatMode(): Boolean = isInChatMode
    
    /**
     * Can WebView go back?
     */
    fun canGoBack(): Boolean = webView.canGoBack()
    
    /**
     * Navigate back in WebView
     */
    fun goBack() {
        webView.goBack()
    }
    
    /**
     * Configure WebView settings
     */
    private fun configureWebView(webView: android.webkit.WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        
        // Set WebView client
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false
            }
            
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { 
                    val title = view?.title ?: ""
                    try {
                        val repo = browserRepository
                        repo.addToHistory(title, it)
                    } catch (e: Exception) {
                        android.util.Log.e("BrowserUIManager", "Error adding to history: ${e.message}")
                    }
                    
                    // If in chat mode and page changed, notify user
                    if (isInChatMode && currentPageUrl != null && currentPageUrl != url) {
                        android.util.Log.d("BrowserUIManager", "Page changed in chat mode: $currentPageUrl -> $url")
                        currentPageUrl = url
                        
                        // Show a message that the page changed (via callback)
                        chatAdapter?.let { adapter ->
                            val notificationMessage = ChatMessage(
                                "📄 Page changed to: ${view?.title ?: url}\n\nYou can continue asking questions about the new page.",
                                isUser = false
                            )
                            adapter.addMessage(notificationMessage)
                            chatRecyclerView.postDelayed({
                                chatRecyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                            }, 50)
                        }
                    }
                }
            }
        }
        
        // Set WebChrome client
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onReceivedTitle(view: android.webkit.WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                // Update tab title
                try {
                    val repo = browserRepository
                    repo.getCurrentTab()?.let { tab ->
                        val updatedTab = tab.copy(title = title ?: "")
                        val index = repo.getAllTabs().indexOfFirst { it.id == tab.id }
                        if (index >= 0) {
                            repo.getAllTabs().toMutableList()[index] = updatedTab
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BrowserUIManager", "Error updating tab title: ${e.message}")
                }
            }
        }
        
        // Set download listener
        webView.setDownloadListener { url, _, _, _, _ ->
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            context.startActivity(intent)
        }
    }
    
    /**
     * Show browser menu
     */
    private fun showBrowserMenu() {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_browser_menu, null)
        dialog.setContentView(view)
        dialog.show()
        
        view.findViewById<LinearLayout>(R.id.newTabOption).setOnClickListener {
            dialog.dismiss()
            createNewTab()
        }
        
        view.findViewById<LinearLayout>(R.id.newIncognitoTabOption).setOnClickListener {
            dialog.dismiss()
            createNewIncognitoTab()
        }
        
        view.findViewById<LinearLayout>(R.id.bookmarksOption).setOnClickListener {
            dialog.dismiss()
            showBookmarks()
        }
        
        view.findViewById<LinearLayout>(R.id.historyOption).setOnClickListener {
            dialog.dismiss()
            showHistory()
        }
        
        view.findViewById<LinearLayout>(R.id.downloadsOption).setOnClickListener {
            dialog.dismiss()
            showDownloads()
        }
        
        view.findViewById<LinearLayout>(R.id.settingsOption).setOnClickListener {
            dialog.dismiss()
            showBrowserSettings()
        }
        
        view.findViewById<LinearLayout>(R.id.closeBrowserOption).setOnClickListener {
            dialog.dismiss()
            hide()
        }
    }
    
    /**
     * Create new tab
     */
    private fun createNewTab() {
        try {
            val repo = browserRepository
            repo.createNewTab()
            configureWebView(webView)
            webView.loadUrl("https://www.google.com")
            Toast.makeText(context, "New tab created", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("BrowserUIManager", "Error creating tab: ${e.message}")
        }
    }
    
    /**
     * Create new incognito tab
     */
    private fun createNewIncognitoTab() {
        try {
            val repo = browserRepository
            repo.createNewTab(isIncognito = true)
            configureWebView(webView)
            webView.loadUrl("https://www.google.com")
            Toast.makeText(context, "New incognito tab created", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("BrowserUIManager", "Error creating incognito tab: ${e.message}")
        }
    }
    
    /**
     * Show bookmarks dialog
     */
    private fun showBookmarks() {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_bookmarks, null)
        dialog.setContentView(view)
        dialog.show()
        
        val bookmarksList = view.findViewById<LinearLayout>(R.id.bookmarksList)
        
        view.findViewById<LinearLayout>(R.id.addBookmarkButton).setOnClickListener {
            val currentUrl = webView.url
            val currentTitle = webView.title ?: "Untitled"
            if (currentUrl != null && currentUrl.isNotEmpty()) {
                try {
                    val repo = browserRepository
                    repo.addBookmark(currentTitle, currentUrl)
                    Toast.makeText(context, "Bookmark added: $currentTitle", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    showBookmarks()
                } catch (e: Exception) {
                    android.util.Log.e("BrowserUIManager", "Error adding bookmark: ${e.message}")
                }
            } else {
                Toast.makeText(context, "No page to bookmark", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Populate bookmarks list
        try {
            val repo = browserRepository
            val bookmarks = repo.getAllBookmarks()
            if (bookmarks.isEmpty()) {
                val emptyText = TextView(context)
                emptyText.text = "No bookmarks yet"
                emptyText.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                emptyText.textSize = 16f
                emptyText.gravity = android.view.Gravity.CENTER
                emptyText.setPadding(16, 32, 16, 32)
                bookmarksList.addView(emptyText)
            } else {
                bookmarks.forEach { bookmark ->
                    val bookmarkItem = createBookmarkItem(bookmark, dialog)
                    bookmarksList.addView(bookmarkItem)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BrowserUIManager", "Error loading bookmarks: ${e.message}")
        }
        
        view.findViewById<Button>(R.id.closeBookmarksButton).setOnClickListener {
            dialog.dismiss()
        }
    }
    
    /**
     * Show history dialog
     */
    private fun showHistory() {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_history, null)
        dialog.setContentView(view)
        dialog.show()
        
        val historyList = view.findViewById<LinearLayout>(R.id.historyList)
        
        view.findViewById<LinearLayout>(R.id.clearHistoryButton).setOnClickListener {
            try {
                val repo = browserRepository
                repo.clearHistory()
                Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } catch (e: Exception) {
                android.util.Log.e("BrowserUIManager", "Error clearing history: ${e.message}")
            }
        }
        
        // Populate history list
        try {
            val repo = browserRepository
            val history = repo.getAllHistory().take(20)
            if (history.isEmpty()) {
                val emptyText = TextView(context)
                emptyText.text = "No history yet"
                emptyText.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                emptyText.textSize = 16f
                emptyText.gravity = android.view.Gravity.CENTER
                emptyText.setPadding(16, 32, 16, 32)
                historyList.addView(emptyText)
            } else {
                history.forEach { historyItem ->
                    val historyItemView = createHistoryItem(historyItem, dialog)
                    historyList.addView(historyItemView)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BrowserUIManager", "Error loading history: ${e.message}")
        }
        
        view.findViewById<Button>(R.id.closeHistoryButton).setOnClickListener {
            dialog.dismiss()
        }
    }
    
    /**
     * Show downloads dialog
     */
    private fun showDownloads() {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_downloads, null)
        dialog.setContentView(view)
        dialog.show()
        
        val downloadsList = view.findViewById<LinearLayout>(R.id.downloadsList)
        
        val emptyText = TextView(context)
        emptyText.text = "No downloads yet\n\nDownloads will appear here when you download files from websites."
        emptyText.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        emptyText.textSize = 16f
        emptyText.gravity = android.view.Gravity.CENTER
        emptyText.setPadding(16, 32, 16, 32)
        downloadsList.addView(emptyText)
        
        view.findViewById<Button>(R.id.closeDownloadsButton).setOnClickListener {
            dialog.dismiss()
        }
    }
    
    /**
     * Show browser settings dialog
     */
    private fun showBrowserSettings() {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_browser_settings, null)
        dialog.setContentView(view)
        dialog.show()
        
        val javascriptSwitch = view.findViewById<Switch>(R.id.javascriptSwitch)
        val zoomControlsSwitch = view.findViewById<Switch>(R.id.zoomControlsSwitch)
        
        javascriptSwitch.setOnCheckedChangeListener { _, isChecked ->
            webView.settings.javaScriptEnabled = isChecked
            Toast.makeText(context, "JavaScript ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }
        
        zoomControlsSwitch.setOnCheckedChangeListener { _, isChecked ->
            webView.settings.displayZoomControls = isChecked
            Toast.makeText(context, "Zoom controls ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }
        
        view.findViewById<LinearLayout>(R.id.clearDataButton).setOnClickListener {
            webView.clearCache(true)
            webView.clearHistory()
            try {
                val repo = browserRepository
                repo.clearHistory()
            } catch (e: Exception) {
                android.util.Log.e("BrowserUIManager", "Error clearing history: ${e.message}")
            }
            Toast.makeText(context, "Browser data cleared", Toast.LENGTH_SHORT).show()
        }
        
        view.findViewById<Button>(R.id.closeSettingsButton).setOnClickListener {
            dialog.dismiss()
        }
    }
    
    /**
     * Show tab switcher dialog
     */
    private fun showTabSwitcher() {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_tab_switcher, null)
        dialog.setContentView(view)
        dialog.show()
        
        val tabsList = view.findViewById<LinearLayout>(R.id.tabsList)
        
        view.findViewById<LinearLayout>(R.id.newTabFromSwitcherButton).setOnClickListener {
            dialog.dismiss()
            createNewTab()
        }
        
        // Populate tabs list
        try {
            val repo = browserRepository
            val tabs = repo.getAllTabs()
            if (tabs.isEmpty()) {
                val emptyText = TextView(context)
                emptyText.text = "No tabs open"
                emptyText.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                emptyText.textSize = 16f
                emptyText.gravity = android.view.Gravity.CENTER
                emptyText.setPadding(16, 32, 16, 32)
                tabsList.addView(emptyText)
            } else {
                tabs.forEach { tab ->
                    val tabItem = createTabItem(tab, dialog)
                    tabsList.addView(tabItem)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BrowserUIManager", "Error loading tabs: ${e.message}")
        }
        
        view.findViewById<Button>(R.id.closeTabSwitcherButton).setOnClickListener {
            dialog.dismiss()
        }
    }
    
    /**
     * Create bookmark list item
     */
    private fun createBookmarkItem(bookmark: BrowserBookmark, dialog: Dialog): LinearLayout {
        val item = LinearLayout(context)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(16, 12, 16, 12)
        item.background = context.getDrawable(R.drawable.menu_item_background)
        
        val titleText = TextView(context)
        titleText.text = bookmark.title
        titleText.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        titleText.textSize = 16f
        titleText.setTypeface(null, android.graphics.Typeface.BOLD)
        
        val urlText = TextView(context)
        urlText.text = bookmark.url
        urlText.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        urlText.textSize = 14f
        
        item.addView(titleText)
        item.addView(urlText)
        
        item.setOnClickListener {
            webView.loadUrl(bookmark.url)
            dialog.dismiss()
        }
        
        return item
    }
    
    /**
     * Create history list item
     */
    private fun createHistoryItem(historyItem: BrowserHistoryItem, dialog: Dialog): LinearLayout {
        val item = LinearLayout(context)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(16, 12, 16, 12)
        item.background = context.getDrawable(R.drawable.menu_item_background)
        
        val titleText = TextView(context)
        titleText.text = historyItem.title
        titleText.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        titleText.textSize = 16f
        titleText.setTypeface(null, android.graphics.Typeface.BOLD)
        
        val urlText = TextView(context)
        urlText.text = historyItem.url
        urlText.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        urlText.textSize = 14f
        
        val dateText = TextView(context)
        val date = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        dateText.text = date.format(java.util.Date(historyItem.dateVisited))
        dateText.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        dateText.textSize = 12f
        
        item.addView(titleText)
        item.addView(urlText)
        item.addView(dateText)
        
        item.setOnClickListener {
            webView.loadUrl(historyItem.url)
            dialog.dismiss()
        }
        
        return item
    }
    
    /**
     * Create tab list item
     */
    private fun createTabItem(tab: BrowserTab, dialog: Dialog): LinearLayout {
        val item = LinearLayout(context)
        item.orientation = LinearLayout.HORIZONTAL
        item.setPadding(16, 12, 16, 12)
        item.background = context.getDrawable(R.drawable.menu_item_background)
        
        val contentLayout = LinearLayout(context)
        contentLayout.orientation = LinearLayout.VERTICAL
        contentLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        
        val titleText = TextView(context)
        titleText.text = if (tab.title.isNotEmpty()) tab.title else "New Tab"
        titleText.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        titleText.textSize = 16f
        titleText.setTypeface(null, android.graphics.Typeface.BOLD)
        
        val urlText = TextView(context)
        urlText.text = if (tab.url.isNotEmpty()) tab.url else "about:blank"
        urlText.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        urlText.textSize = 14f
        
        contentLayout.addView(titleText)
        contentLayout.addView(urlText)
        
        if (tab.isIncognito) {
            val incognitoText = TextView(context)
            incognitoText.text = "🔒 Incognito"
            incognitoText.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_light))
            incognitoText.textSize = 12f
            contentLayout.addView(incognitoText)
        }
        
        // Close tab button
        val closeButton = ImageButton(context)
        closeButton.layoutParams = LinearLayout.LayoutParams(40, 40)
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        closeButton.setColorFilter(android.graphics.Color.WHITE)
        closeButton.background = context.getDrawable(R.drawable.pill_background_compact)
        closeButton.setOnClickListener {
            try {
                val repo = browserRepository
                repo.closeTab(tab.id)
                dialog.dismiss()
                showTabSwitcher()
            } catch (e: Exception) {
                android.util.Log.e("BrowserUIManager", "Error closing tab: ${e.message}")
            }
        }
        
        item.addView(contentLayout)
        item.addView(closeButton)
        
        if (tab.isActive) {
            item.background = context.getDrawable(R.drawable.menu_item_background)
            item.setPadding(16, 12, 16, 12)
        }
        
        item.setOnClickListener {
            try {
                val repo = browserRepository
                repo.switchToTab(tab.id)
                dialog.dismiss()
            } catch (e: Exception) {
                android.util.Log.e("BrowserUIManager", "Error switching tab: ${e.message}")
            }
        }
        
        return item
    }
    
    /**
     * Set callbacks
     */
    fun setCallbacks(
        chatAdapter: SimpleChatAdapter,
        conversationRepository: Any,
        aiRepository: Any,
        onHideKeyboard: () -> Unit,
        trayManager: TrayManager
    ) {
        this.chatAdapter = chatAdapter
        this.conversationRepository = conversationRepository
        this.aiRepository = aiRepository
        this.onHideKeyboard = onHideKeyboard
        this.trayManager = trayManager
    }
    
    /**
     * Extract page content and chat with webpage
     */
    fun chatWithPage(userQuery: String, onComplete: (String, String, Long, Int, Long) -> Unit) {
        val totalStartTime = System.currentTimeMillis()
        
        // Enter chat mode
        isInChatMode = true
        currentPageUrl = webView.url
        
        // Shrink WebView to thumbnail
        shrinkWebViewToThumbnail()
        
        // Extract page content
        extractPageContent { pageContent ->
            val extractionTime = System.currentTimeMillis() - totalStartTime
            
            // Prepare context for Grok
            val contextMessage = """
                I'm viewing a webpage. Here's the content:
                
                $pageContent
                
                User question: $userQuery
            """.trimIndent()
            
            // Return to callback for AI processing
            onComplete(contextMessage, pageContent, extractionTime, pageContent.length, totalStartTime)
        }
        
        onHideKeyboard?.invoke()
    }
    
    /**
     * Extract page content via JavaScript
     */
    private fun extractPageContent(callback: (String) -> Unit) {
        val startTime = System.currentTimeMillis()
        
        val javascript = """
            (function() {
                let content = '';
                
                content += 'Page Title: ' + document.title + '\\n\\n';
                
                const metaDesc = document.querySelector('meta[name="description"]');
                if (metaDesc) {
                    content += 'Description: ' + metaDesc.content + '\\n\\n';
                }
                
                const mainSelectors = ['main', 'article', '[role="main"]', '.main-content', '#main-content', '.content', '#content'];
                let mainContent = null;
                
                for (let selector of mainSelectors) {
                    mainContent = document.querySelector(selector);
                    if (mainContent) break;
                }
                
                if (!mainContent) {
                    mainContent = document.body;
                }
                
                const headings = mainContent.querySelectorAll('h1, h2, h3, h4, h5, h6');
                headings.forEach(h => {
                    content += h.textContent.trim() + '\\n';
                });
                
                const paragraphs = mainContent.querySelectorAll('p');
                paragraphs.forEach(p => {
                    const text = p.textContent.trim();
                    if (text.length > 0) {
                        content += text + '\\n\\n';
                    }
                });
                
                const lists = mainContent.querySelectorAll('li');
                lists.forEach(li => {
                    const text = li.textContent.trim();
                    if (text.length > 0) {
                        content += '• ' + text + '\\n';
                    }
                });
                
                if (content.length > 16000) {
                    content = content.substring(0, 16000) + '...\\n\\n[Content truncated due to length]';
                }
                
                return content;
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(javascript) { result ->
            val extractionTime = System.currentTimeMillis() - startTime
            val cleanedResult = result?.trim('"')?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""
            android.util.Log.d("BrowserUIManager", "Extracted content length: ${cleanedResult.length} characters (${extractionTime}ms)")
            callback(cleanedResult)
        }
    }
    
    /**
     * Shrink WebView to thumbnail (50/50 split with chat)
     */
    private fun shrinkWebViewToThumbnail() {
        val layoutParams = webView.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.height = 0
        layoutParams.topToBottom = R.id.hiddenTray
        layoutParams.topToTop = ConstraintLayout.LayoutParams.UNSET
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        layoutParams.bottomToTop = R.id.swipeRefreshLayout
        layoutParams.matchConstraintPercentHeight = 0.5f
        webView.layoutParams = layoutParams
        
        val chatLayoutParams = swipeRefreshLayout.layoutParams as ConstraintLayout.LayoutParams
        chatLayoutParams.topToBottom = R.id.webView
        chatLayoutParams.topToTop = ConstraintLayout.LayoutParams.UNSET
        chatLayoutParams.bottomToTop = R.id.inputLayout
        chatLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        chatLayoutParams.matchConstraintPercentHeight = 0.5f
        swipeRefreshLayout.layoutParams = chatLayoutParams
        
        swipeRefreshLayout.visibility = View.VISIBLE
        closeChatButton.visibility = View.VISIBLE
        closeChatButton.setOnClickListener {
            expandWebViewToFullSize()
        }
        
        webView.setOnClickListener {
            expandWebViewToFullSize()
        }
        
        android.util.Log.d("BrowserUIManager", "WebView set to top 50%, chat to bottom 50%")
    }
    
    /**
     * Expand WebView to full size
     */
    private fun expandWebViewToFullSize() {
        isInChatMode = false
        currentPageUrl = null
        
        closeChatButton.visibility = View.GONE
        
        val layoutParams = webView.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.height = 0
        layoutParams.topToBottom = R.id.hiddenTray
        layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.topToTop = ConstraintLayout.LayoutParams.UNSET
        layoutParams.matchConstraintPercentHeight = 1.0f
        webView.layoutParams = layoutParams
        
        val chatLayoutParams = swipeRefreshLayout.layoutParams as ConstraintLayout.LayoutParams
        chatLayoutParams.bottomToTop = R.id.inputLayout
        chatLayoutParams.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        chatLayoutParams.matchConstraintPercentHeight = 1.0f
        swipeRefreshLayout.layoutParams = chatLayoutParams
        
        swipeRefreshLayout.visibility = View.GONE
        webView.setOnClickListener(null)
        
        android.util.Log.d("BrowserUIManager", "WebView expanded to full size, exited chat mode")
    }
}

