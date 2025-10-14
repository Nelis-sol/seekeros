package com.myagentos.app

import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var suggestionsLayout: android.widget.LinearLayout
    private lateinit var suggestionsScrollView: android.widget.HorizontalScrollView
    private lateinit var chatAdapter: SimpleChatAdapter
    private lateinit var historyButton: ImageButton
    private lateinit var newChatButton: ImageButton
    private lateinit var addAgentButton: ImageButton
    private lateinit var widgetDisplayArea: android.widget.ScrollView
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var hiddenTray: android.widget.LinearLayout
    private lateinit var rightTray: android.widget.LinearLayout
    // Job Grid related variables
    private lateinit var jobGridOverlay: View
    private val jobs = mutableListOf<Job>()
    
    // App Embedding variables
    private var embeddedAppContainer: FrameLayout? = null
    private var isAppEmbedded = false
    private var embeddedAppPackage: String? = null
    private lateinit var modelManager: ModelManager
    private lateinit var externalAIService: ExternalAIService
    private lateinit var appManager: AppManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var conversationManager: ConversationManager
    private var selectedModel: ModelType? = null
    private var installedApps: List<AppInfo> = emptyList()
    private var isUpdatingPills = false // Flag to prevent infinite loops
    private var hasShownPermissionDialog = false // Flag to show permission dialog only once per session
    
    // Browser-related variables
    private lateinit var webView: android.webkit.WebView
    private lateinit var browserMenuButton: ImageButton
    private lateinit var tabManagerButton: ImageButton
    private lateinit var closeChatButton: ImageButton
    private var isBrowserVisible = false
    private var isInChatMode = false // Track if we're chatting with a page
    private var currentPageUrl: String? = null // Track current page for context updates
    private lateinit var browserManager: BrowserManager
    
    // Right tray state variables (declared early for dispatchTouchEvent)
    private var isTrayVisible = false
    @Volatile private var isRightTrayVisible = false
    private var startY = 0f
    private var startX = 0f
    private var isTracking = false
    private var isTrackingRight = false
    private var isKeyboardVisible = true // Initially true since input is auto-focused
    private var wasKeyboardVisibleBeforeRightTray = false // Track keyboard state before opening right tray

    // Double-tap detection for job grid
    private var lastTapTime = 0L
    private var tapCount = 0
    private var isJobGridVisible = false

    companion object {
        private var savedMessages: MutableList<ChatMessage>? = null
    }
    
    // Override dispatchTouchEvent to intercept right-edge gestures before system
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val screenWidth = resources.displayMetrics.widthPixels
        val edgeThreshold = 60 * resources.displayMetrics.density
        val isNearRightEdge = event.rawX > (screenWidth - edgeThreshold)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Handle double-tap detection first
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < 300) { // 300ms double-tap window
                    tapCount++
                    if (tapCount == 2) {
                        // Double tap detected
                        android.util.Log.d("JobGrid", "DOUBLE TAP DETECTED at (${event.x}, ${event.y})")
                        showJobGrid()
                        tapCount = 0
                        return true // Consume the event
                    }
                } else {
                    tapCount = 1
                }
                lastTapTime = currentTime
                
                if (isNearRightEdge || isRightTrayVisible) {
                    startX = event.rawX
                    isTrackingRight = true
                    android.util.Log.d("RightEdgeIntercept", "ACTION_DOWN at x=${event.rawX}, screenWidth=$screenWidth, nearEdge=$isNearRightEdge, trayVisible=$isRightTrayVisible")
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTrackingRight) {
                    val deltaX = event.rawX - startX
                    android.util.Log.d("RightEdgeIntercept", "ACTION_MOVE deltaX=$deltaX")
                    
                    // Swipe left from right edge to open
                    if (!isRightTrayVisible && isNearRightEdge && deltaX < -100) {
                        android.util.Log.d("RightEdgeIntercept", "LEFT SWIPE DETECTED! Opening tray - CONSUMING EVENT")
                        showRightTray()
                        isTrackingRight = false
                        // Send a CANCEL event to clear any pending touch state
                        val cancelEvent = MotionEvent.obtain(event)
                        cancelEvent.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancelEvent)
                        cancelEvent.recycle()
                        return true // Consume event, don't let system handle it
                    }
                    // Swipe right to close
                    else if (isRightTrayVisible && deltaX > 100) {
                        android.util.Log.d("RightEdgeIntercept", "RIGHT SWIPE DETECTED! Closing tray - CONSUMING EVENT")
                        hideRightTray()
                        isTrackingRight = false
                        // Send a CANCEL event to clear any pending touch state
                        val cancelEvent = MotionEvent.obtain(event)
                        cancelEvent.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancelEvent)
                        cancelEvent.recycle()
                        return true // Consume event
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isTrackingRight) {
                    android.util.Log.d("RightEdgeIntercept", "Touch ended, resetting tracking")
                    isTrackingRight = false
                }
            }
        }
        
        // Let normal event handling continue
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if we should show as transparent overlay
        val showTransparent = intent.getBooleanExtra("SHOW_TRANSPARENT", false)
        
        if (showTransparent) {
            // Use transparent overlay theme
            setTheme(R.style.Theme_AgentOS_Overlay)
        } else {
            // Use normal dark theme
        setTheme(R.style.Theme_AgentOS_Dark)
        }
        
        setContentView(R.layout.activity_main)

        if (!showTransparent) {
            // Force dark background only for normal mode
        val rootView = findViewById<android.view.View>(android.R.id.content)
        rootView.setBackgroundColor(android.graphics.Color.BLACK)
        }
        
        // Check for intents from floating overlay
        handleOverlayIntent(intent)
        
        // CRITICAL: Disable system gesture navigation on edges so we can handle right-edge swipes
        // This prevents the Android back/forward gesture arrow from interfering
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        
        // Request exclusive gesture control for right edge
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.decorView.setOnApplyWindowInsetsListener { view, insets ->
                // Get the system gesture insets (areas where system gestures are active)
                val gestureInsets = insets.systemGestureInsets
                android.util.Log.e("GestureControl", "System gesture insets: left=${gestureInsets.left}, right=${gestureInsets.right}")
                
                // Create exclusion rects to tell system "don't handle gestures in these areas"
                val exclusionRects = mutableListOf<android.graphics.Rect>()
                
                // Exclude the entire right edge (60dp width)
                val edgeWidth = (60 * resources.displayMetrics.density).toInt()
                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels
                
                val rightEdgeRect = android.graphics.Rect(
                    screenWidth - edgeWidth,  // left
                    0,                         // top
                    screenWidth,               // right
                    screenHeight               // bottom
                )
                exclusionRects.add(rightEdgeRect)
                
                view.systemGestureExclusionRects = exclusionRects
                android.util.Log.e("GestureControl", "Set gesture exclusion rect: $rightEdgeRect")
                
                insets
            }
        }

        modelManager = ModelManager(this)
        externalAIService = ExternalAIService()
        appManager = AppManager(this)
        usageStatsManager = UsageStatsManager(this)
        conversationManager = ConversationManager(this)

        setupUI()
        checkFirstLaunch()
        showRecentApps()
        
        // Handle intent extras
        handleIntentExtras()
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras()
        
        // Handle overlay intents
        intent?.let { handleOverlayIntent(it) }
    }
    
    override fun onResume() {
        super.onResume()
        // Don't force any keyboard state - let it be natural
        
        // Refresh recent apps when returning to launcher
        showRecentApps()
    }

    private fun setupUI() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        suggestionsLayout = findViewById(R.id.suggestionsLayout)
        suggestionsScrollView = findViewById(R.id.suggestionsScrollView)
        historyButton = findViewById(R.id.historyButton)
        newChatButton = findViewById(R.id.newChatButton)
        addAgentButton = findViewById(R.id.addAgentButton)
        widgetDisplayArea = findViewById(R.id.widgetDisplayArea)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        hiddenTray = findViewById(R.id.hiddenTray)
        rightTray = findViewById(R.id.rightTray)
        val rightEdgeDetector = findViewById<View>(R.id.rightEdgeDetector)
        
        // Initialize radial menu views
        jobGridOverlay = findViewById(R.id.jobGridOverlay)
        embeddedAppContainer = findViewById(R.id.embeddedAppContainer)
        
        // Set up job grid
        setupJobGrid()
        
        // Initialize browser views
        webView = findViewById(R.id.webView)
        browserMenuButton = findViewById(R.id.browserMenuButton)
        tabManagerButton = findViewById(R.id.tabManagerButton)
        closeChatButton = findViewById(R.id.closeChatButton)
        browserManager = BrowserManager(this)
        
        // Set up close chat button click listener
        closeChatButton.setOnClickListener {
            expandWebViewToFullSize()
        }

        // Setup Chat
        val messages = savedMessages ?: mutableListOf()
        chatAdapter = SimpleChatAdapter(messages, true, false) { 
            // Browse pill click callback
            showBrowser()
        }
        
        // Use GridLayoutManager for 1-column cards
        val gridLayoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 1)
        gridLayoutManager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return chatAdapter.getSpanSize(position, 1)
            }
        }
        chatRecyclerView.layoutManager = gridLayoutManager
        chatRecyclerView.adapter = chatAdapter
        
        // Enable nested scrolling for smooth scrolling with SwipeRefreshLayout
        chatRecyclerView.isNestedScrollingEnabled = true
        
        // Show/hide widget display area based on message count
        updateWelcomeAreaVisibility()

        // Don't show welcome message - let user start typing immediately
        // if (messages.isEmpty()) {
        //     updateWelcomeMessage()
        // }

        // Setup send button
        sendButton.setOnClickListener {
            sendMessage()
        }
        
        // Setup history button
        historyButton.setOnClickListener { 
            val intent = Intent(this, ConversationHistoryActivity::class.java)
            startActivity(intent)
        }
        
        // Setup new chat button
        newChatButton.setOnClickListener {
            // Clear current conversation and start fresh
            conversationManager.startNewConversation()
            chatAdapter.clearMessages()
            messageInput.setText("")
            hideKeyboardAndClearFocus()
        }
        
        // Setup add agent button (placeholder for now)
        addAgentButton.setOnClickListener {
            // TODO: Implement add agent functionality
            android.widget.Toast.makeText(this, "Add Agent feature coming soon!", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // Setup tray click to close (fallback method)
        hiddenTray.setOnClickListener {
            if (isTrayVisible) {
                hideTray()
            }
        }
        
        // Setup SwipeRefreshLayout for pull-to-reveal tray
        swipeRefreshLayout.setOnRefreshListener {
            android.util.Log.d("SwipeRefresh", "onRefresh triggered! isTrayVisible=$isTrayVisible")
            // Immediately stop refresh and toggle tray with zero latency
            swipeRefreshLayout.isRefreshing = false
            
            // Only toggle if tray is not already visible (prevent double-trigger)
            if (!isTrayVisible) {
                toggleTray()
            } else {
                android.util.Log.d("SwipeRefresh", "Ignoring onRefresh - tray already visible")
            }
        }
        
        // Enable refresh functionality
        swipeRefreshLayout.isEnabled = true
        
        // Completely disable the refresh indicator for zero latency
        swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.TRANSPARENT)
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(android.graphics.Color.TRANSPARENT)
        swipeRefreshLayout.setSize(androidx.swiperefreshlayout.widget.SwipeRefreshLayout.DEFAULT)
        
        // Set minimal distance to trigger for faster response
        swipeRefreshLayout.setDistanceToTriggerSync(100)
        
        // Add scroll listener to handle tray visibility and pull gestures
        chatRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                android.util.Log.d("RecyclerView", "onScrollStateChanged: $newState")
                updateSwipeRefreshState()
            }
            
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                android.util.Log.d("RecyclerView", "onScrolled: dx=$dx, dy=$dy, canScrollUp=${recyclerView.canScrollVertically(-1)}, canScrollDown=${recyclerView.canScrollVertically(1)}")
            }
        })
        
        // Add touch listener to detect upward swipes to close tray
        setupUpwardSwipeListener()
        setupRightEdgeSwipeListener(rightEdgeDetector)
        
        // Initially hide right tray (set position without animation)
        rightTray.visibility = View.GONE
        isRightTrayVisible = false
        // Set initial translation after layout
        rightTray.post {
            rightTray.translationX = rightTray.width.toFloat()
        }
        
        // Initially hide the tray
        hideTray()
        
        // Log initial state
        android.util.Log.d("MainActivity", "Initial setup complete - isKeyboardVisible: $isKeyboardVisible, itemCount: ${chatAdapter.itemCount}")
        
        // Setup keyboard detection for cards
        setupKeyboardDetection()
        
        // Load installed apps for detection
        loadInstalledApps()
        
        // Setup text change listener for app detection
        setupAppDetection()
        
        // Apply dark theme colors programmatically
        applyDarkColors()
        
        // Auto-focus input field for immediate typing (with delay to ensure it works)
        messageInput.post {
            messageInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(messageInput, InputMethodManager.SHOW_FORCED)
        }

        // Enable clickable spans in the EditText
        messageInput.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        
        // Setup enter key to send
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND || 
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                sendMessage()
                true
            } else {
                false
            }
        }
        
        // Auto-scroll to bottom when keyboard appears
        messageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && chatAdapter.itemCount > 0) {
                chatRecyclerView.postDelayed({
                    chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }, 100)
            }
        }
        
        // Tap chat area to dismiss keyboard (best practice)
        chatRecyclerView.setOnClickListener {
            hideKeyboardAndClearFocus()
        }
        
        // Setup browser
        setupBrowser()
    }

    private fun checkFirstLaunch() {
        if (modelManager.isFirstLaunch()) {
            showModelSelectionDialog()
        } else {
            selectedModel = modelManager.getSelectedModel()
            initializeSelectedModel()
        }
    }


    private fun showModelSelectionDialog() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_model_selection, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.show()

        val externalModelCard = view.findViewById<CardView>(R.id.externalModelCard)

        externalModelCard.setOnClickListener {
            dialog.dismiss()
            selectedModel = ModelType.EXTERNAL_GROK // Use Grok as default
            modelManager.setSelectedModel(ModelType.EXTERNAL_GROK)
            modelManager.setFirstLaunchCompleted()
            initializeSelectedModel()
        }
    }

    private fun initializeSelectedModel() {
        selectedModel = modelManager.getSelectedModel()
        when (selectedModel) {
            ModelType.EXTERNAL_CHATGPT, ModelType.EXTERNAL_GROK -> {
                // No welcome message - let user start typing immediately
            }
            null -> {
                // No model selected, show selection dialog
                showModelSelectionDialog()
            }
        }
    }

    private fun updateWelcomeMessage() {
        val welcomeMessage = when (selectedModel) {
            ModelType.EXTERNAL_CHATGPT -> "Hello! I'm AgentOS, connected to ChatGPT for the best AI experience. How can I help you today?"
            ModelType.EXTERNAL_GROK -> "Hello! I'm AgentOS, powered by Grok AI for the most advanced AI experience. How can I help you today?"
            null -> "Hello! I'm AgentOS. Please select your AI model to get started."
        }
        
        chatAdapter.addMessage(ChatMessage(welcomeMessage, isUser = false))
    }

    private fun sendMessage() {
        val message = messageInput.text.toString().trim()
        if (message.isEmpty()) return

        // If browser is visible, check if it's a URL or chat query
        if (isBrowserVisible) {
            if (isUrl(message)) {
                // Navigate to URL
                loadUrl(message)
                messageInput.text.clear()
                return
            } else {
                // Chat with page content
                chatWithPageContent(message)
                messageInput.text.clear()
                return
            }
        }

        // Add user message to chat
        val userMessage = ChatMessage(message, isUser = true)
        chatAdapter.addMessage(userMessage)
        conversationManager.addMessage(userMessage)
        messageInput.text.clear()
        
        // Hide welcome area when first message is sent
        updateWelcomeAreaVisibility()

        // Hide widget display area when first message is sent

        // Dismiss keyboard after sending (best practice)
        hideKeyboardAndClearFocus()

        // Scroll to bottom with delay to ensure smooth scrolling
        chatRecyclerView.postDelayed({
            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }, 50)

        // Generate AI response
        generateAIResponse(message)
    }

    private fun generateAIResponse(userMessage: String) {
        android.util.Log.e("AI_RESPONSE", "=== generateAIResponse() CALLED ===")
        android.util.Log.e("AI_RESPONSE", "User message: $userMessage")
        android.util.Log.e("AI_RESPONSE", "Selected model: $selectedModel")
        
        CoroutineScope(Dispatchers.IO).launch {
            android.util.Log.e("AI_RESPONSE", "Coroutine started on thread: ${Thread.currentThread().name}")
            try {
                android.util.Log.e("AI_RESPONSE", "About to call API...")
                val aiResponse = when (selectedModel) {
                    ModelType.EXTERNAL_CHATGPT, ModelType.EXTERNAL_GROK -> {
                        externalAIService.generateResponse(userMessage, selectedModel!!)
                    }
                    null -> "Please select an AI model first."
                }
                android.util.Log.e("AI_RESPONSE", "API response received: ${aiResponse.take(100)}...")
                
                withContext(Dispatchers.Main) {
                    android.util.Log.e("AI_RESPONSE", "Switched to Main thread: ${Thread.currentThread().name}")
                    android.util.Log.e("AI_RESPONSE", "Current itemCount BEFORE adding: ${chatAdapter.itemCount}")
                    
                    val aiMessage = ChatMessage(aiResponse, isUser = false)
                    chatAdapter.addMessage(aiMessage)
                    android.util.Log.e("AI_RESPONSE", "addMessage() called, itemCount AFTER: ${chatAdapter.itemCount}")
                    
                    conversationManager.addMessage(aiMessage)
                    conversationManager.saveCurrentConversation()
                    
                    android.util.Log.e("AI_RESPONSE", "Forcing UI update...")
                    android.util.Log.e("AI_RESPONSE", "RecyclerView: width=${chatRecyclerView.width}, height=${chatRecyclerView.height}, visibility=${chatRecyclerView.visibility}, childCount=${chatRecyclerView.childCount}")
                    android.util.Log.e("AI_RESPONSE", "RecyclerView: isAttachedToWindow=${chatRecyclerView.isAttachedToWindow}, isLaidOut=${chatRecyclerView.isLaidOut}")
                    chatRecyclerView.adapter = chatAdapter
                    chatRecyclerView.invalidate()
                    chatRecyclerView.requestLayout()
                    android.util.Log.e("AI_RESPONSE", "UI update forced, posting scroll...")
                    
                    chatRecyclerView.post {
                        android.util.Log.e("AI_RESPONSE", "Scroll posted, itemCount: ${chatAdapter.itemCount}")
                        if (chatAdapter.itemCount > 0) {
                            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                        }
                    }
                    android.util.Log.e("AI_RESPONSE", "=== generateAIResponse() COMPLETED ===")
                }
            } catch (e: Exception) {
                android.util.Log.e("AI_RESPONSE", "ERROR: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    chatAdapter.addMessage(ChatMessage(
                        "Sorry, I encountered an error: ${e.message}",
                        isUser = false
                    ))
                }
            }
        }
    }

    // Load installed apps for detection
    private fun loadInstalledApps() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                installedApps = appManager.getInstalledApps()
                android.util.Log.d("AppDetection", "Loaded ${installedApps.size} apps")
            } catch (e: Exception) {
                android.util.Log.e("AppDetection", "Error loading apps", e)
            }
        }
    }
    
    // Setup app detection on text change
    private fun setupAppDetection() {
        messageInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Skip processing if we're programmatically updating text
                if (isUpdatingPills) return
                
                val text = s?.toString() ?: ""
                
                // Only detect apps after word boundaries (spaces, punctuation, end of text)
                if (shouldTriggerAppDetection(text)) {
                detectAppsInText(text)
                // Apply inline pills with safe flag
                applyInlinePills(text)
                }
            }
        })
    }
    
    // Check if we should trigger app detection based on word boundaries
    private fun shouldTriggerAppDetection(text: String): Boolean {
        if (text.isEmpty()) return true // Always detect when text is cleared
        
        val lastChar = text.lastOrNull()
        
        // Trigger detection after word boundaries:
        // - Space
        // - Punctuation marks (. , ! ? ; : -)
        // - End of text (for the last word)
        return lastChar == null || 
               lastChar == ' ' || 
               lastChar == '.' || 
               lastChar == ',' || 
               lastChar == '!' || 
               lastChar == '?' || 
               lastChar == ';' || 
               lastChar == ':' || 
               lastChar == '-' ||
               lastChar == '\n'
    }
    
    // Apply inline pills to the text input
    private fun applyInlinePills(text: String) {
        if (text.isEmpty() || installedApps.isEmpty() || isUpdatingPills || isBrowserVisible) return
        
        val spannableString = android.text.SpannableString(text)
        val textLower = text.lowercase()
        var hasPills = false
        
        // Find all app names in the text - only match complete words
        for (app in installedApps) {
            val appNameLower = app.appName.lowercase()
            if (appNameLower.length > 2 && isCompleteWordMatch(textLower, appNameLower)) {
                // Find all occurrences of this app name
                var startIndex = 0
                while (true) {
                    val index = textLower.indexOf(appNameLower, startIndex)
                    if (index == -1) break
                    
                    // Double-check this is a complete word match at this position
                    if (!isCompleteWordMatchAtPosition(textLower, appNameLower, index)) {
                        startIndex = index + 1
                        continue
                    }
                    
                    val endIndex = index + appNameLower.length
                    
                    // Create clickable span for the app name
                    val clickableSpan = object : android.text.style.ClickableSpan() {
                        override fun onClick(widget: android.view.View) {
                            // Show suggestions for this specific app
                            showAppSuggestions(listOf(app))
                        }
                        
                        override fun updateDrawState(ds: android.text.TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = false // Remove underline
                        }
                    }
                    
                    // Create custom span for the pill with icon and styling
                    val pillSpan = createPillSpan(app)
                    
                    // Apply pill styling with custom span
                    spannableString.setSpan(clickableSpan, index, endIndex, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(pillSpan, index, endIndex, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.WHITE), index, endIndex, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), index, endIndex, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    
                    hasPills = true
                    startIndex = endIndex
                }
            }
        }
        
        // Only update text if we found pills to avoid interfering with normal typing
        if (hasPills) {
            try {
                isUpdatingPills = true // Set flag before updating
                val currentSelection = messageInput.selectionStart
                
                // Use replace() instead of setText() to preserve autocorrect
                val editable = messageInput.text
                editable.replace(0, editable.length, spannableString)
                
                // Restore cursor position if it's still valid
                if (currentSelection >= 0 && currentSelection <= text.length) {
                    messageInput.setSelection(currentSelection)
                } else {
                    messageInput.setSelection(text.length)
                }
            } catch (e: Exception) {
                android.util.Log.e("InlinePills", "Error applying inline pills", e)
            } finally {
                isUpdatingPills = false // Always reset flag
            }
        }
    }
    
    // Create a custom span for the pill with icon and styling
    private fun createPillSpan(app: AppInfo): android.text.style.ReplacementSpan {
        return object : android.text.style.ReplacementSpan() {
            override fun getSize(paint: android.graphics.Paint, text: CharSequence?, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int {
                val iconSize = (16 * resources.displayMetrics.density).toInt() // Smaller icon: 16dp
                val horizontalPadding = (8 * resources.displayMetrics.density).toInt() // Less padding: 8dp
                val iconTextGap = (4 * resources.displayMetrics.density).toInt() // Gap between icon and text: 4dp
                val verticalPadding = (8 * resources.displayMetrics.density).toInt() // Vertical padding: 8dp
                val textWidth = paint.measureText(text, start, end).toInt()
                
                // Update font metrics to ensure pill height can contain icon + padding
                if (fm != null) {
                    val requiredPillHeight = iconSize + (verticalPadding * 2) // Total height needed
                    val currentTextHeight = fm.bottom - fm.top
                    
                    // Ensure we have enough vertical space for the icon
                    if (requiredPillHeight > currentTextHeight) {
                        val extraHeight = requiredPillHeight - currentTextHeight
                        fm.top -= extraHeight / 2
                        fm.bottom += extraHeight / 2
                        fm.ascent -= extraHeight / 2
                        fm.descent += extraHeight / 2
                    }
                }
                
                return horizontalPadding + iconSize + iconTextGap + textWidth + horizontalPadding
            }
            
            override fun draw(canvas: android.graphics.Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: android.graphics.Paint) {
                val iconSize = (16 * resources.displayMetrics.density).toInt() // Smaller icon: 16dp
                val horizontalPadding = (8 * resources.displayMetrics.density).toInt() // Less padding: 8dp
                val iconTextGap = (4 * resources.displayMetrics.density).toInt() // Gap between icon and text: 4dp
                val verticalPadding = (8 * resources.displayMetrics.density).toInt() // Vertical padding: 8dp
                val cornerRadius = 12 * resources.displayMetrics.density // Smaller radius: 12dp
                
                // Measure text
                val textWidth = paint.measureText(text, start, end)
                val totalWidth = horizontalPadding + iconSize + iconTextGap + textWidth + horizontalPadding
                val totalHeight = (bottom - top).toFloat()
                
                // Draw background with rounded corners (fill the entire allocated space)
                val rect = android.graphics.RectF(x, top.toFloat(), x + totalWidth, bottom.toFloat())
                val bgPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#3A3A3C")
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
                
                // Draw border
                val borderPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#4A4A4C")
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1 * resources.displayMetrics.density
                }
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
                
                // Draw icon (with proper padding inside the background)
                if (app.icon != null) {
                    try {
                        val iconBitmap = android.graphics.Bitmap.createBitmap(iconSize, iconSize, android.graphics.Bitmap.Config.ARGB_8888)
                        val iconCanvas = android.graphics.Canvas(iconBitmap)
                        app.icon.setBounds(0, 0, iconSize, iconSize)
                        app.icon.draw(iconCanvas)
                        
                        // Center icon vertically within the pill
                        val iconY = top + (totalHeight - iconSize) / 2f
                        canvas.drawBitmap(iconBitmap, x + horizontalPadding, iconY, null)
                    } catch (e: Exception) {
                        android.util.Log.e("InlinePills", "Error drawing icon", e)
                    }
                }
                
                // Draw text (centered vertically)
                val textPaint = android.graphics.Paint(paint).apply {
                    color = android.graphics.Color.WHITE
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
                val textX = x + horizontalPadding + iconSize + iconTextGap
                canvas.drawText(text ?: "", start, end, textX, y.toFloat(), textPaint)
            }
        }
    }
    
    // Detect app names and intent names in text and show pills
    private fun detectAppsInText(text: String) {
        if (text.isEmpty()) {
            // Show recent apps when text input is cleared (only if browser is not visible)
            if (!isBrowserVisible) {
            showRecentApps()
            }
            return
        }
        
        // Don't show app suggestions when browser is visible
        if (isBrowserVisible) {
            suggestionsScrollView.visibility = android.view.View.GONE
            return
        }

        val detectedApps = mutableListOf<AppInfo>()
        val textLower = text.lowercase()

        // First, detect app names - only match complete words
        for (app in installedApps) {
            val appNameLower = app.appName.lowercase()
            if (appNameLower.length > 2 && isCompleteWordMatch(textLower, appNameLower)) {
                detectedApps.add(app)
            }
        }
        
        // If no apps detected, check for intent action phrases
        if (detectedApps.isEmpty()) {
            detectedApps.addAll(detectIntentInText(textLower))
        }

        if (detectedApps.isNotEmpty()) {
            showAppSuggestions(detectedApps)
        } else {
            suggestionsScrollView.visibility = android.view.View.GONE
        }
    }
    
    // Check if a word appears as a complete word in the text (not as part of another word)
    private fun isCompleteWordMatch(text: String, word: String): Boolean {
        val index = text.indexOf(word)
        if (index == -1) return false
        
        // Check if the word is at the beginning of text or preceded by a word boundary
        val beforeChar = if (index > 0) text[index - 1] else null
        val isWordStart = beforeChar == null || isWordBoundary(beforeChar)
        
        // Check if the word is at the end of text or followed by a word boundary
        val afterIndex = index + word.length
        val afterChar = if (afterIndex < text.length) text[afterIndex] else null
        val isWordEnd = afterChar == null || isWordBoundary(afterChar)
        
        return isWordStart && isWordEnd
    }
    
    // Check if a character is a word boundary
    private fun isWordBoundary(char: Char): Boolean {
        return char == ' ' || 
               char == '.' || 
               char == ',' || 
               char == '!' || 
               char == '?' || 
               char == ';' || 
               char == ':' || 
               char == '-' ||
               char == '\n' ||
               char == '\t'
    }
    
    // Check if a word appears as a complete word at a specific position in the text
    private fun isCompleteWordMatchAtPosition(text: String, word: String, position: Int): Boolean {
        // Check if the word is at the beginning of text or preceded by a word boundary
        val beforeChar = if (position > 0) text[position - 1] else null
        val isWordStart = beforeChar == null || isWordBoundary(beforeChar)
        
        // Check if the word is at the end of text or followed by a word boundary
        val afterIndex = position + word.length
        val afterChar = if (afterIndex < text.length) text[afterIndex] else null
        val isWordEnd = afterChar == null || isWordBoundary(afterChar)
        
        return isWordStart && isWordEnd
    }
    
    // Detect intent actions in text and return apps that support them
    private fun detectIntentInText(textLower: String): List<AppInfo> {
        val appsForIntent = mutableListOf<AppInfo>()
        
        // Map individual words to intent actions (more flexible matching)
        val intentWords = mapOf(
            "send" to android.content.Intent.ACTION_SEND,
            "share" to android.content.Intent.ACTION_SEND,
            "call" to android.content.Intent.ACTION_DIAL,
            "dial" to android.content.Intent.ACTION_DIAL,
            "photo" to android.content.Intent.ACTION_SEND,
            "image" to android.content.Intent.ACTION_SEND,
            "view" to android.content.Intent.ACTION_VIEW,
            "open" to android.content.Intent.ACTION_VIEW
        )
        
        // Check if text contains any intent word
        for ((word, action) in intentWords) {
            if (textLower.contains(word)) {
                // Find all apps that support this intent
                val packageManager = packageManager
                val intent = when (action) {
                    android.content.Intent.ACTION_SEND -> {
                        if (textLower.contains("photo") || textLower.contains("image")) {
                            android.content.Intent(action).setType("image/*")
                        } else {
                            android.content.Intent(action).setType("text/plain")
                        }
                    }
                    android.content.Intent.ACTION_DIAL -> android.content.Intent(action)
                    android.content.Intent.ACTION_VIEW -> android.content.Intent(action)
                    else -> android.content.Intent(action)
                }
                
                val activities = packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                
                // Convert to AppInfo objects (skip duplicates)
                for (resolveInfo in activities) {
                    val packageName = resolveInfo.activityInfo.packageName
                    // Find if we already have this app in installedApps
                    val existingApp = installedApps.find { it.packageName == packageName }
                    if (existingApp != null && !appsForIntent.contains(existingApp)) {
                        appsForIntent.add(existingApp)
                    }
                }
                
                // If we found a match, don't check other words
                if (appsForIntent.isNotEmpty()) {
                    break
                }
            }
        }
        
        // Sort apps by recent usage (most recent first)
        // Get the recent apps list to use as reference for sorting
        val recentApps = usageStatsManager.getRecentlyUsedApps(50) // Get more apps for better sorting
        val recentAppsMap = recentApps.mapIndexed { index, app -> app.packageName to index }.toMap()
        
        return appsForIntent.sortedBy { app ->
            // Apps that appear in recent apps get their position (lower = more recent)
            // Apps not in recent apps get a high number (appear last)
            recentAppsMap[app.packageName] ?: 999
        }
    }
    
    // Show app suggestion pills
    private fun showAppSuggestions(apps: List<AppInfo>) {
        suggestionsLayout.removeAllViews()

        // If only one app, show it with all its intents
        if (apps.size == 1) {
            val app = apps[0]
            val pill = createAppPill(app, "Open") // Changed to say "Open"
            suggestionsLayout.addView(pill)

            // Get and show ALL intent capabilities for this app
            val intentCapabilities = appManager.getAppIntentCapabilities(app.packageName)
            for (capability in intentCapabilities) { // Show ALL intent capabilities
                val intentPill = createIntentPill(app, capability)
                suggestionsLayout.addView(intentPill)
            }
        } else {
            // Multiple apps - show just the app pills (they can click to see intents)
            for (app in apps) {
                val pill = createAppPill(app) // Show app name
                suggestionsLayout.addView(pill)
            }
        }

        suggestionsScrollView.visibility = android.view.View.VISIBLE
    }
    
    // Create a pill for an app
    private fun createAppPill(app: AppInfo, label: String = app.appName): android.widget.LinearLayout {
        val pill = android.widget.LinearLayout(this)
        pill.orientation = android.widget.LinearLayout.HORIZONTAL
        pill.gravity = android.view.Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * resources.displayMetrics.density).toInt() // 10dp horizontal padding
        val verticalPaddingPx = (8 * resources.displayMetrics.density).toInt() // 8dp vertical padding
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        pill.background = getDrawable(R.drawable.pill_background_compact)
        
        // Create icon view
        val iconView = android.widget.ImageView(this)
        iconView.setImageDrawable(app.icon)
        val iconSizePx = (32 * resources.displayMetrics.density).toInt() // 32dp icon (matches inline pill scale better)
        iconView.layoutParams = android.widget.LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        iconView.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        
        // Create text view
        val textView = android.widget.TextView(this)
        textView.text = label // Use provided label
        textView.setTextColor(android.graphics.Color.WHITE)
        textView.textSize = 14f
        textView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val textPaddingPx = (6 * resources.displayMetrics.density).toInt() // 6dp gap between icon and text
        textView.setPadding(textPaddingPx, 0, 0, 0)
        
        // Add views to pill
        pill.addView(iconView)
        pill.addView(textView)
        
        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * resources.displayMetrics.density).toInt() // 16dp right margin
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        // Add ripple effect for better touch feedback
        pill.foreground = getDrawable(android.R.drawable.list_selector_background)
        
        pill.setOnClickListener {
            // Embed the app when pill is clicked
            embedApp(app.packageName)
        }
        
        return pill
    }
    
    // Create a pill for an intent capability
    private fun createIntentPill(app: AppInfo, capability: IntentCapability): android.widget.LinearLayout {
        val pill = android.widget.LinearLayout(this)
        pill.orientation = android.widget.LinearLayout.HORIZONTAL
        pill.gravity = android.view.Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * resources.displayMetrics.density).toInt() // 10dp horizontal padding
        val verticalPaddingPx = (8 * resources.displayMetrics.density).toInt() // 8dp vertical padding
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        pill.background = getDrawable(R.drawable.pill_background_compact)
        
        // Create icon view using the app icon (not generic action icon)
        val iconView = android.widget.ImageView(this)
        iconView.setImageDrawable(app.icon) // Use app icon instead of capability.icon
        val iconSizePx = (32 * resources.displayMetrics.density).toInt() // 32dp icon (same as app pills)
        iconView.layoutParams = android.widget.LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        iconView.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        
        // Create text view for intent action name
        val textView = android.widget.TextView(this)
        textView.text = capability.displayName
        textView.setTextColor(android.graphics.Color.WHITE)
        textView.textSize = 14f
        textView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val textPaddingPx = (6 * resources.displayMetrics.density).toInt() // 6dp gap between icon and text
        textView.setPadding(textPaddingPx, 0, 0, 0)
        
        // Add views to pill
        pill.addView(iconView)
        pill.addView(textView)
        
        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * resources.displayMetrics.density).toInt() // 16dp right margin
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        // Add ripple effect for better touch feedback
        pill.foreground = getDrawable(android.R.drawable.list_selector_background)
        
                pill.setOnClickListener {
                    // Show parameter input dialog for intents that require parameters
                    if (capability.parameters.isNotEmpty()) {
                        showParameterInputDialog(app, capability)
                    } else {
                        // Launch the app with the specific intent (no parameters needed)
                        val success = appManager.launchAppWithIntent(app.packageName, capability)
                        if (!success) {
                            android.widget.Toast.makeText(this, "Could not launch ${app.appName} with ${capability.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        
        return pill
    }
    
    // Show dynamic parameter input dialog
    private fun showParameterInputDialog(app: AppInfo, capability: IntentCapability) {
        val dialog = android.app.Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_parameter_input, null)
        dialog.setContentView(view)
        dialog.show()

        val dialogTitle = view.findViewById<TextView>(R.id.dialogTitle)
        val dialogDescription = view.findViewById<TextView>(R.id.dialogDescription)
        val parametersContainer = view.findViewById<LinearLayout>(R.id.parametersContainer)
        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        val executeButton = view.findViewById<Button>(R.id.executeButton)

        // Set dialog title and description
        dialogTitle.text = capability.displayName
        dialogDescription.text = capability.description

        // Create input fields for each parameter
        val parameterInputs = mutableMapOf<String, EditText>()
        
        for (parameter in capability.parameters) {
            val parameterLayout = LinearLayout(this)
            parameterLayout.orientation = LinearLayout.VERTICAL
            parameterLayout.setPadding(0, 0, 0, 16)

            // Parameter label
            val label = TextView(this)
            label.text = "${parameter.name}${if (parameter.required) " *" else ""}"
            label.setTextColor(android.graphics.Color.WHITE)
            label.textSize = 14f
            label.setPadding(0, 0, 0, 4)

            // Parameter input
            val input = EditText(this)
            input.hint = parameter.example
            input.setTextColor(android.graphics.Color.WHITE)
            input.setHintTextColor(android.graphics.Color.parseColor("#8E8E93"))
            input.background = getDrawable(R.drawable.input_background)
            input.setPadding(12, 12, 12, 12)

            // Set default values for common parameters
            when (parameter.name) {
                "message" -> input.setText("Hello! This is a message from AgentOS launcher.")
                "phone_number" -> input.setText("+1234567890")
                "url" -> input.setText("https://www.google.com")
            }

            parameterInputs[parameter.name] = input

            parameterLayout.addView(label)
            parameterLayout.addView(input)
            parametersContainer.addView(parameterLayout)
        }

        // Set up cancel button
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        // Set up execute button
        executeButton.setOnClickListener {
            val parameters = mutableMapOf<String, String>()
            var hasErrors = false

            // Collect parameter values and validate required ones
            for (parameter in capability.parameters) {
                val input = parameterInputs[parameter.name]!!
                val value = input.text.toString().trim()
                
                if (parameter.required && value.isEmpty()) {
                    input.error = "This field is required"
                    hasErrors = true
                } else {
                    parameters[parameter.name] = value
                }
            }

            if (!hasErrors) {
                val success = appManager.launchAppWithCallback(app.packageName, capability, parameters, this)
                if (success) {
                    dialog.dismiss()
                } else {
                    android.widget.Toast.makeText(this, "Could not launch ${app.appName} with ${capability.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Apply dark theme colors
    private fun applyDarkColors() {
        val chatRecyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.chatRecyclerView)
        val inputLayout = findViewById<android.widget.LinearLayout>(R.id.inputLayout)
        
        // Dark theme colors
        chatRecyclerView.setBackgroundColor(android.graphics.Color.BLACK)
        inputLayout.setBackgroundColor(android.graphics.Color.BLACK)
        messageInput.setTextColor(android.graphics.Color.WHITE)
        messageInput.setHintTextColor(android.graphics.Color.parseColor("#8E8E93"))
        
        // Set input field background to dark grey with rounded corners
        val darkInputDrawable = android.graphics.drawable.GradientDrawable()
        darkInputDrawable.setColor(android.graphics.Color.parseColor("#2C2C2E"))
        darkInputDrawable.cornerRadius = 20 * resources.displayMetrics.density // 20dp
        darkInputDrawable.setStroke((1 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#3A3A3C"))
        messageInput.background = darkInputDrawable
    }
    
    // Helper function to hide keyboard and clear focus (best practice)
    private fun hideKeyboardAndClearFocus() {
        messageInput.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(messageInput.windowToken, 0)
    }

    
    // Handle callbacks from launched intents
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            1001 -> { // Our intent callback
                when (resultCode) {
                    RESULT_OK -> {
                        // Intent completed successfully
                        if (data != null) {
                            // Handle returned data (e.g., from ACTION_PICK)
                            val selectedUri = data.data
                            if (selectedUri != null) {
                                android.widget.Toast.makeText(this, "Selected: $selectedUri", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            android.widget.Toast.makeText(this, "Action completed successfully", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    RESULT_CANCELED -> {
                        // User cancelled the intent
                        android.widget.Toast.makeText(this, "Action cancelled", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    // Show recently used apps as pills when launcher opens
    private fun showRecentApps() {
        // Check if we have usage stats permission
        if (!usageStatsManager.hasUsageStatsPermission()) {
            // Show permission request dialog only once per session
            if (!hasShownPermissionDialog) {
                hasShownPermissionDialog = true
                showUsageStatsPermissionDialog()
            }
            return
        }
        
        // Load recent apps in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val recentApps = usageStatsManager.getRecentlyUsedApps(10)
                
                withContext(Dispatchers.Main) {
                    if (recentApps.isNotEmpty()) {
                        showRecentAppsPills(recentApps)
                        // Show success toast
                        Toast.makeText(this@MainActivity, "Recent apps loaded successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        // No recent apps found
                        Toast.makeText(this@MainActivity, "No recent apps found. Try using some apps first!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RecentApps", "Error loading recent apps", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error loading recent apps", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Show usage stats permission request dialog
    private fun showUsageStatsPermissionDialog() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_usage_permission, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.show()
        
        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.dialogMessage)
        val grantButton = view.findViewById<Button>(R.id.grantButton)
        val skipButton = view.findViewById<Button>(R.id.skipButton)
        
        title.text = "Enable App Usage Access"
        message.text = "To show your recently used apps, AgentOS needs permission to access app usage statistics.\n\n1. Tap 'Open Settings' below\n2. Find 'AgentOS' in the list\n3. Toggle the switch to enable\n4. Return to AgentOS"
        
        grantButton.text = "Open Settings"
        grantButton.setOnClickListener {
            dialog.dismiss()
            usageStatsManager.requestUsageStatsPermission()
            
            // Show a follow-up dialog to check permission
            showPermissionCheckDialog()
        }
        
        skipButton.setOnClickListener {
            dialog.dismiss()
        }
    }
    
    // Show dialog to check if permission was granted
    private fun showPermissionCheckDialog() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_permission_check, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.show()
        
        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.dialogMessage)
        val checkButton = view.findViewById<Button>(R.id.checkButton)
        val skipButton = view.findViewById<Button>(R.id.skipButton)
        
        title.text = "Permission Granted?"
        message.text = "After enabling the permission in Settings, tap 'Check Again' to load your recent apps."
        
        checkButton.setOnClickListener {
            dialog.dismiss()
            // Check permission and show recent apps if granted
            showRecentApps()
        }
        
        skipButton.setOnClickListener {
            dialog.dismiss()
        }
    }
    
    // Display recent apps as pills
    private fun showRecentAppsPills(recentApps: List<AppInfo>) {
        suggestionsLayout.removeAllViews()
        
        // Add recent app pills (no label)
        for (app in recentApps) {
            val pill = createRecentAppPill(app)
            suggestionsLayout.addView(pill)
        }
        
        suggestionsScrollView.visibility = View.VISIBLE
    }
    
    // Create a pill for a recent app
    private fun createRecentAppPill(app: AppInfo): LinearLayout {
        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = android.view.Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * resources.displayMetrics.density).toInt() // 10dp horizontal padding (reduced)
        val verticalPaddingPx = (8 * resources.displayMetrics.density).toInt() // 8dp vertical padding (reduced)
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        pill.background = getDrawable(R.drawable.pill_background_compact)
        
        // Create icon view
        val iconView = ImageView(this)
        iconView.setImageDrawable(app.icon)
        val iconSizePx = (32 * resources.displayMetrics.density).toInt() // 32dp icon
        iconView.layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        iconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        
        // Create text view
        val textView = TextView(this)
        textView.text = app.appName
        textView.setTextColor(android.graphics.Color.WHITE)
        textView.textSize = 14f
        textView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val textPaddingPx = (6 * resources.displayMetrics.density).toInt() // 6dp gap between icon and text
        textView.setPadding(textPaddingPx, 0, 0, 0)
        
        // Add views to pill
        pill.addView(iconView)
        pill.addView(textView)
        
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * resources.displayMetrics.density).toInt() // 16dp right margin (increased)
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        // Add ripple effect for better touch feedback
        pill.foreground = getDrawable(android.R.drawable.list_selector_background)
        
        pill.setOnClickListener {
            // Embed the app when pill is clicked
            embedApp(app.packageName)
        }
        
        return pill
    }
    
    // Conversation History Methods (now handled by ConversationHistoryActivity)
    
    private fun loadConversation(conversationId: Long) {
        val messages = conversationManager.loadConversation(conversationId)
        if (messages != null) {
            // Clear current messages and load the conversation
            chatAdapter.clearMessages()
            messages.forEach { message ->
                chatAdapter.addMessage(message)
            }
            
            // Scroll to bottom
            chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            
            // Hide suggestions
            suggestionsScrollView.visibility = View.GONE
            
            // Hide welcome area
            updateWelcomeAreaVisibility()
            
            Toast.makeText(this, "Conversation loaded", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Could not load conversation", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startNewConversation() {
        conversationManager.startNewConversation()
        chatAdapter.clearMessages()
        
        // Don't show welcome message - just show the welcome area like first launch
        // Show recent apps
        showRecentApps()
        
        // Show welcome area
        updateWelcomeAreaVisibility()
        
        Toast.makeText(this, "Started new conversation", Toast.LENGTH_SHORT).show()
    }
    
    private fun handleIntentExtras() {
        val intent = intent
        android.util.Log.d("MainActivity", "Handling intent extras: ${intent.extras}")
        
        when {
            intent.getBooleanExtra("start_new_chat", false) -> {
                android.util.Log.d("MainActivity", "Starting new chat")
                startNewConversation()
            }
            intent.hasExtra("load_conversation_id") -> {
                val conversationId = intent.getLongExtra("load_conversation_id", -1L)
                android.util.Log.d("MainActivity", "Loading conversation: $conversationId")
                if (conversationId != -1L) {
                    loadConversation(conversationId)
                }
            }
        }
    }
    
    private fun updateWelcomeAreaVisibility() {
        val hasMessages = chatAdapter.itemCount > 0
        // Keep widgetDisplayArea GONE since cards are shown in RecyclerView instead
        // If VISIBLE, it blocks touch events to RecyclerView below it in the FrameLayout
        widgetDisplayArea.visibility = View.GONE
        updateSwipeRefreshState()
        updateCardsVisibility()
    }
    
    private fun toggleTray() {
        android.util.Log.d("TrayToggle", "toggleTray called, current state: isTrayVisible=$isTrayVisible")
        if (isTrayVisible) {
            hideTray()
        } else {
            showTray()
        }
    }
    
    private fun showTray() {
        android.util.Log.d("TrayToggle", "showTray called")
        // Zero latency: direct layout parameter change
        val layoutParams = hiddenTray.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        layoutParams.height = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
        hiddenTray.layoutParams = layoutParams
        isTrayVisible = true
        android.util.Log.d("TrayToggle", "Tray shown, isTrayVisible=$isTrayVisible")
        updateSwipeRefreshState()
    }
    
    private fun hideTray() {
        android.util.Log.d("TrayToggle", "hideTray called")
        // Zero latency: direct layout parameter change
        val layoutParams = hiddenTray.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        layoutParams.height = 0
        hiddenTray.layoutParams = layoutParams
        isTrayVisible = false
        android.util.Log.d("TrayToggle", "Tray hidden, isTrayVisible=$isTrayVisible")
        updateSwipeRefreshState()
    }
    
    private fun updateSwipeRefreshState() {
        // SwipeRefreshLayout should ALWAYS be enabled to allow touch events through to RecyclerView
        // It will only trigger refresh when RecyclerView is at the top and user pulls down
        // Disable only when tray is already visible
        swipeRefreshLayout.isEnabled = !isTrayVisible
        
        val hasMessages = chatAdapter.getMessages().isNotEmpty()
        val showingCards = !isKeyboardVisible && !hasMessages
        android.util.Log.d("SwipeRefresh", "Enabled: ${swipeRefreshLayout.isEnabled}, hasMessages: $hasMessages, showingCards: $showingCards, trayVisible: $isTrayVisible, keyboardVisible: $isKeyboardVisible")
        android.util.Log.d("SwipeRefresh", "RecyclerView canScrollUp: ${chatRecyclerView.canScrollVertically(-1)}, canScrollDown: ${chatRecyclerView.canScrollVertically(1)}")
    }
    
    private fun setupUpwardSwipeListener() {
        // Add touch listener to multiple views to ensure upward swipe detection
        val touchListener = View.OnTouchListener { _, event ->
            if (!isTrayVisible) {
                return@OnTouchListener false // Only handle touches when tray is visible
            }
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.y
                    isTracking = true
                    android.util.Log.d("TouchListener", "ACTION_DOWN: startY=$startY")
                    false // Don't consume the event initially
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isTracking) {
                        val deltaY = event.y - startY
                        android.util.Log.d("TouchListener", "ACTION_MOVE: deltaY=$deltaY")
                        // If user swipes up significantly (negative deltaY)
                        if (deltaY < -80) { // Reduced threshold for easier triggering
                            android.util.Log.d("TouchListener", "Upward swipe detected! Hiding tray")
                            hideTray()
                            isTracking = false
                            true // Consume the event
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTracking = false
                    false
                }
                else -> false
            }
        }
        
        // Apply touch listener ONLY to widgetDisplayArea
        // DO NOT apply to swipeRefreshLayout as it interferes with nested scrolling!
        // swipeRefreshLayout.setOnTouchListener(touchListener) // REMOVED - this breaks RecyclerView scrolling
        widgetDisplayArea.setOnTouchListener(touchListener)
        
        // Apply to hiddenTray for upward swipe to close when tray is visible
        hiddenTray.setOnTouchListener(touchListener)
    }
    
    // Right Tray Functions
    private fun toggleRightTray() {
        android.util.Log.d("RightTrayToggle", "toggleRightTray called, current state: isRightTrayVisible=$isRightTrayVisible")
        if (isRightTrayVisible) {
            hideRightTray()
        } else {
            showRightTray()
        }
    }
    
    private fun showRightTray() {
        android.util.Log.e("RightTrayToggle", "==================== showRightTray CALLED ====================")
        
        // Hide keyboard when opening right tray
        hideKeyboardAndClearFocus()
        
        // CRITICAL: Set flag FIRST before any UI changes
        isRightTrayVisible = true
        android.util.Log.e("RightTrayToggle", "Set isRightTrayVisible=true")
        
        // Show the tray immediately (no post needed since we're canceling events properly)
        rightTray.translationX = 0f
        rightTray.visibility = View.VISIBLE
        
        android.util.Log.e("RightTrayToggle", "Tray shown, visibility=${rightTray.visibility}")
        android.util.Log.e("RightTrayToggle", "==================== showRightTray DONE ====================")
    }
    
    private fun hideRightTray() {
        android.util.Log.e("RightTrayToggle", "==================== hideRightTray CALLED ====================")
        android.util.Log.e("RightTrayToggle", "BEFORE: isRightTrayVisible=$isRightTrayVisible")
        android.util.Log.e("RightTrayToggle", "Stack trace:", Exception("Stack trace"))
        
        // Just hide the tray - nothing else, no scroll manipulation
        isRightTrayVisible = false
        rightTray.translationX = rightTray.width.toFloat()
        rightTray.visibility = View.GONE
        
        android.util.Log.e("RightTrayToggle", "AFTER: isRightTrayVisible=$isRightTrayVisible")
        android.util.Log.e("RightTrayToggle", "==================== hideRightTray DONE ====================")
    }
    
    private fun setupRightEdgeSwipeListener(edgeDetector: View) {
        // Use a dedicated edge detector view for right-edge swipe detection
        // This avoids conflicts with other touch listeners
        
        val touchListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    isTrackingRight = true
                    android.util.Log.d("RightEdgeSwipe", "ACTION_DOWN at x=${event.rawX}")
                    false // Don't consume, allow other handlers
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isTrackingRight) {
                        val deltaX = event.rawX - startX
                        android.util.Log.d("RightEdgeSwipe", "ACTION_MOVE deltaX=$deltaX, startX=$startX, currentX=${event.rawX}")
                        
                        // Swipe left from right edge to open (deltaX will be negative)
                        if (!isRightTrayVisible && deltaX < -100) {
                            android.util.Log.d("RightEdgeSwipe", "Left swipe from edge detected! Showing right tray")
                            showRightTray()
                            isTrackingRight = false
                            return@OnTouchListener true // Consume the event
                        }
                    }
                    false // Don't consume by default
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTrackingRight = false
                    false
                }
                else -> false
            }
        }
        
        // Apply to edge detector view
        edgeDetector.setOnTouchListener(touchListener)
        
        // Add touch listener to right tray itself to handle closing
        rightTray.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    isTrackingRight = true
                    android.util.Log.d("RightEdgeSwipe", "Tray ACTION_DOWN at x=${event.rawX}")
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isTrackingRight && isRightTrayVisible) {
                        val deltaX = event.rawX - startX
                        android.util.Log.d("RightEdgeSwipe", "Tray ACTION_MOVE deltaX=$deltaX")
                        
                        // Swipe right to close (deltaX will be positive)
                        if (deltaX > 100) {
                            android.util.Log.d("RightEdgeSwipe", "Right swipe detected! Hiding right tray")
                            hideRightTray()
                            isTrackingRight = false
                            return@setOnTouchListener true
                        }
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTrackingRight = false
                    false
                }
                else -> false
            }
        }
    }
    
    
    private fun setupKeyboardDetection() {
        val rootView = findViewById<View>(android.R.id.content)
        val initialHeight = rootView.height
        
        // Use a more reliable method - check the window insets
        rootView.setOnApplyWindowInsetsListener { _, insets ->
            val imeHeight = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            isKeyboardVisible = imeHeight > 0
            android.util.Log.d("KeyboardDetection", "IME height: $imeHeight, Keyboard visible: $isKeyboardVisible")
            updateCardsVisibility()
            insets
        }
        
        // Fallback method: Height-based detection
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val currentHeight = rootView.height
            val heightDiff = initialHeight - currentHeight
            
            // More reliable keyboard detection
            val heightBasedKeyboardVisible = heightDiff > 150
            android.util.Log.d("KeyboardDetection", "Height diff: $heightDiff, Height-based keyboard visible: $heightBasedKeyboardVisible")
            
            // Use height-based detection as fallback if window insets don't work
            if (!isKeyboardVisible && heightBasedKeyboardVisible) {
                isKeyboardVisible = true
                updateCardsVisibility()
            }
        }
        
        // Input focus method
        messageInput.setOnFocusChangeListener { _, hasFocus ->
            android.util.Log.d("KeyboardDetection", "Input focus changed: $hasFocus")
            // Don't immediately update here, let the other methods handle it
        }
    }
    
    private fun updateCardsVisibility() {
        android.util.Log.e("CardsVisibility", ">>> updateCardsVisibility CALLED, isRightTrayVisible=$isRightTrayVisible")
        
        // CRITICAL: If right tray is visible, do NOTHING - don't change any state
        if (isRightTrayVisible) {
            android.util.Log.e("CardsVisibility", "!!! RIGHT TRAY IS VISIBLE - SKIPPING ALL UPDATES !!!")
            return
        }
        
        // Cards show only when keyboard is NOT active AND there are no messages
        val hasMessages = chatAdapter.getMessages().isNotEmpty()
        val shouldShowCards = !isKeyboardVisible && !hasMessages
        android.util.Log.e("CardsVisibility", "isKeyboardVisible: $isKeyboardVisible, hasMessages: $hasMessages, isRightTrayVisible: $isRightTrayVisible, shouldShowCards: $shouldShowCards")
        android.util.Log.d("CardsVisibility", "Current itemCount: ${chatAdapter.itemCount}")
        chatAdapter.setShowCards(shouldShowCards)
        android.util.Log.d("CardsVisibility", "After setShowCards - itemCount: ${chatAdapter.itemCount}")
        
        // Update swipe refresh state after cards visibility changes
        chatRecyclerView.post {
            updateSwipeRefreshState()
        }
    }


    // Job Grid Functions
    private fun setupJobGrid() {
        // Set up click listener for overlay to close grid
        jobGridOverlay.setOnClickListener {
            hideJobGrid()
        }
        
        // Set up click listener for "New Job" button (slot 1)
        jobGridOverlay.findViewById<View>(R.id.jobSlot1).setOnClickListener {
            showCreateJobDialog()
        }
        
        // Load saved jobs
        loadJobs()
    }
    
    private fun showJobGrid() {
        if (isJobGridVisible) return
        
        android.util.Log.d("JobGrid", "Showing job grid")
        
        isJobGridVisible = true
        jobGridOverlay.visibility = View.VISIBLE
        
        // Update job slots display
        updateJobSlots()
    }
    
    private fun hideJobGrid() {
        if (!isJobGridVisible) return
        
        android.util.Log.d("JobGrid", "Hiding job grid")
        
        isJobGridVisible = false
        jobGridOverlay.visibility = View.GONE
    }
    
    private fun updateJobSlots() {
        // Show all 6 job slots
        for (i in 0 until 6) {
            val slotId = when(i) {
                0 -> R.id.jobSlot1 // Always "Add New" button
                1 -> R.id.jobSlot2
                2 -> R.id.jobSlot3
                3 -> R.id.jobSlot4
                4 -> R.id.jobSlot5
                5 -> R.id.jobSlot6
                else -> continue
            }
            
            val slot = jobGridOverlay.findViewById<FrameLayout>(slotId)
            slot.visibility = View.VISIBLE // Always show all 6 slots
            
            if (i == 0) {
                // First slot is always the "Add New" button - keep original content
                // Don't modify slot 1, it already has the + icon and "New Job" text
            } else if (i - 1 < jobs.size) {
                // Show job
                val job = jobs[i - 1]
                updateJobSlot(slot, job)
            } else {
                // Show empty slot with dashed border
                slot.removeAllViews()
                slot.setOnClickListener {
                    showCreateJobDialog()
                }
                slot.setOnLongClickListener(null)
            }
        }
    }
    
    private fun updateJobSlot(slot: FrameLayout, job: Job) {
        slot.removeAllViews()
        
        // Create container for consistent positioning
        val container = FrameLayout(this)
        container.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        // Add icon in center (only if not empty)
        if (job.iconEmoji.isNotEmpty()) {
            val iconText = TextView(this)
            iconText.text = job.iconEmoji
            iconText.textSize = 24f
            iconText.gravity = android.view.Gravity.CENTER
            iconText.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
            container.addView(iconText)
        }
        
        // Add name text at bottom (only if job has a name)
        if (job.name.isNotEmpty()) {
            val nameText = TextView(this)
            nameText.text = job.name
            nameText.textSize = 12f
            nameText.setTextColor(android.graphics.Color.WHITE)
            nameText.gravity = android.view.Gravity.CENTER
            nameText.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = 12 // Increased bottom margin for more padding
            }
            container.addView(nameText)
        }
        
        slot.addView(container)
        
        // Set click listener to execute the job
        slot.setOnClickListener {
            executeJob(job)
            hideJobGrid()
        }
        
        // Set long click listener to edit/delete the job
        slot.setOnLongClickListener {
            showJobOptionsDialog(job)
            true
        }
    }
    
    private fun showCreateJobDialog() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_create_job, null)
        dialog.setContentView(view)
        dialog.show()
        
        val jobNameInput = view.findViewById<EditText>(R.id.jobNameInput)
        val jobPromptInput = view.findViewById<EditText>(R.id.jobPromptInput)
        val jobIconInput = view.findViewById<EditText>(R.id.jobIconInput)
        
        view.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }
        
        view.findViewById<Button>(R.id.saveButton).setOnClickListener {
            val name = jobNameInput.text.toString().trim()
            val prompt = jobPromptInput.text.toString().trim()
            val icon = jobIconInput.text.toString().trim().ifEmpty { "🤖" }
            
            if (name.isNotEmpty() && prompt.isNotEmpty()) {
                val job = Job(
                    id = System.currentTimeMillis().toString(),
                    name = name,
                    prompt = prompt,
                    iconEmoji = icon
                )
                jobs.add(job)
                saveJobs()
                updateJobSlots()
                dialog.dismiss()
                Toast.makeText(this, "Job created: $name", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showJobOptionsDialog(job: Job) {
        val options = arrayOf("Edit Job", "Delete Job", "Cancel")
        
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Job: ${job.name}")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> editJob(job) // Edit
                1 -> deleteJob(job) // Delete
                2 -> dialog.dismiss() // Cancel
            }
        }
        builder.show()
    }
    
    private fun editJob(job: Job) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_create_job, null)
        dialog.setContentView(view)
        
        // Pre-fill with existing job data
        val jobNameInput = view.findViewById<EditText>(R.id.jobNameInput)
        val jobPromptInput = view.findViewById<EditText>(R.id.jobPromptInput)
        val jobIconInput = view.findViewById<EditText>(R.id.jobIconInput)
        
        jobNameInput.setText(job.name)
        jobPromptInput.setText(job.prompt)
        jobIconInput.setText(job.iconEmoji)
        
        // Change title - find the title TextView in the dialog
        val titleView = view.findViewById<TextView>(android.R.id.title) 
            ?: view.rootView.findViewById<TextView>(android.R.id.title)
        titleView?.text = "Edit Job"
        
        // Change save button text
        val saveButton = view.findViewById<Button>(R.id.saveButton)
        saveButton.text = "Update Job"
        
        dialog.show()
        
        view.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }
        
        saveButton.setOnClickListener {
            val name = jobNameInput.text.toString().trim()
            val prompt = jobPromptInput.text.toString().trim()
            val icon = jobIconInput.text.toString().trim().ifEmpty { "🤖" }
            
            if (name.isNotEmpty() && prompt.isNotEmpty()) {
                // Update the job
                val updatedJob = job.copy(
                    name = name,
                    prompt = prompt,
                    iconEmoji = icon
                )
                
                // Replace in list
                val index = jobs.indexOfFirst { it.id == job.id }
                if (index >= 0) {
                    jobs[index] = updatedJob
                    saveJobs()
                    updateJobSlots()
                    dialog.dismiss()
                    Toast.makeText(this, "Job updated: $name", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun deleteJob(job: Job) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Delete Job")
        builder.setMessage("Are you sure you want to delete '${job.name}'?")
        builder.setPositiveButton("Delete") { _, _ ->
            jobs.removeAll { it.id == job.id }
            saveJobs()
            updateJobSlots()
            Toast.makeText(this, "Job deleted: ${job.name}", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    private fun executeJob(job: Job) {
        android.util.Log.d("JobGrid", "Executing job: ${job.name}")
        
        // Show typing indicator in chat
        chatAdapter.showTypingIndicator()
        chatRecyclerView.postDelayed({
            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }, 50)
        
        // Extract screen content
        extractScreenContent { screenContent ->
            // Combine job prompt with screen content
            val contextMessage = """
                ${job.prompt}
                
                Current screen content:
                $screenContent
            """.trimIndent()
            
            // Send to Grok
            sendJobToGrok(job, contextMessage)
        }
    }
    
    private fun extractScreenContent(callback: (String) -> Unit) {
        val startTime = System.currentTimeMillis()
        android.util.Log.d("JobGrid", "Starting screen content extraction...")
        
        when {
            isBrowserVisible -> {
                // Extract content from WebView
                extractWebViewContent(callback)
            }
            else -> {
                // Extract content from chat screen
                extractChatContent(callback)
            }
        }
    }
    
    private fun extractWebViewContent(callback: (String) -> Unit) {
        val startTime = System.currentTimeMillis()
        
        // Use the same JavaScript extraction as we do for chat with webpage
        val javascript = """
            (function() {
                var content = '';
                
                // Get page title
                content += 'Page Title: ' + document.title + '\n\n';
                
                // Get meta description
                var metaDesc = document.querySelector('meta[name="description"]');
                if (metaDesc) {
                    content += 'Description: ' + metaDesc.getAttribute('content') + '\n\n';
                }
                
                // Get main headings
                var headings = document.querySelectorAll('h1, h2, h3');
                if (headings.length > 0) {
                    content += 'Headings:\n';
                    for (var i = 0; i < Math.min(headings.length, 10); i++) {
                        content += '- ' + headings[i].textContent.trim() + '\n';
                    }
                    content += '\n';
                }
                
                // Get paragraphs and main text content
                var paragraphs = document.querySelectorAll('p, article, .content, main');
                if (paragraphs.length > 0) {
                    content += 'Content:\n';
                    for (var i = 0; i < Math.min(paragraphs.length, 15); i++) {
                        var text = paragraphs[i].textContent.trim();
                        if (text.length > 50) {
                            content += text.substring(0, 300) + '\n\n';
                        }
                    }
                }
                
                // Get list items
                var lists = document.querySelectorAll('li');
                if (lists.length > 0) {
                    content += 'List Items:\n';
                    for (var i = 0; i < Math.min(lists.length, 10); i++) {
                        var text = lists[i].textContent.trim();
                        if (text.length > 10) {
                            content += '• ' + text.substring(0, 150) + '\n';
                        }
                    }
                }
                
                return content.substring(0, 8000); // Limit content length
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(javascript) { result ->
            val extractionTime = System.currentTimeMillis() - startTime
            android.util.Log.d("JobGrid", "WebView content extraction completed in ${extractionTime}ms")
            
            // Clean up the result (remove quotes and escape characters)
            val cleanedResult = result?.let {
                it.removeSurrounding("\"")
                  .replace("\\n", "\n")
                  .replace("\\\"", "\"")
                  .replace("\\/", "/")
            } ?: "Could not extract webpage content"
            
            android.util.Log.d("JobGrid", "Extracted WebView content length: ${cleanedResult.length} characters")
            callback(cleanedResult)
        }
    }
    
    private fun extractChatContent(callback: (String) -> Unit) {
        val startTime = System.currentTimeMillis()
        
        // Extract recent chat messages
        val messages = chatAdapter.getMessages()
        val recentMessages = messages.takeLast(10) // Get last 10 messages
        
        val content = StringBuilder()
        content.append("Recent Chat Messages:\n\n")
        
        for (message in recentMessages) {
            val sender = if (message.isUser) "User" else "AI"
            content.append("$sender: ${message.text}\n\n")
        }
        
        // If no messages, indicate empty chat
        if (messages.isEmpty()) {
            content.append("No chat messages available.\n")
            content.append("Current screen: AgentOS launcher with empty chat.")
        }
        
        val extractionTime = System.currentTimeMillis() - startTime
        android.util.Log.d("JobGrid", "Chat content extraction completed in ${extractionTime}ms")
        android.util.Log.d("JobGrid", "Extracted chat content length: ${content.length} characters")
        
        callback(content.toString())
    }
    
    private fun sendJobToGrok(job: Job, contextMessage: String) {
        val apiStartTime = System.currentTimeMillis()
        android.util.Log.d("JobGrid", "Starting Grok API call for job: ${job.name}")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = externalAIService.generateResponse(contextMessage, ModelType.EXTERNAL_GROK)
                val apiTime = System.currentTimeMillis() - apiStartTime
                android.util.Log.d("JobGrid", "Grok API call completed in ${apiTime / 1000.0}s")
                
                withContext(Dispatchers.Main) {
                    // Hide typing indicator
                    chatAdapter.hideTypingIndicator()
                    
                    // Add job execution message
                    val jobMessage = ChatMessage("🤖 **${job.name}** executed:\n\n$response", isUser = false)
                    chatAdapter.addMessage(jobMessage)
                    conversationManager.addMessage(jobMessage)
                    
                    // Scroll to bottom
                    chatRecyclerView.postDelayed({
                        chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                    }, 50)
                    
                    Toast.makeText(this@MainActivity, "Job '${job.name}' completed!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("JobGrid", "Error executing job: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // Hide typing indicator
                    chatAdapter.hideTypingIndicator()
                    
                    // Show error message
                    val errorMessage = ChatMessage("❌ Job '${job.name}' failed: ${e.message}", isUser = false)
                    chatAdapter.addMessage(errorMessage)
                    
                    Toast.makeText(this@MainActivity, "Job failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun loadJobs() {
        // Load jobs from SharedPreferences
        val prefs = getSharedPreferences("jobs", MODE_PRIVATE)
        val jobsJson = prefs.getString("jobs_list", "[]")
        
        try {
            val jsonArray = org.json.JSONArray(jobsJson)
            jobs.clear()
            for (i in 0 until jsonArray.length()) {
                val jobJson = jsonArray.getJSONObject(i)
                val job = Job(
                    id = jobJson.getString("id"),
                    name = jobJson.getString("name"),
                    prompt = jobJson.getString("prompt"),
                    iconEmoji = jobJson.optString("iconEmoji", "🤖"),
                    createdAt = jobJson.optLong("createdAt", System.currentTimeMillis())
                )
                jobs.add(job)
            }
            android.util.Log.d("JobGrid", "Loaded ${jobs.size} jobs")
            
            // Add default jobs if none exist or if we need to update them
            if (jobs.isEmpty() || shouldUpdateDefaultJobs()) {
                jobs.clear() // Clear existing jobs
                addDefaultJobs()
            }
        } catch (e: Exception) {
            android.util.Log.e("JobGrid", "Error loading jobs: ${e.message}")
            // Add default jobs on error too
            addDefaultJobs()
        }
    }
    
    private fun shouldUpdateDefaultJobs(): Boolean {
        // Check job version to force update to new layout
        val prefs = getSharedPreferences("jobs", MODE_PRIVATE)
        val currentVersion = prefs.getInt("jobs_version", 0)
        val targetVersion = 3 // Increment this when we want to force update
        
        if (currentVersion < targetVersion) {
            // Update version
            prefs.edit().putInt("jobs_version", targetVersion).apply()
            return true
        }
        
        return false
    }
    
    private fun addDefaultJobs() {
        val defaultJobs = listOf(
            Job(
                id = "default_summarize",
                name = "Summarize",
                prompt = "Please provide a concise summary of the content on this screen in 3-4 bullet points. Focus on the key information and main points.",
                iconEmoji = "📝"
            ),
            Job(
                id = "default_chat",
                name = "Chat",
                prompt = "Based on the content on this screen, start a helpful conversation. Ask clarifying questions or provide insights about what you see.",
                iconEmoji = "💬"
            ),
            Job(
                id = "default_explain",
                name = "Explain",
                prompt = "Explain the content on this screen in simple terms. Break down any complex concepts and provide context where needed.",
                iconEmoji = "💡"
            ),
            Job(
                id = "default_context",
                name = "Add to context",
                prompt = "Analyze and remember the content on this screen for future reference. Summarize the key points that might be useful later.",
                iconEmoji = "🧠"
            ),
            Job(
                id = "default_empty",
                name = "",
                prompt = "Analyze the content on this screen and provide any relevant insights or observations.",
                iconEmoji = ""
            )
        )
        
        jobs.addAll(defaultJobs)
        saveJobs()
        android.util.Log.d("JobGrid", "Added ${defaultJobs.size} default jobs")
    }
    
    private fun saveJobs() {
        // Save jobs to SharedPreferences
        val prefs = getSharedPreferences("jobs", MODE_PRIVATE)
        val jsonArray = org.json.JSONArray()
        
        for (job in jobs) {
            val jobJson = org.json.JSONObject()
            jobJson.put("id", job.id)
            jobJson.put("name", job.name)
            jobJson.put("prompt", job.prompt)
            jobJson.put("iconEmoji", job.iconEmoji)
            jobJson.put("createdAt", job.createdAt)
            jsonArray.put(jobJson)
        }
        
        prefs.edit().putString("jobs_list", jsonArray.toString()).apply()
        android.util.Log.d("JobGrid", "Saved ${jobs.size} jobs")
    }
    
    private fun handleOverlayIntent(intent: Intent) {
        // Show job grid (from floating overlay double-tap)
        if (intent.getBooleanExtra("SHOW_JOB_GRID", false)) {
            window.decorView.post {
                showJobGrid()
            }
        }
        
        // Show top tray (from floating overlay pull-down)
        if (intent.getBooleanExtra("SHOW_TOP_TRAY", false)) {
            window.decorView.post {
                showTray()
            }
        }
        
        // Show right tray (from floating overlay right-edge pull)
        if (intent.getBooleanExtra("SHOW_RIGHT_TRAY", false)) {
            window.decorView.post {
                showRightTray()
            }
        }
        
        // Show as transparent overlay (from floating button)
        if (intent.getBooleanExtra("SHOW_TRANSPARENT", false)) {
            window.decorView.post {
                enableTransparentMode()
            }
        }
    }
    
    private fun enableTransparentMode() {
        try {
            android.util.Log.d("TransparentMode", "Enabling transparent overlay mode")
            
            // Make all main containers semi-transparent
            findViewById<View>(R.id.swipeRefreshLayout)?.apply {
                setBackgroundColor(android.graphics.Color.parseColor("#BB000000")) // 73% black - more transparent
            }
            
            // Make chat recycler view background semi-transparent
            findViewById<View>(R.id.chatRecyclerView)?.apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            
            // Make input area less transparent for visibility
            findViewById<View>(R.id.inputLayout)?.apply {
                setBackgroundColor(android.graphics.Color.parseColor("#DD000000")) // 87% black
            }
            
            // Make hidden tray semi-transparent too
            findViewById<View>(R.id.hiddenTray)?.apply {
                setBackgroundColor(android.graphics.Color.parseColor("#DD000000")) // 87% black
            }
            
            android.util.Log.d("TransparentMode", "Transparent overlay mode enabled successfully")
        } catch (e: Exception) {
            android.util.Log.e("TransparentMode", "Error enabling transparent mode: ${e.message}", e)
        }
    }
    
    private fun disableTransparentMode() {
        // Restore dark background
        val rootView = findViewById<android.view.View>(android.R.id.content)
        rootView.setBackgroundColor(android.graphics.Color.BLACK)
        
        findViewById<View>(R.id.swipeRefreshLayout)?.apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        
        findViewById<View>(R.id.inputLayout)?.apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        
        android.util.Log.d("TransparentMode", "Transparent overlay mode disabled")
    }
    
    // App Embedding Functions
    private fun embedApp(packageName: String) {
        try {
            android.util.Log.d("AppEmbedding", "Attempting to embed app: $packageName")
            
            // Check if app is installed
            val packageManager = packageManager
            val appInfo = try {
                packageManager.getApplicationInfo(packageName, 0)
            } catch (e: Exception) {
                Toast.makeText(this, "App not installed: $packageName", Toast.LENGTH_SHORT).show()
                return
            }
            
            // For now, show a placeholder with app info since true embedding requires system-level access
            showEmbeddedAppPlaceholder(appInfo.loadLabel(packageManager).toString(), packageName)
            
        } catch (e: Exception) {
            android.util.Log.e("AppEmbedding", "Error embedding app: ${e.message}", e)
            Toast.makeText(this, "Failed to embed app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showEmbeddedAppPlaceholder(appName: String, packageName: String) {
        // Show embedded container
        showEmbeddedApp()
        
        // Create placeholder content
        val container = embeddedAppContainer ?: return
        container.removeAllViews()
        
        // Create placeholder layout
        val placeholderLayout = LinearLayout(this)
        placeholderLayout.orientation = LinearLayout.VERTICAL
        placeholderLayout.gravity = android.view.Gravity.CENTER
        placeholderLayout.setPadding(32, 32, 32, 32)
        
        // App icon (if available)
        try {
            val appIcon = packageManager.getApplicationIcon(packageName)
            val iconView = ImageView(this)
            iconView.setImageDrawable(appIcon)
            iconView.layoutParams = LinearLayout.LayoutParams(128, 128).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = 24
            }
            placeholderLayout.addView(iconView)
        } catch (e: Exception) {
            // No icon available
        }
        
        // App name
        val titleText = TextView(this)
        titleText.text = appName
        titleText.textSize = 24f
        titleText.setTextColor(android.graphics.Color.WHITE)
        titleText.gravity = android.view.Gravity.CENTER
        titleText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 16
        }
        placeholderLayout.addView(titleText)
        
        // Explanation text
        val explanationText = TextView(this)
        explanationText.text = """
            App Embedding Demo
            
            Due to Android security restrictions, apps cannot be truly embedded within other apps without system-level permissions.
            
            Alternative approaches:
            • Floating overlay (requires permission)
            • Quick Settings tile
            • Notification actions
            • Accessibility service
        """.trimIndent()
        explanationText.textSize = 14f
        explanationText.setTextColor(android.graphics.Color.LTGRAY)
        explanationText.gravity = android.view.Gravity.CENTER
        explanationText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 24
        }
        placeholderLayout.addView(explanationText)
        
        // Launch button
        val launchButton = Button(this)
        launchButton.text = "Launch $appName"
        launchButton.setOnClickListener {
            // Launch the app normally
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        }
        placeholderLayout.addView(launchButton)
        
        // Floating button controls
        val floatingButton = Button(this)
        floatingButton.text = "Show Floating Button"
        floatingButton.setOnClickListener {
            requestOverlayPermission()
        }
        placeholderLayout.addView(floatingButton)
        
        // Stop floating button
        val stopFloatingButton = Button(this)
        stopFloatingButton.text = "Hide Floating Button"
        stopFloatingButton.setOnClickListener {
            stopService(Intent(this, FloatingOverlayService::class.java))
            Toast.makeText(this, "Floating button removed", Toast.LENGTH_SHORT).show()
        }
        placeholderLayout.addView(stopFloatingButton)
        
        // Add to container
        container.addView(placeholderLayout)
        
        // Store embedded app info
        isAppEmbedded = true
        embeddedAppPackage = packageName
        
        android.util.Log.d("AppEmbedding", "Showing placeholder for: $appName")
    }
    
    private fun showEmbeddedApp() {
        // Hide other views
        swipeRefreshLayout.visibility = View.GONE
        webView.visibility = View.GONE
        
        // Show embedded container
        embeddedAppContainer?.visibility = View.VISIBLE
        
        android.util.Log.d("AppEmbedding", "Embedded app container shown")
    }
    
    private fun hideEmbeddedApp() {
        // Hide embedded container
        embeddedAppContainer?.visibility = View.GONE
        
        // Show chat screen
        swipeRefreshLayout.visibility = View.VISIBLE
        
        // Reset state
        isAppEmbedded = false
        embeddedAppPackage = null
        
        android.util.Log.d("AppEmbedding", "Embedded app container hidden")
    }
    
    private fun requestOverlayPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1234)
                Toast.makeText(this, "Please grant overlay permission to enable floating mode", Toast.LENGTH_LONG).show()
            } else {
                createFloatingOverlay()
            }
        } else {
            createFloatingOverlay()
        }
    }
    
    private fun createFloatingOverlay() {
        // Start the floating button service
        val serviceIntent = Intent(this, FloatingOverlayService::class.java)
        startService(serviceIntent)
        
        Toast.makeText(this, "Floating button created! Tap it to return to AgentOS.", Toast.LENGTH_LONG).show()
        
        // Minimize the app to show the button
        moveTaskToBack(true)
    }
    
    // Browser Setup and Functions
    private fun setupBrowser() {
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
    }
    
    private var webViewStartY = 0f
    private var webViewStartScrollY = 0
    
    private fun setupWebViewTouchListener() {
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    webViewStartY = event.rawY
                    webViewStartScrollY = webView.scrollY
                    false // Don't consume the event
                }
                MotionEvent.ACTION_MOVE -> {
                    // Check if we're at the top of the WebView and pulling down
                    if (webView.scrollY == 0 && webViewStartScrollY == 0) {
                        val deltaY = event.rawY - webViewStartY
                        if (deltaY > 50) { // Pull down threshold
                            // Show the hidden tray
                            if (!isTrayVisible) {
                                showTray()
                            }
                            true // Consume the event
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }
    
    private fun showBrowser() {
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
        sendButton.background = getDrawable(R.drawable.pill_background_compact)
        sendButton.setColorFilter(android.graphics.Color.WHITE)
        
        browserMenuButton.background = getDrawable(R.drawable.pill_background_compact)
        browserMenuButton.setColorFilter(android.graphics.Color.WHITE)
        
        tabManagerButton.background = getDrawable(R.drawable.pill_background_compact)
        tabManagerButton.setColorFilter(android.graphics.Color.WHITE)
        
        // Make input field transparent and text whiter
        messageInput.background = getDrawable(R.drawable.pill_background_compact)
        messageInput.setTextColor(android.graphics.Color.WHITE)
        messageInput.setHintTextColor(android.graphics.Color.parseColor("#CCCCCC"))
        
        // Make input layout transparent
        findViewById<LinearLayout>(R.id.inputLayout).setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        // Create initial tab if none exists
        if (browserManager.getAllTabs().isEmpty()) {
            val tab = browserManager.createNewTab()
            // Don't replace the webView reference, just configure the existing one
            configureWebView(webView)
        } else {
            val currentTab = browserManager.getCurrentTab()
            if (currentTab != null) {
                // Switch to the current tab's WebView
                switchToTabWebView(currentTab.webView)
            }
        }
        
        // Load default page if webview is empty
        if (webView.url == null || webView.url == "about:blank") {
            android.util.Log.d("Browser", "Loading default page: https://www.google.com")
            webView.loadUrl("https://www.google.com")
        } else {
            android.util.Log.d("Browser", "WebView already has URL: ${webView.url}")
        }
        
        // Update input hint for URL navigation and make it single line
        messageInput.hint = "Enter URL or type message..."
        messageInput.textSize = 14f
        messageInput.maxLines = 1
        messageInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        messageInput.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
        
        // Hide keyboard
        hideKeyboardAndClearFocus()
        
        android.util.Log.d("Browser", "Browser shown")
    }
    
    private fun hideBrowser() {
        android.util.Log.d("Browser", "hideBrowser() called, isInChatMode=$isInChatMode")
        
        try {
            isBrowserVisible = false
            
            // Hide close chat button
            closeChatButton.visibility = View.GONE
            
            // Exit chat mode if active
            if (isInChatMode) {
                isInChatMode = false
                currentPageUrl = null
                
                // Restore WebView to normal (hidden) position
                val webViewParams = webView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                webViewParams.height = 0
                webViewParams.topToBottom = R.id.hiddenTray
                webViewParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                webViewParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                webViewParams.bottomToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                webViewParams.matchConstraintPercentHeight = 1.0f
                webView.layoutParams = webViewParams
                
                // Restore chat screen to normal position
                val chatParams = swipeRefreshLayout.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                chatParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                chatParams.topToBottom = R.id.hiddenTray
                chatParams.bottomToTop = R.id.inputLayout
                chatParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                chatParams.matchConstraintPercentHeight = 1.0f
                swipeRefreshLayout.layoutParams = chatParams
                
                // Remove click listener from WebView
                webView.setOnClickListener(null)
                
                android.util.Log.d("Browser", "Chat mode cleanup completed")
            }
        } catch (e: Exception) {
            android.util.Log.e("Browser", "Error in hideBrowser chat mode cleanup: ${e.message}", e)
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
            sendButton.background = getDrawable(R.drawable.send_button_background)
            sendButton.clearColorFilter()
            
            browserMenuButton.background = getDrawable(R.drawable.pill_background_compact)
            browserMenuButton.clearColorFilter()
            
            tabManagerButton.background = getDrawable(R.drawable.pill_background_compact)
            tabManagerButton.clearColorFilter()
            
            // Restore input field original background and text colors
            messageInput.background = getDrawable(R.drawable.input_background)
            messageInput.setTextColor(getColor(android.R.color.primary_text_light))
            messageInput.setHintTextColor(getColor(android.R.color.secondary_text_light))
            
            // Restore input layout background
            findViewById<LinearLayout>(R.id.inputLayout).background = ContextCompat.getDrawable(this, android.R.attr.colorBackground)
            
            // Reset input hint and restore multi-line behavior
            messageInput.hint = "Search apps or type message..."
            messageInput.textSize = 16f
            messageInput.maxLines = 3
            messageInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            messageInput.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            
            android.util.Log.d("Browser", "Browser hidden successfully")
        } catch (e: Exception) {
            android.util.Log.e("Browser", "Error in hideBrowser UI restoration: ${e.message}", e)
        }
    }
    
    private fun isUrl(text: String): Boolean {
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
        
        // Check if text contains any TLD
        val containsTld = tlds.any { trimmed.contains(it) }
        
        // Check for IP address pattern (e.g., 192.168.1.1)
        val ipPattern = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?(/.*)?$""")
        val isIpAddress = ipPattern.matches(trimmed)
        
        // Check for localhost
        val isLocalhost = trimmed.startsWith("localhost")
        
        // Check if it starts with common protocols
        val hasProtocol = trimmed.startsWith("http://") || trimmed.startsWith("https://") || 
                         trimmed.startsWith("ftp://") || trimmed.startsWith("file://")
        
        return containsTld || isIpAddress || isLocalhost || hasProtocol
    }
    
    private fun loadUrl(url: String) {
        var finalUrl = url.trim()
        
        if (finalUrl.isEmpty()) {
            android.util.Log.d("Browser", "Empty URL provided")
            return
        }
        
        // Add https:// if no protocol specified
        if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
            finalUrl = "https://$finalUrl"
        }
        
        android.util.Log.d("Browser", "Loading URL: $finalUrl")
        webView.loadUrl(finalUrl)
        
        // Hide keyboard after loading URL
        hideKeyboardAndClearFocus()
    }
    
    private fun extractPageContent(callback: (String) -> Unit) {
        val startTime = System.currentTimeMillis()
        android.util.Log.d("Browser", "Starting page content extraction...")
        
        // JavaScript to extract main content from the page
        val javascript = """
            (function() {
                let content = '';
                
                // Get page title
                content += 'Page Title: ' + document.title + '\\n\\n';
                
                // Get meta description
                const metaDesc = document.querySelector('meta[name="description"]');
                if (metaDesc) {
                    content += 'Description: ' + metaDesc.content + '\\n\\n';
                }
                
                // Get main content - try different selectors
                const mainSelectors = ['main', 'article', '[role="main"]', '.main-content', '#main-content', '.content', '#content'];
                let mainContent = null;
                
                for (let selector of mainSelectors) {
                    mainContent = document.querySelector(selector);
                    if (mainContent) break;
                }
                
                // If no main content found, use body
                if (!mainContent) {
                    mainContent = document.body;
                }
                
                // Extract text from headings
                const headings = mainContent.querySelectorAll('h1, h2, h3, h4, h5, h6');
                headings.forEach(h => {
                    content += h.textContent.trim() + '\\n';
                });
                
                // Extract text from paragraphs
                const paragraphs = mainContent.querySelectorAll('p');
                paragraphs.forEach(p => {
                    const text = p.textContent.trim();
                    if (text.length > 0) {
                        content += text + '\\n\\n';
                    }
                });
                
                // Extract text from lists
                const lists = mainContent.querySelectorAll('li');
                lists.forEach(li => {
                    const text = li.textContent.trim();
                    if (text.length > 0) {
                        content += '• ' + text + '\\n';
                    }
                });
                
                // Limit content length to avoid token limits (approximately 4000 words)
                if (content.length > 16000) {
                    content = content.substring(0, 16000) + '...\\n\\n[Content truncated due to length]';
                }
                
                return content;
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(javascript) { result ->
            val extractionTime = System.currentTimeMillis() - startTime
            android.util.Log.d("Browser", "Page content extraction completed in ${extractionTime}ms")
            
            // Remove quotes and unescape the result
            val cleanedResult = result?.trim('"')?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""
            android.util.Log.d("Browser", "Extracted content length: ${cleanedResult.length} characters")
            callback(cleanedResult)
        }
    }
    
    private fun chatWithPageContent(userQuery: String) {
        val totalStartTime = System.currentTimeMillis()
        
        // Show typing indicator
        chatAdapter.showTypingIndicator()
        chatRecyclerView.postDelayed({
            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }, 50)
        
        // Enter chat mode
        isInChatMode = true
        currentPageUrl = webView.url
        
        // Shrink WebView to thumbnail
        shrinkWebViewToThumbnail()
        
        // Extract page content
        extractPageContent { pageContent ->
            val extractionTime = System.currentTimeMillis() - totalStartTime
            // Add user message to chat
            val userMessage = ChatMessage(userQuery, isUser = true)
            chatAdapter.addMessage(userMessage)
            conversationManager.addMessage(userMessage)
            
            // Prepare context for Grok
            val contextMessage = """
                I'm viewing a webpage. Here's the content:
                
                $pageContent
                
                User question: $userQuery
            """.trimIndent()
            
            // Send to Grok
            sendToGrokWithContext(contextMessage, extractionTime, pageContent.length, totalStartTime)
        }
        
        // Hide keyboard
        hideKeyboardAndClearFocus()
    }
    
    private fun sendToGrokWithContext(
        contextMessage: String,
        extractionTime: Long,
        contentLength: Int,
        totalStartTime: Long
    ) {
        val apiStartTime = System.currentTimeMillis()
        android.util.Log.d("Browser", "Starting Grok API call, context length: ${contextMessage.length} characters")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = externalAIService.generateResponse(contextMessage, ModelType.EXTERNAL_GROK)
                val apiTime = System.currentTimeMillis() - apiStartTime
                val totalTime = System.currentTimeMillis() - totalStartTime
                android.util.Log.d("Browser", "Grok API call completed in ${apiTime}ms")
                
                withContext(Dispatchers.Main) {
                    // Hide typing indicator
                    chatAdapter.hideTypingIndicator()
                    
                    // Add timing info message
                    val timingInfo = """
                        ⏱️ **Performance:**
                        • Content extraction: ${extractionTime / 1000.0}s
                        • Page content size: ${contentLength} chars
                        • Grok API call: ${apiTime / 1000.0}s
                        • Total time: ${totalTime / 1000.0}s
                    """.trimIndent()
                    val timingMessage = ChatMessage(timingInfo, isUser = false)
                    chatAdapter.addMessage(timingMessage)
                    
                    // Add AI response
                    val aiMessage = ChatMessage(response, isUser = false)
                    chatAdapter.addMessage(aiMessage)
                    conversationManager.addMessage(aiMessage)
                    
                    // Scroll to bottom
                    chatRecyclerView.postDelayed({
                        chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                    }, 50)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Hide typing indicator
                    chatAdapter.hideTypingIndicator()
                    
                    // Show error
                    val errorMessage = ChatMessage("Sorry, I couldn't process that. Error: ${e.message}", isUser = false)
                    chatAdapter.addMessage(errorMessage)
                }
            }
        }
    }
    
    private fun shrinkWebViewToThumbnail() {
        // Set WebView to top half using constraints (50/50 split)
        val layoutParams = webView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        layoutParams.height = 0 // Use match_constraint
        layoutParams.topToBottom = R.id.hiddenTray
        layoutParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        layoutParams.bottomToTop = R.id.swipeRefreshLayout
        layoutParams.matchConstraintPercentHeight = 0.5f // 50% of available height
        webView.layoutParams = layoutParams
        
        // Set chat screen to bottom half
        val chatLayoutParams = swipeRefreshLayout.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        chatLayoutParams.topToBottom = R.id.webView
        chatLayoutParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        chatLayoutParams.bottomToTop = R.id.inputLayout
        chatLayoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        chatLayoutParams.matchConstraintPercentHeight = 0.5f // 50% of available height
        swipeRefreshLayout.layoutParams = chatLayoutParams
        
        // Show chat screen below
        swipeRefreshLayout.visibility = View.VISIBLE
        
        // Show close chat button
        closeChatButton.visibility = View.VISIBLE
        
        // Make WebView clickable to expand
        webView.setOnClickListener {
            expandWebViewToFullSize()
        }
        
        android.util.Log.d("Browser", "WebView set to top 50%, chat to bottom 50%")
    }
    
    private fun expandWebViewToFullSize() {
        // Exit chat mode
        isInChatMode = false
        currentPageUrl = null
        
        // Hide close chat button
        closeChatButton.visibility = View.GONE
        
        // Restore WebView to full size
        val layoutParams = webView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        layoutParams.height = 0 // Match constraint
        layoutParams.topToBottom = R.id.hiddenTray
        layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        layoutParams.matchConstraintPercentHeight = 1.0f // Reset to 100%
        webView.layoutParams = layoutParams
        
        // Restore chat screen constraints
        val chatLayoutParams = swipeRefreshLayout.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        chatLayoutParams.bottomToTop = R.id.inputLayout
        chatLayoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        chatLayoutParams.matchConstraintPercentHeight = 1.0f // Reset to 100%
        swipeRefreshLayout.layoutParams = chatLayoutParams
        
        // Hide chat screen
        swipeRefreshLayout.visibility = View.GONE
        
        // Remove click listener
        webView.setOnClickListener(null)
        
        android.util.Log.d("Browser", "WebView expanded to full size, exited chat mode")
    }
    
    // Override back button to handle browser navigation and embedded apps
    override fun onBackPressed() {
        when {
            isAppEmbedded -> {
                hideEmbeddedApp()
            }
            isBrowserVisible && webView.canGoBack() -> {
                webView.goBack()
            }
            isBrowserVisible -> {
                hideBrowser()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }
    
    // Browser Menu Functions
    private fun showBrowserMenu() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_browser_menu, null)
        dialog.setContentView(view)
        dialog.show()
        
        // Set up menu item click listeners
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
            hideBrowser()
        }
    }
    
    private fun createNewTab() {
        val tab = browserManager.createNewTab()
        // Configure the existing WebView for the new tab
        configureWebView(webView)
        webView.loadUrl("https://www.google.com")
        Toast.makeText(this, "New tab created", Toast.LENGTH_SHORT).show()
    }
    
    private fun createNewIncognitoTab() {
        val tab = browserManager.createNewTab(isIncognito = true)
        // Configure the existing WebView for the new incognito tab
        configureWebView(webView)
        webView.loadUrl("https://www.google.com")
        Toast.makeText(this, "New incognito tab created", Toast.LENGTH_SHORT).show()
    }
    
    private fun showBookmarks() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_bookmarks, null)
        dialog.setContentView(view)
        dialog.show()
        
        val bookmarksList = view.findViewById<LinearLayout>(R.id.bookmarksList)
        
        // Add current page button
        view.findViewById<LinearLayout>(R.id.addBookmarkButton).setOnClickListener {
            val currentUrl = webView.url
            val currentTitle = webView.title ?: "Untitled"
            if (currentUrl != null && currentUrl.isNotEmpty()) {
                browserManager.addBookmark(currentTitle, currentUrl)
                Toast.makeText(this, "Bookmark added: $currentTitle", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                showBookmarks() // Refresh the dialog
            } else {
                Toast.makeText(this, "No page to bookmark", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Populate bookmarks list
        val bookmarks = browserManager.getAllBookmarks()
        if (bookmarks.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "No bookmarks yet"
            emptyText.setTextColor(getColor(android.R.color.white))
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
        
        // Close button
        view.findViewById<Button>(R.id.closeBookmarksButton).setOnClickListener {
            dialog.dismiss()
        }
    }
    
    private fun showHistory() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_history, null)
        dialog.setContentView(view)
        dialog.show()
        
        val historyList = view.findViewById<LinearLayout>(R.id.historyList)
        
        // Clear history button
        view.findViewById<LinearLayout>(R.id.clearHistoryButton).setOnClickListener {
            browserManager.clearHistory()
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        // Populate history list
        val history = browserManager.getAllHistory().take(20) // Show last 20
        if (history.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "No history yet"
            emptyText.setTextColor(getColor(android.R.color.white))
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
        
        // Close button
        view.findViewById<Button>(R.id.closeHistoryButton).setOnClickListener {
            dialog.dismiss()
        }
    }
    
    private fun showDownloads() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_downloads, null)
        dialog.setContentView(view)
        dialog.show()
        
        val downloadsList = view.findViewById<LinearLayout>(R.id.downloadsList)
        
        // For now, show a placeholder message
        val emptyText = TextView(this)
        emptyText.text = "No downloads yet\n\nDownloads will appear here when you download files from websites."
        emptyText.setTextColor(getColor(android.R.color.white))
        emptyText.textSize = 16f
        emptyText.gravity = android.view.Gravity.CENTER
        emptyText.setPadding(16, 32, 16, 32)
        downloadsList.addView(emptyText)
        
        // Close button
        view.findViewById<Button>(R.id.closeDownloadsButton).setOnClickListener {
            dialog.dismiss()
        }
    }
    
    private fun showBrowserSettings() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_browser_settings, null)
        dialog.setContentView(view)
        dialog.show()
        
        val javascriptSwitch = view.findViewById<Switch>(R.id.javascriptSwitch)
        val zoomControlsSwitch = view.findViewById<Switch>(R.id.zoomControlsSwitch)
        
        // JavaScript toggle
        javascriptSwitch.setOnCheckedChangeListener { _, isChecked ->
            webView.settings.javaScriptEnabled = isChecked
            Toast.makeText(this, "JavaScript ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }
        
        // Zoom controls toggle
        zoomControlsSwitch.setOnCheckedChangeListener { _, isChecked ->
            webView.settings.displayZoomControls = isChecked
            Toast.makeText(this, "Zoom controls ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }
        
        // Clear data button
        view.findViewById<LinearLayout>(R.id.clearDataButton).setOnClickListener {
            webView.clearCache(true)
            webView.clearHistory()
            browserManager.clearHistory()
            Toast.makeText(this, "Browser data cleared", Toast.LENGTH_SHORT).show()
        }
        
        // Close button
        view.findViewById<Button>(R.id.closeSettingsButton).setOnClickListener {
            dialog.dismiss()
        }
    }
    
    // Helper methods for WebView management
    private fun configureWebView(webView: android.webkit.WebView) {
        // Configure WebView settings
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
                    browserManager.addToHistory(title, it)
                    
                    // If in chat mode and page changed, notify user
                    if (isInChatMode && currentPageUrl != null && currentPageUrl != url) {
                        android.util.Log.d("Browser", "Page changed in chat mode: $currentPageUrl -> $url")
                        currentPageUrl = url
                        
                        // Show a message that the page changed
                        runOnUiThread {
                            val notificationMessage = ChatMessage(
                                "📄 Page changed to: ${view?.title ?: url}\n\nYou can continue asking questions about the new page.",
                                isUser = false
                            )
                            chatAdapter.addMessage(notificationMessage)
                            chatRecyclerView.postDelayed({
                                chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
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
                browserManager.getCurrentTab()?.let { tab ->
                    val updatedTab = tab.copy(title = title ?: "")
                    val index = browserManager.getAllTabs().indexOfFirst { it.id == tab.id }
                    if (index >= 0) {
                        // Update the tab in the manager
                        browserManager.getAllTabs().toMutableList()[index] = updatedTab
                    }
                }
            }
        }
        
        // Set download listener
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            // Handle downloads
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        }
    }
    
    private fun switchToTabWebView(newWebView: android.webkit.WebView) {
        // Hide current WebView
        webView.visibility = View.GONE
        
        // Update reference to new WebView
        webView = newWebView
        
        // Show new WebView
        webView.visibility = View.VISIBLE
    }
    
    // Helper methods for creating list items
    private fun createBookmarkItem(bookmark: BrowserBookmark, dialog: Dialog): LinearLayout {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(16, 12, 16, 12)
        item.background = getDrawable(R.drawable.menu_item_background)
        item.setPadding(16, 12, 16, 12)
        
        val titleText = TextView(this)
        titleText.text = bookmark.title
        titleText.setTextColor(getColor(android.R.color.white))
        titleText.textSize = 16f
        titleText.setTypeface(null, android.graphics.Typeface.BOLD)
        
        val urlText = TextView(this)
        urlText.text = bookmark.url
        urlText.setTextColor(getColor(android.R.color.darker_gray))
        urlText.textSize = 14f
        
        item.addView(titleText)
        item.addView(urlText)
        
        item.setOnClickListener {
            webView.loadUrl(bookmark.url)
            dialog.dismiss()
        }
        
        return item
    }
    
    private fun createHistoryItem(historyItem: BrowserHistoryItem, dialog: Dialog): LinearLayout {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.setPadding(16, 12, 16, 12)
        item.background = getDrawable(R.drawable.menu_item_background)
        item.setPadding(16, 12, 16, 12)
        
        val titleText = TextView(this)
        titleText.text = historyItem.title
        titleText.setTextColor(getColor(android.R.color.white))
        titleText.textSize = 16f
        titleText.setTypeface(null, android.graphics.Typeface.BOLD)
        
        val urlText = TextView(this)
        urlText.text = historyItem.url
        urlText.setTextColor(getColor(android.R.color.darker_gray))
        urlText.textSize = 14f
        
        val dateText = TextView(this)
        val date = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        dateText.text = date.format(java.util.Date(historyItem.dateVisited))
        dateText.setTextColor(getColor(android.R.color.darker_gray))
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
    
    // Tab Switcher Functions
    private fun showTabSwitcher() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_tab_switcher, null)
        dialog.setContentView(view)
        dialog.show()
        
        val tabsList = view.findViewById<LinearLayout>(R.id.tabsList)
        
        // New tab button
        view.findViewById<LinearLayout>(R.id.newTabFromSwitcherButton).setOnClickListener {
            dialog.dismiss()
            createNewTab()
        }
        
        // Populate tabs list
        val tabs = browserManager.getAllTabs()
        if (tabs.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "No tabs open"
            emptyText.setTextColor(getColor(android.R.color.white))
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
        
        // Close button
        view.findViewById<Button>(R.id.closeTabSwitcherButton).setOnClickListener {
            dialog.dismiss()
        }
    }
    
    private fun createTabItem(tab: BrowserTab, dialog: Dialog): LinearLayout {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.HORIZONTAL
        item.setPadding(16, 12, 16, 12)
        item.background = getDrawable(R.drawable.menu_item_background)
        item.setPadding(16, 12, 16, 12)
        
        val contentLayout = LinearLayout(this)
        contentLayout.orientation = LinearLayout.VERTICAL
        contentLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        
        val titleText = TextView(this)
        titleText.text = if (tab.title.isNotEmpty()) tab.title else "New Tab"
        titleText.setTextColor(getColor(android.R.color.white))
        titleText.textSize = 16f
        titleText.setTypeface(null, android.graphics.Typeface.BOLD)
        
        val urlText = TextView(this)
        urlText.text = if (tab.url.isNotEmpty()) tab.url else "about:blank"
        urlText.setTextColor(getColor(android.R.color.darker_gray))
        urlText.textSize = 14f
        
        val incognitoText = TextView(this)
        if (tab.isIncognito) {
            incognitoText.text = "🔒 Incognito"
            incognitoText.setTextColor(getColor(android.R.color.holo_orange_light))
            incognitoText.textSize = 12f
        }
        
        contentLayout.addView(titleText)
        contentLayout.addView(urlText)
        if (tab.isIncognito) {
            contentLayout.addView(incognitoText)
        }
        
        // Close tab button
        val closeButton = ImageButton(this)
        closeButton.layoutParams = LinearLayout.LayoutParams(40, 40)
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        closeButton.setColorFilter(android.graphics.Color.WHITE)
        closeButton.background = getDrawable(R.drawable.pill_background_compact)
        closeButton.setOnClickListener {
            browserManager.closeTab(tab.id)
            dialog.dismiss()
            showTabSwitcher() // Refresh the dialog
        }
        
        item.addView(contentLayout)
        item.addView(closeButton)
        
        // Highlight current tab
        if (tab.isActive) {
            item.background = getDrawable(R.drawable.menu_item_background)
            item.setPadding(16, 12, 16, 12)
        }
        
        item.setOnClickListener {
            browserManager.switchToTab(tab.id)
            webView = tab.webView
            dialog.dismiss()
        }
        
        return item
    }

}
