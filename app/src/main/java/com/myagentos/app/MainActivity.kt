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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Enum for tab types in card overview
enum class TabType {
    BLINKS,
    APPS
}

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
    private lateinit var blinkService: BlinkService
    private lateinit var walletManager: WalletManager
    private lateinit var mcpService: McpService
    private var selectedModel: ModelType? = null
    
    // MCP app state
    private val connectedApps = mutableMapOf<String, McpApp>()
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
    
    // Tab state variables for Blinks vs Apps
    private lateinit var cardTypeTabLayout: com.google.android.material.tabs.TabLayout
    private var currentTab: TabType = TabType.APPS  // Default to Apps tab
    
    // Track current MCP app context for showing relevant tool pills
    private var currentMcpAppContext: String? = null  // appId of the current MCP app in conversation
    
    // Track last invoked tool for re-invocation with updated parameters
    private data class LastInvokedTool(
        val appId: String,
        val tool: McpTool,
        val lastParameters: Map<String, Any>
    )
    private var lastInvokedTool: LastInvokedTool? = null
    
    // Parameter collection state
    private data class ParameterCollectionState(
        val appId: String,
        val tool: McpTool,
        val requiredParams: List<String>,
        val collectedParams: MutableMap<String, Any> = mutableMapOf(),
        val conversationHistory: MutableList<Pair<String, String>> = mutableListOf() // role to content
    )
    private var activeParameterCollection: ParameterCollectionState? = null

    companion object {
        private var savedMessages: MutableList<ChatMessage>? = null
        private const val REQUEST_CODE_APP_DETAILS = 1001
    }
    
    // Override dispatchTouchEvent to intercept right-edge gestures before system
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val screenWidth = resources.displayMetrics.widthPixels
        val edgeThreshold = 60 * resources.displayMetrics.density
        val isNearRightEdge = event.rawX > (screenWidth - edgeThreshold)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Handle triple-tap detection first
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < 300) { // 300ms tap window
                    tapCount++
                    if (tapCount == 3) {
                        // Triple tap detected
                        android.util.Log.d("JobGrid", "TRIPLE TAP DETECTED at (${event.x}, ${event.y})")
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
        
        // Clear MCP app context and parameter collection on app start (fresh state)
        currentMcpAppContext = null
        activeParameterCollection = null
        lastInvokedTool = null
        android.util.Log.d("MCP", "Cleared MCP app context and parameter collection on app start")
        
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
        blinkService = BlinkService()
        walletManager = WalletManager(this)
        mcpService = McpService.getInstance()
        
        // Reset any stale MCP connections from previous app session
        mcpService.resetConnections()
        
        // Set up connection loss listener to auto-reconnect or show Connect button
        mcpService.setOnConnectionLost { serverUrl ->
            android.util.Log.d("MCP", "Connection lost to: $serverUrl")
            runOnUiThread {
                handleMcpConnectionLost(serverUrl)
            }
        }

        setupUI()
        checkFirstLaunch()
        showRecentApps()
        
        // Auto-start floating button service
        autoStartFloatingButton()
        
        // Handle intent extras
        handleIntentExtras()
        
        // Load test blinks for development/testing
        loadTestBlinks()
    }
    
    private fun autoStartFloatingButton() {
        // Check if we have overlay permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                // Start the floating button service
                val serviceIntent = Intent(this, FloatingOverlayService::class.java)
                startService(serviceIntent)
                android.util.Log.d("MainActivity", "Auto-started floating button service")
                Toast.makeText(this, "Floating button active", Toast.LENGTH_SHORT).show()
            } else {
                android.util.Log.d("MainActivity", "No overlay permission, requesting permission")
                // Request overlay permission automatically on first launch
                requestOverlayPermission()
            }
        } else {
            // For older Android versions, just start the service
            val serviceIntent = Intent(this, FloatingOverlayService::class.java)
            startService(serviceIntent)
            android.util.Log.d("MainActivity", "Auto-started floating button service (pre-M)")
            Toast.makeText(this, "Floating button active", Toast.LENGTH_SHORT).show()
        }
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
        
        // Initialize tab layout for Blinks vs Apps
        cardTypeTabLayout = findViewById(R.id.cardTypeTabLayout)
        setupTabLayout()
        
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
        chatAdapter = SimpleChatAdapter(
            messages = messages,
            isDarkMode = true,
            showCards = false,
            onBrowseClick = {
                // Browse pill click callback
                showBrowser()
            },
            blinkCard = null,
            onBlinkActionClick = { actionUrl, parameters ->
                // Handle blink action execution
                handleBlinkActionExecution(actionUrl, parameters)
            },
            onMcpAppConnectClick = { appId ->
                // Handle MCP app connection
                connectToMcpApp(appId)
            },
            getConnectedApps = {
                // Provide connected apps state
                connectedApps
            },
            onMcpToolInvokeClick = { appId, toolName ->
                // Handle MCP tool invocation
                invokeMcpTool(appId, toolName)
            },
            onMcpAppDetailsClick = { appId ->
                // Handle MCP app details screen
                showMcpAppDetails(appId)
            }
        )
        
        // Use GridLayoutManager for 1-column cards
        val gridLayoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 1)
        gridLayoutManager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return chatAdapter.getSpanSize(position, 1)
            }
        }
        chatRecyclerView.layoutManager = gridLayoutManager
        chatRecyclerView.adapter = chatAdapter
        
        // Set initial card type to match default tab (Apps)
        chatAdapter.setCardType(currentTab)
        
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
            
            // Clear MCP app context and parameter collection
            currentMcpAppContext = null
            activeParameterCollection = null
            lastInvokedTool = null
            android.util.Log.d("MCP", "Cleared MCP app context and parameter collection (new chat button)")
            
            // Show recent apps pills (will respect cleared MCP context)
            showRecentApps()
            android.util.Log.d("MCP", "Updated pills to show recent apps after new chat")
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
        android.util.Log.d("MCP", "Active parameter collection: ${activeParameterCollection != null}")
        android.util.Log.d("MCP", "Last invoked tool: ${lastInvokedTool?.tool?.name}")
        android.util.Log.d("MCP", "Current MCP context: $currentMcpAppContext")
        
        CoroutineScope(Dispatchers.IO).launch {
            android.util.Log.e("AI_RESPONSE", "Coroutine started on thread: ${Thread.currentThread().name}")
            try {
                android.util.Log.e("AI_RESPONSE", "About to call API...")
                
                // Intelligent routing based on context
                val aiResponse = when {
                    // 1. Active parameter collection - collecting required params
                    activeParameterCollection != null -> {
                        android.util.Log.d("MCP", "Route: Parameter collection conversation")
                        continueParameterCollection(userMessage)
                    }
                    
                    // 2. In MCP context - check for tool invocation or parameter changes
                    currentMcpAppContext != null -> {
                        handleMcpContextMessage(userMessage)
                    }
                    
                    // 3. Regular conversation - no MCP context
                    else -> {
                        android.util.Log.d("MCP", "Route: Regular conversation")
                        when (selectedModel) {
                            ModelType.EXTERNAL_CHATGPT, ModelType.EXTERNAL_GROK -> {
                                val conversationHistory = buildConversationHistory()
                                android.util.Log.d("MCP", "Built conversation history with ${conversationHistory.size} messages")
                                externalAIService.generateResponseWithHistory(userMessage, selectedModel!!, conversationHistory)
                            }
                            null -> "Please select an AI model first."
                        }
                    }
                }
                android.util.Log.e("AI_RESPONSE", "API response received: ${aiResponse.take(100)}...")
                
                withContext(Dispatchers.Main) {
                    android.util.Log.e("AI_RESPONSE", "Switched to Main thread: ${Thread.currentThread().name}")
                    android.util.Log.e("AI_RESPONSE", "Current itemCount BEFORE adding: ${chatAdapter.itemCount}")
                    
                    // Only add AI message if response is not empty (empty = tool re-invoked)
                    if (aiResponse.isNotEmpty()) {
                        val aiMessage = ChatMessage(aiResponse, isUser = false)
                        chatAdapter.addMessage(aiMessage)
                        android.util.Log.e("AI_RESPONSE", "addMessage() called, itemCount AFTER: ${chatAdapter.itemCount}")
                        
                        conversationManager.addMessage(aiMessage)
                        conversationManager.saveCurrentConversation()
                    } else {
                        android.util.Log.d("MCP", "Empty AI response (tool re-invoked), not adding message")
                    }
                    
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
        if (text.isEmpty() || isUpdatingPills || isBrowserVisible) return
        
        val spannableString = android.text.SpannableString(text)
        val textLower = text.lowercase()
        var hasPills = false
        
        // Find all installed app names in the text - only match complete words
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
        
        // Also find MCP app names in the text
        val allMcpApps = AppDirectory.getFeaturedApps()
        for (mcpApp in allMcpApps) {
            val appNameLower = mcpApp.name.lowercase()
            if (appNameLower.length > 2 && isCompleteWordMatch(textLower, appNameLower)) {
                // Find all occurrences of this MCP app name
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
                    
                    // Create clickable span for the MCP app name
                    val clickableSpan = object : android.text.style.ClickableSpan() {
                        override fun onClick(widget: android.view.View) {
                            // Show suggestions for this specific MCP app
                            showCombinedAppSuggestions(emptyList(), listOf(mcpApp))
                        }
                        
                        override fun updateDrawState(ds: android.text.TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = false // Remove underline
                        }
                    }
                    
                    // Create custom span for the MCP pill with different styling
                    val pillSpan = createMcpPillSpan(mcpApp)
                    
                    // Apply pill styling with custom span (different color for MCP apps)
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
    
    // Create a custom span for MCP app pill with same styling as regular apps
    private fun createMcpPillSpan(mcpApp: McpApp): android.text.style.ReplacementSpan {
        return object : android.text.style.ReplacementSpan() {
            override fun getSize(paint: android.graphics.Paint, text: CharSequence?, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int {
                val iconSize = (16 * resources.displayMetrics.density).toInt()
                val horizontalPadding = (8 * resources.displayMetrics.density).toInt()
                val iconTextGap = (4 * resources.displayMetrics.density).toInt()
                val verticalPadding = (8 * resources.displayMetrics.density).toInt()
                val textWidth = paint.measureText(text, start, end).toInt()
                
                if (fm != null) {
                    val requiredPillHeight = iconSize + (verticalPadding * 2)
                    val currentTextHeight = fm.bottom - fm.top
                    
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
                val iconSize = (16 * resources.displayMetrics.density).toInt()
                val horizontalPadding = (8 * resources.displayMetrics.density).toInt()
                val iconTextGap = (4 * resources.displayMetrics.density).toInt()
                val cornerRadius = 12 * resources.displayMetrics.density
                
                val textWidth = paint.measureText(text, start, end)
                val totalWidth = horizontalPadding + iconSize + iconTextGap + textWidth + horizontalPadding
                val totalHeight = (bottom - top).toFloat()
                
                // Use same styling as regular app pills - grey background
                val rect = android.graphics.RectF(x, top.toFloat(), x + totalWidth, bottom.toFloat())
                val bgPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#3A3A3C") // Same grey as regular apps
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
                
                // Same border as regular apps
                val borderPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.parseColor("#4A4A4C") // Same border as regular apps
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1 * resources.displayMetrics.density
                }
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
                
                // Draw placeholder icon (use app icon resource)
                try {
                    val iconDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_apps)
                    if (iconDrawable != null) {
                        val iconBitmap = android.graphics.Bitmap.createBitmap(iconSize, iconSize, android.graphics.Bitmap.Config.ARGB_8888)
                        val iconCanvas = android.graphics.Canvas(iconBitmap)
                        iconDrawable.setBounds(0, 0, iconSize, iconSize)
                        iconDrawable.draw(iconCanvas)
                        
                        val iconY = top + (totalHeight - iconSize) / 2f
                        canvas.drawBitmap(iconBitmap, x + horizontalPadding, iconY, null)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("InlinePills", "Error drawing MCP icon", e)
                }
                
                // Draw text - same as regular apps
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
        // PRIORITY CHECK: If we're in MCP app context, show tool pills immediately
        // Don't do any app detection at all
        if (currentMcpAppContext != null) {
            android.util.Log.d("MCP", "In MCP context, showing tool pills for: $currentMcpAppContext")
            showMcpToolSuggestions(currentMcpAppContext!!)
            return
        }
        
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
        val detectedMcpApps = mutableListOf<McpApp>()
        val textLower = text.lowercase()

        // First, detect installed app names - only match complete words
        for (app in installedApps) {
            val appNameLower = app.appName.lowercase()
            if (appNameLower.length > 2 && isCompleteWordMatch(textLower, appNameLower)) {
                detectedApps.add(app)
            }
        }
        
        // Also detect MCP apps by name or description keywords
        detectedMcpApps.addAll(detectMcpAppsInText(textLower))
        
        // If no apps detected, check for intent action phrases
        if (detectedApps.isEmpty() && detectedMcpApps.isEmpty()) {
            detectedApps.addAll(detectIntentInText(textLower))
        }

        // Show suggestions if we detected either installed apps or MCP apps
        if (detectedApps.isNotEmpty() || detectedMcpApps.isNotEmpty()) {
            showCombinedAppSuggestions(detectedApps, detectedMcpApps)
        } else {
            suggestionsScrollView.visibility = android.view.View.GONE
        }
    }
    
    // Detect MCP apps in text by name or description keywords
    private fun detectMcpAppsInText(textLower: String): List<McpApp> {
        val detectedMcpApps = mutableListOf<McpApp>()
        
        // Get all available MCP apps
        val allMcpApps = AppDirectory.getFeaturedApps()
        
        // Check for exact app name matches (complete word)
        for (app in allMcpApps) {
            val appNameLower = app.name.lowercase()
            if (appNameLower.length > 2 && isCompleteWordMatch(textLower, appNameLower)) {
                detectedMcpApps.add(app)
                android.util.Log.d("McpAppDetection", "Detected MCP app by name: ${app.name}")
            }
        }
        
        // If no exact matches, check for keyword matches in description
        if (detectedMcpApps.isEmpty()) {
            for (app in allMcpApps) {
                val keywords = extractKeywords(app.name, app.description)
                for (keyword in keywords) {
                    if (keyword.length > 3 && isCompleteWordMatch(textLower, keyword.lowercase())) {
                        if (!detectedMcpApps.contains(app)) {
                            detectedMcpApps.add(app)
                            android.util.Log.d("McpAppDetection", "Detected MCP app by keyword '$keyword': ${app.name}")
                        }
                    }
                }
            }
        }
        
        return detectedMcpApps
    }
    
    // Extract relevant keywords from app name and description for matching
    private fun extractKeywords(name: String, description: String): List<String> {
        val keywords = mutableListOf<String>()
        
        // Add individual words from the app name
        name.split(" ", "-", "_").forEach { word ->
            if (word.length > 3) {
                keywords.add(word)
            }
        }
        
        // Extract key nouns/verbs from description (common domain-specific words)
        val commonWords = listOf(
            "pizza", "pizzas", "food", "order", "ordering",
            "weather", "forecast", "temperature", "climate",
            "todo", "task", "tasks", "reminder", "reminders",
            "calendar", "event", "events", "schedule", "meeting",
            "calculator", "calculate", "math", "equation"
        )
        
        for (word in commonWords) {
            if (description.lowercase().contains(word)) {
                keywords.add(word)
            }
        }
        
        return keywords.distinct()
    }
    
    // Show combined suggestions for both installed apps and MCP apps
    private fun showCombinedAppSuggestions(installedApps: List<AppInfo>, mcpApps: List<McpApp>) {
        suggestionsLayout.removeAllViews()
        
        // Show installed apps first
        if (installedApps.size == 1 && mcpApps.isEmpty()) {
            // Single installed app - show with all intents
            val app = installedApps[0]
            val pill = createAppPill(app, "Open")
            suggestionsLayout.addView(pill)
            
            val intentCapabilities = appManager.getAppIntentCapabilities(app.packageName)
            for (capability in intentCapabilities) {
                val intentPill = createIntentPill(app, capability)
                suggestionsLayout.addView(intentPill)
            }
        } else {
            // Multiple apps or mix of installed and MCP apps
            for (app in installedApps) {
                val pill = createAppPill(app)
                suggestionsLayout.addView(pill)
            }
            
            // Show MCP app pills
            for (mcpApp in mcpApps) {
                val pill = createMcpAppPill(mcpApp)
                suggestionsLayout.addView(pill)
            }
        }
        
        suggestionsScrollView.visibility = android.view.View.VISIBLE
    }
    
    // Create a pill for an MCP app
    private fun createMcpAppPill(mcpApp: McpApp): android.widget.LinearLayout {
        val pill = android.widget.LinearLayout(this)
        pill.orientation = android.widget.LinearLayout.HORIZONTAL
        pill.gravity = android.view.Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * resources.displayMetrics.density).toInt() // Same as regular app pills
        val verticalPaddingPx = (8 * resources.displayMetrics.density).toInt()
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        
        // Check if app is connected
        val isConnected = connectedApps.containsKey(mcpApp.id)
        
        // Use same background as regular app pills for consistency
        pill.background = getDrawable(R.drawable.pill_background_compact)
        
        // Create icon view (load from URL or use placeholder)
        val iconView = android.widget.ImageView(this)
        val iconSizePx = (32 * resources.displayMetrics.density).toInt() // Same size as regular app pills
        iconView.layoutParams = android.widget.LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        iconView.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        
        // Load icon from URL if available
        if (!mcpApp.icon.isNullOrEmpty()) {
            val imageLoader = coil.ImageLoader(this)
            val request = coil.request.ImageRequest.Builder(this)
                .data(mcpApp.icon)
                .target(iconView)
                .placeholder(R.drawable.ic_apps)
                .error(R.drawable.ic_apps)
                .size(iconSizePx, iconSizePx)
                .build()
            imageLoader.enqueue(request)
        } else {
            iconView.setImageResource(R.drawable.ic_apps)
        }
        
        // Create text view
        val textView = android.widget.TextView(this)
        val status = if (isConnected) " ✓" else ""
        textView.text = mcpApp.name + status
        textView.setTextColor(android.graphics.Color.WHITE)
        textView.textSize = 14f
        textView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val textPaddingPx = (6 * resources.displayMetrics.density).toInt() // Same gap as regular app pills
        textView.setPadding(textPaddingPx, 0, 0, 0)
        
        // Add views to pill
        pill.addView(iconView)
        pill.addView(textView)
        
        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * resources.displayMetrics.density).toInt() // Same margin as regular app pills
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        // Add ripple effect for better touch feedback (same as regular app pills)
        pill.foreground = getDrawable(android.R.drawable.list_selector_background)
        
        // Click handler - insert app name as inline pill and show tools
        pill.setOnClickListener {
            // Insert the MCP app name into the text field as an inline pill
            insertMcpAppIntoText(mcpApp)
            
            // Show this MCP app with its tools (or just the app if not connected)
            android.util.Log.d("McpAppPill", "Showing MCP app details for: ${mcpApp.name}")
            showSingleMcpAppWithTools(mcpApp)
        }
        
        return pill
    }
    
    // Insert MCP app name into text field with inline pill formatting
    private fun insertMcpAppIntoText(mcpApp: McpApp) {
        val currentText = messageInput.text.toString()
        val cursorPosition = messageInput.selectionStart
        
        // Determine where to insert the app name
        val (insertPosition, textToInsert) = if (currentText.isEmpty()) {
            // Empty field - just insert the app name
            Pair(0, mcpApp.name)
        } else if (cursorPosition == currentText.length) {
            // At the end - add space before if needed
            val needsSpace = currentText.isNotEmpty() && !currentText.last().isWhitespace()
            val prefix = if (needsSpace) " " else ""
            Pair(currentText.length, prefix + mcpApp.name)
        } else {
            // In the middle - add spaces around if needed
            val needsSpaceBefore = cursorPosition > 0 && !currentText[cursorPosition - 1].isWhitespace()
            val needsSpaceAfter = cursorPosition < currentText.length && !currentText[cursorPosition].isWhitespace()
            val prefix = if (needsSpaceBefore) " " else ""
            val suffix = if (needsSpaceAfter) " " else ""
            Pair(cursorPosition, prefix + mcpApp.name + suffix)
        }
        
        // Build new text
        val newText = currentText.substring(0, insertPosition) + textToInsert + currentText.substring(insertPosition)
        
        // Set the text and apply inline pills
        isUpdatingPills = true
        messageInput.setText(newText)
        isUpdatingPills = false
        
        // Position cursor after the inserted app name
        val newCursorPosition = insertPosition + textToInsert.length
        messageInput.setSelection(newCursorPosition)
        
        // Trigger app detection to apply inline pill styling
        applyInlinePills(newText)
        
        // Keep focus on input
        messageInput.requestFocus()
    }
    
    // Show a single MCP app with its tools (similar to showAppSuggestions for regular apps)
    private fun showSingleMcpAppWithTools(mcpApp: McpApp) {
        suggestionsLayout.removeAllViews()
        
        // Check if app is connected
        val connectedApp = connectedApps[mcpApp.id]
        val isConnected = connectedApp != null
        
        // Always show the app pill first
        val appPill = createMcpAppPillForDisplay(mcpApp)
        suggestionsLayout.addView(appPill)
        
        // If connected and has tools, show all tool pills
        if (isConnected && connectedApp!!.tools.isNotEmpty()) {
            android.util.Log.d("McpAppPill", "App is connected with ${connectedApp.tools.size} tools")
            currentMcpAppContext = mcpApp.id
            
            // Create a pill for each tool
            for (tool in connectedApp.tools) {
                val toolPill = createMcpToolPill(mcpApp.id, tool)
                suggestionsLayout.addView(toolPill)
            }
        } else if (!isConnected) {
            // Automatically connect to the app
            android.util.Log.d("McpAppPill", "App is not connected, connecting automatically...")
            
            // Show a temporary "Connecting..." pill
            val connectingPill = createConnectingPill()
            suggestionsLayout.addView(connectingPill)
            
            // Connect in the background
            connectToMcpApp(mcpApp.id)
        }
        
        suggestionsScrollView.visibility = android.view.View.VISIBLE
    }
    
    // Create a display-only pill for MCP app (doesn't insert into text when clicked)
    private fun createMcpAppPillForDisplay(mcpApp: McpApp): android.widget.LinearLayout {
        val pill = android.widget.LinearLayout(this)
        pill.orientation = android.widget.LinearLayout.HORIZONTAL
        pill.gravity = android.view.Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * resources.displayMetrics.density).toInt()
        val verticalPaddingPx = (8 * resources.displayMetrics.density).toInt()
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        pill.background = getDrawable(R.drawable.pill_background_compact)
        
        // Create icon view
        val iconView = android.widget.ImageView(this)
        val iconSizePx = (32 * resources.displayMetrics.density).toInt()
        iconView.layoutParams = android.widget.LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        iconView.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        
        // Load icon from URL if available
        if (!mcpApp.icon.isNullOrEmpty()) {
            val imageLoader = coil.ImageLoader(this)
            val request = coil.request.ImageRequest.Builder(this)
                .data(mcpApp.icon)
                .target(iconView)
                .placeholder(R.drawable.ic_apps)
                .error(R.drawable.ic_apps)
                .size(iconSizePx, iconSizePx)
                .build()
            imageLoader.enqueue(request)
        } else {
            iconView.setImageResource(R.drawable.ic_apps)
        }
        
        // Create text view
        val textView = android.widget.TextView(this)
        textView.text = mcpApp.name
        textView.setTextColor(android.graphics.Color.WHITE)
        textView.textSize = 14f
        textView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val textPaddingPx = (6 * resources.displayMetrics.density).toInt()
        textView.setPadding(textPaddingPx, 0, 0, 0)
        
        pill.addView(iconView)
        pill.addView(textView)
        
        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * resources.displayMetrics.density).toInt()
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        // Add ripple effect
        pill.foreground = getDrawable(android.R.drawable.list_selector_background)
        
        // This pill doesn't do anything when clicked (just shows app info)
        pill.setOnClickListener {
            // Could open app details here if needed
            android.util.Log.d("McpAppPill", "App pill clicked: ${mcpApp.name}")
        }
        
        return pill
    }
    
    // Create a "Connecting..." pill to show during connection
    private fun createConnectingPill(): android.widget.LinearLayout {
        val pill = android.widget.LinearLayout(this)
        pill.orientation = android.widget.LinearLayout.HORIZONTAL
        pill.gravity = android.view.Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * resources.displayMetrics.density).toInt()
        val verticalPaddingPx = (8 * resources.displayMetrics.density).toInt()
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        pill.background = getDrawable(R.drawable.pill_background_compact)
        
        // Create text view
        val textView = android.widget.TextView(this)
        textView.text = "Connecting..."
        textView.setTextColor(android.graphics.Color.parseColor("#AAAAAA")) // Grey text
        textView.textSize = 14f
        textView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        
        pill.addView(textView)
        
        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * resources.displayMetrics.density).toInt()
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        return pill
    }
    
    // Create a pill for an MCP tool
    private fun createMcpToolPill(appId: String, tool: McpTool): android.widget.LinearLayout {
        val pill = android.widget.LinearLayout(this)
        pill.orientation = android.widget.LinearLayout.HORIZONTAL
        pill.gravity = android.view.Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * resources.displayMetrics.density).toInt()
        val verticalPaddingPx = (8 * resources.displayMetrics.density).toInt()
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        pill.background = getDrawable(R.drawable.pill_background_compact)
        
        // Get the MCP app to use its icon
        val mcpApp = connectedApps[appId]
        
        // Create icon view
        val iconView = android.widget.ImageView(this)
        val iconSizePx = (32 * resources.displayMetrics.density).toInt()
        iconView.layoutParams = android.widget.LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        iconView.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        
        // Load app icon for the tool
        if (mcpApp != null && !mcpApp.icon.isNullOrEmpty()) {
            val imageLoader = coil.ImageLoader(this)
            val request = coil.request.ImageRequest.Builder(this)
                .data(mcpApp.icon)
                .target(iconView)
                .placeholder(R.drawable.ic_apps)
                .error(R.drawable.ic_apps)
                .size(iconSizePx, iconSizePx)
                .build()
            imageLoader.enqueue(request)
        } else {
            iconView.setImageResource(R.drawable.ic_apps)
        }
        
        // Create text view for tool name
        val textView = android.widget.TextView(this)
        textView.text = tool.title ?: tool.name
        textView.setTextColor(android.graphics.Color.WHITE)
        textView.textSize = 14f
        textView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val textPaddingPx = (6 * resources.displayMetrics.density).toInt()
        textView.setPadding(textPaddingPx, 0, 0, 0)
        
        pill.addView(iconView)
        pill.addView(textView)
        
        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * resources.displayMetrics.density).toInt()
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        // Add ripple effect
        pill.foreground = getDrawable(android.R.drawable.list_selector_background)
        
        // Click to invoke this tool
        pill.setOnClickListener {
            android.util.Log.d("McpToolPill", "Tool clicked: ${tool.name}")
            
            // Clear the text input field - user has completed their action
            messageInput.setText("")
            
            // Reset MCP app context so we don't keep showing tool pills
            currentMcpAppContext = null
            
            // Hide the suggestion pills
            suggestionsScrollView.visibility = android.view.View.GONE
            
            // Invoke the tool
            invokeMcpTool(appId, tool.name, null)
        }
        
        return pill
    }
    
    // Show dialog to connect to an MCP app
    private fun showMcpAppConnectionDialog(mcpApp: McpApp) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle(mcpApp.name)
        builder.setMessage("${mcpApp.description}\n\nWould you like to connect to this app?")
        builder.setPositiveButton("Connect") { _, _ ->
            connectToMcpApp(mcpApp.id)
        }
        builder.setNeutralButton("Details") { _, _ ->
            // Open app details screen
            val intent = Intent(this, McpAppDetailsActivity::class.java)
            intent.putExtra("APP_ID", mcpApp.id)
            startActivityForResult(intent, REQUEST_CODE_APP_DETAILS)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
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
            // Launch the app directly
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                android.widget.Toast.makeText(this, "Could not launch ${app.appName}", android.widget.Toast.LENGTH_SHORT).show()
            }
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
    
    // Show MCP tool suggestions for the current app context
    private fun showMcpToolSuggestions(appId: String) {
        suggestionsLayout.removeAllViews()
        
        // Get the connected MCP app
        val mcpApp = connectedApps[appId]
        if (mcpApp == null || mcpApp.tools.isEmpty()) {
            suggestionsScrollView.visibility = android.view.View.GONE
            return
        }
        
        android.util.Log.d("MCP", "Showing tool suggestions for ${mcpApp.name}: ${mcpApp.tools.size} tools")
        
        // If we're collecting parameters, show parameter pills first
        if (activeParameterCollection != null) {
            val paramState = activeParameterCollection!!
            android.util.Log.d("MCP", "Parameter collection active, showing parameter pills")
            
            // Get tool's input schema
            val inputSchema = paramState.tool.inputSchema
            val properties = if (inputSchema.has("properties")) {
                inputSchema.getJSONObject("properties")
            } else {
                org.json.JSONObject()
            }
            
            // Show pills for required parameters that haven't been collected yet
            for (paramName in paramState.requiredParams) {
                if (!paramState.collectedParams.containsKey(paramName)) {
                    val paramSchema = if (properties.has(paramName)) {
                        properties.getJSONObject(paramName)
                    } else {
                        null
                    }
                    
                    val pill = createParameterPill(paramName, paramSchema, required = true)
                    suggestionsLayout.addView(pill)
                }
            }
            
            // Also show optional parameters if they exist
            if (properties.length() > 0) {
                val allParams = properties.keys().asSequence().toList()
                val optionalParams = allParams.filter { !paramState.requiredParams.contains(it) }
                
                for (paramName in optionalParams) {
                    if (!paramState.collectedParams.containsKey(paramName)) {
                        val paramSchema = properties.getJSONObject(paramName)
                        val pill = createParameterPill(paramName, paramSchema, required = false)
                        suggestionsLayout.addView(pill)
                    }
                }
            }
        }
        
        // Show all tools as pills
        for (tool in mcpApp.tools) {
            val pill = createMcpToolPill(appId, tool)
            suggestionsLayout.addView(pill)
        }
        
        suggestionsScrollView.visibility = android.view.View.VISIBLE
    }
    
    // Create a pill for a parameter
    private fun createParameterPill(paramName: String, paramSchema: org.json.JSONObject?, required: Boolean): android.widget.LinearLayout {
        val pill = android.widget.LinearLayout(this)
        pill.orientation = android.widget.LinearLayout.HORIZONTAL
        pill.gravity = android.view.Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * resources.displayMetrics.density).toInt()
        val verticalPaddingPx = (8 * resources.displayMetrics.density).toInt()
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        
        // Use the same background as tool pills
        pill.background = getDrawable(R.drawable.pill_background_compact)
        
        // Create icon view (parameter icon)
        val iconView = android.widget.ImageView(this)
        val iconSizePx = (32 * resources.displayMetrics.density).toInt()
        iconView.layoutParams = android.widget.LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        iconView.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        
        // Use a settings/parameter icon
        iconView.setImageResource(android.R.drawable.ic_menu_edit) // Built-in edit/input icon
        iconView.setColorFilter(android.graphics.Color.WHITE)
        
        pill.addView(iconView)
        
        // Create text view
        val textView = android.widget.TextView(this)
        textView.setTextColor(android.graphics.Color.WHITE)
        textView.textSize = 14f
        textView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val textPaddingPx = (6 * resources.displayMetrics.density).toInt()
        textView.setPadding(textPaddingPx, 0, 0, 0)
        
        // Format parameter name nicely
        val displayName = paramName.split("_").joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
        val description = paramSchema?.optString("description", "") ?: ""
        
        // Show parameter name (no special indicators, keep it clean like tool pills)
        textView.text = displayName
        
        pill.addView(textView)
        
        // Add layout params with right margin (same as tool pills)
        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * resources.displayMetrics.density).toInt()
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        // Add ripple effect for better touch feedback (same as tool pills)
        pill.foreground = getDrawable(android.R.drawable.list_selector_background)
        
        // When clicked, insert the parameter name/prompt into the input field
        pill.setOnClickListener {
            val paramType = paramSchema?.optString("type", "string") ?: "string"
            val prompt = when (paramType) {
                "string" -> "I want $displayName: "
                "number" -> "$displayName: "
                "boolean" -> if (description?.isNotEmpty() == true) "$description: " else "$displayName: "
                else -> "$displayName: "
            }
            
            messageInput.setText(prompt)
            messageInput.setSelection(messageInput.text.length)
            messageInput.requestFocus()
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
            1234 -> { // Overlay permission callback
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        // Permission granted, start the floating button
                        createFloatingOverlay()
                    } else {
                        Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            1001 -> { // Our intent callback (including app details)
                when (resultCode) {
                    RESULT_OK -> {
                        if (data != null) {
                            // Check if this is from app details screen
                            val action = data.getStringExtra("action")
                            val appId = data.getStringExtra("app_id")
                            
                            if (action == "connect" && appId != null) {
                                android.util.Log.d("MCP", "Connecting to app from details screen: $appId")
                                connectToMcpApp(appId)
                            } else {
                                // Handle returned data (e.g., from ACTION_PICK)
                                val selectedUri = data.data
                                if (selectedUri != null) {
                                    android.widget.Toast.makeText(this, "Selected: $selectedUri", android.widget.Toast.LENGTH_SHORT).show()
                                }
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
        // PRIORITY CHECK: If we're in MCP app context, show tool pills instead
        if (currentMcpAppContext != null) {
            android.util.Log.d("MCP", "In MCP context (showRecentApps), showing tool pills for: $currentMcpAppContext")
            showMcpToolSuggestions(currentMcpAppContext!!)
            return
        }
        
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
            // Launch the app directly
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                android.widget.Toast.makeText(this, "Could not launch ${app.appName}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        return pill
    }
    
    // Conversation History Methods (now handled by ConversationHistoryActivity)
    
    private fun loadConversation(conversationId: Long) {
        val messages = conversationManager.loadConversation(conversationId)
        if (messages != null) {
            // Clear current messages and load the conversation
            chatAdapter.clearMessages()
            
            // Clear MCP app context when loading a conversation
            currentMcpAppContext = null
            lastInvokedTool = null
            android.util.Log.d("MCP", "Cleared MCP app context (loading conversation)")
            
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
        
        // Clear MCP app context
        currentMcpAppContext = null
        lastInvokedTool = null
        android.util.Log.d("MCP", "Cleared MCP app context (new conversation)")
        
        // Update pills to show recent apps
        showRecentApps()
        android.util.Log.d("MCP", "Updated pills to show recent apps after starting new conversation")
        
        // Show welcome area
        updateWelcomeAreaVisibility()
        
        Toast.makeText(this, "Started new conversation", Toast.LENGTH_SHORT).show()
    }
    
    private fun handleIntentExtras() {
        val intent = intent
        android.util.Log.d("MainActivity", "Handling intent extras: ${intent.extras}")
        android.util.Log.d("MainActivity", "Intent action: ${intent.action}, data: ${intent.data}")
        
        // Handle Solana Action / Blink deep link
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val blinkUrl = intent.data.toString()
            android.util.Log.d("MainActivity", "Received blink URL: $blinkUrl")
            handleBlinkUrl(blinkUrl)
            return
        }
        
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
    
    private fun handleBlinkUrl(blinkUrl: String) {
        android.util.Log.d("MainActivity", "Processing blink URL: $blinkUrl")
        
        // Parse the blink URL
        val actionUrl = blinkService.parseBlinkUrl(blinkUrl)
        if (actionUrl == null) {
            android.util.Log.e("MainActivity", "Failed to parse blink URL")
            Toast.makeText(this, "Invalid Solana Action URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        android.util.Log.d("MainActivity", "Parsed action URL: $actionUrl")
        Toast.makeText(this, "Loading Solana Action...", Toast.LENGTH_SHORT).show()
        
        // Fetch metadata in background
        CoroutineScope(Dispatchers.Main).launch {
            val startTime = System.currentTimeMillis()
            
            val metadata = withContext(Dispatchers.IO) {
                blinkService.fetchBlinkMetadata(actionUrl)
            }
            
            val fetchTime = (System.currentTimeMillis() - startTime) / 1000.0
            android.util.Log.d("MainActivity", "Blink metadata fetch completed in ${fetchTime}s")
            
            if (metadata == null) {
                android.util.Log.e("MainActivity", "Failed to fetch blink metadata")
                Toast.makeText(this@MainActivity, "Failed to load action", Toast.LENGTH_SHORT).show()
                
                // Add error message to chat
                val errorMessage = ChatMessage(
                    text = "Failed to load Solana Action from $actionUrl",
                    isUser = false,
                    messageType = MessageType.TEXT
                )
                chatAdapter.addMessage(errorMessage)
                chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                return@launch
            }
            
            android.util.Log.d("MainActivity", "Successfully fetched blink metadata: ${metadata.title}")
            
            // Set the blink as a card in the cards overview
            chatAdapter.setBlinkCard(metadata)
            
            // Also add blink as a message to chat (hidden when cards are showing)
            val blinkMessage = ChatMessage(
                text = metadata.title,
                isUser = false,
                messageType = MessageType.BLINK,
                blinkMetadata = metadata
            )
            
            chatAdapter.addMessage(blinkMessage)
            
            // Show cards view to display the blink card
            if (!chatAdapter.getShowCards()) {
                chatAdapter.setShowCards(true)
            }
            
            chatRecyclerView.scrollToPosition(0) // Scroll to top to show the blink card
            
            Toast.makeText(this@MainActivity, "Solana Action loaded", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadTestBlinks() {
        // List of test blink URLs for development/testing
        val testBlinkUrls = listOf(
            "solana-action:https://rps.sendarcade.fun/api/actions/rps?_brf=5056cb65-8e5f-4812-bbfb-c887f555e91f&_bin=9d908db2-5996-4c4c-9650-37530601e8e0",
            "solana-action:https://sanctum.dial.to/trade/SOL-bonkSOL?_brf=d722f276-6bc6-4391-adef-5058c4d5b5c7&_bin=801cc219-0b70-4d05-ae96-2950b7081f7b",
            "solana-action:https://bonkblinks.com/api/actions/lock?_brf=a0898550-e7ec-408d-b721-fca000769498&_bin=ffafbecd-bb86-435a-8722-e45bf139eab5"
        )
        
        // Fetch each test blink in the background
        CoroutineScope(Dispatchers.Main).launch {
            testBlinkUrls.forEach { blinkUrl ->
                // Parse the blink URL
                val actionUrl = blinkService.parseBlinkUrl(blinkUrl)
                if (actionUrl == null) {
                    android.util.Log.e("MainActivity", "Failed to parse test blink URL: $blinkUrl")
                    return@forEach
                }
                
                android.util.Log.d("MainActivity", "Loading test blink: $actionUrl")
                
                // Fetch metadata in background
                val metadata = withContext(Dispatchers.IO) {
                    blinkService.fetchBlinkMetadata(actionUrl)
                }
                
                if (metadata == null) {
                    android.util.Log.e("MainActivity", "Failed to fetch test blink metadata: $actionUrl")
                    return@forEach
                }
                
                android.util.Log.d("MainActivity", "Successfully loaded test blink: ${metadata.title}")
                
                // Add blink directly as a card (not as a message)
                chatAdapter.addDynamicBlinkCard(metadata)
            }
            
            // Ensure cards view is shown to display all blinks
            if (!chatAdapter.getShowCards()) {
                chatAdapter.setShowCards(true)
            }
            
            android.util.Log.d("MainActivity", "✅ Test blinks loaded successfully")
        }
    }
    
    private fun handleBlinkActionExecution(actionHref: String, parameters: Map<String, String>) {
        android.util.Log.d("MainActivity", "Executing blink action: $actionHref with params: $parameters")
        
        Toast.makeText(this, "Connecting to wallet...", Toast.LENGTH_SHORT).show()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Step 1: Connect to wallet and get user's public key
                val walletAddress = walletManager.connectWallet()
                
                if (walletAddress == null) {
                    android.util.Log.e("MainActivity", "Failed to connect to wallet")
                    Toast.makeText(this@MainActivity, "Failed to connect to wallet", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                android.util.Log.d("MainActivity", "Connected to wallet: $walletAddress")
                Toast.makeText(this@MainActivity, "Wallet connected, creating transaction...", Toast.LENGTH_SHORT).show()
                
                // Step 2: Build the action URL with parameters (if any)
                var fullActionUrl = actionHref
                parameters.forEach { (key, value) ->
                    // Replace {param} placeholders in the URL
                    fullActionUrl = fullActionUrl.replace("{$key}", value)
                }
                
                android.util.Log.d("MainActivity", "Full action URL: $fullActionUrl")
                
                // Step 3: POST request to get the transaction
                val transactionBase64 = withContext(Dispatchers.IO) {
                    blinkService.executeBlinkAction(fullActionUrl, walletAddress, parameters)
                }
                
                if (transactionBase64 == null) {
                    android.util.Log.e("MainActivity", "Failed to get transaction from action")
                    Toast.makeText(this@MainActivity, "Failed to create transaction", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                android.util.Log.d("MainActivity", "Transaction created, requesting signature...")
                Toast.makeText(this@MainActivity, "Please sign the transaction...", Toast.LENGTH_SHORT).show()
                
                // Step 4: Sign the transaction via wallet (don't send yet)
                val signedTransaction = walletManager.signTransaction(transactionBase64)
                
                if (signedTransaction == null) {
                    android.util.Log.e("MainActivity", "Failed to sign transaction")
                    Toast.makeText(this@MainActivity, "Transaction signing failed", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                android.util.Log.d("MainActivity", "Transaction signed, submitting to action endpoint...")
                Toast.makeText(this@MainActivity, "Submitting transaction...", Toast.LENGTH_SHORT).show()
                
                // Step 5: Submit signed transaction back to the action endpoint
                val signature = withContext(Dispatchers.IO) {
                    blinkService.submitSignedTransaction(fullActionUrl, signedTransaction, walletAddress)
                }
                
                if (signature == null) {
                    android.util.Log.e("MainActivity", "Failed to submit signed transaction")
                    Toast.makeText(this@MainActivity, "Transaction submission failed", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                android.util.Log.d("MainActivity", "Transaction successful! Signature: $signature")
                Toast.makeText(
                    this@MainActivity, 
                    "Transaction successful!\n$signature", 
                    Toast.LENGTH_LONG
                ).show()
                
                // Add success message to chat
                val successMessage = ChatMessage(
                    text = "✅ Transaction successful!\nSignature: $signature",
                    isUser = false,
                    messageType = MessageType.TEXT
                )
                chatAdapter.addMessage(successMessage)
                chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error executing blink action: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun invokeMcpTool(appId: String, toolName: String, providedParameters: Map<String, Any>? = null) {
        android.util.Log.d("MCP", "Invoking tool: $toolName from app: $appId")
        
        // Get the connected app
        val app = connectedApps[appId]
        if (app == null) {
            android.util.Log.e("MCP", "App not connected: $appId")
            Toast.makeText(this, "App not connected", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Find the tool
        val tool = app.tools.find { it.name == toolName }
        if (tool == null) {
            android.util.Log.e("MCP", "Tool not found: $toolName")
            Toast.makeText(this, "Tool not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val toolTitle = tool.title ?: tool.name
        
        // Set MCP app context for showing relevant tool pills
        currentMcpAppContext = appId
        android.util.Log.d("MCP", "Set MCP context to: $appId")
        
        // 1. Add user message to chat
        val userMessage = ChatMessage(
            text = toolTitle,
            isUser = true,
            messageType = MessageType.TEXT
        )
        chatAdapter.addMessage(userMessage)
        chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        
        // 2. Hide cards and show chat
        chatAdapter.setShowCards(false)
        cardTypeTabLayout.visibility = View.GONE
        updateCardsVisibility()
        
        // 3. Update pills immediately to show MCP tool pills
        showMcpToolSuggestions(appId)
        android.util.Log.d("MCP", "Updated pills to show MCP tools")
        
        // 4. Check if tool has required parameters
        val requiredParams = getRequiredParameters(tool)
        android.util.Log.d("MCP", "Tool has ${requiredParams.size} required parameters: $requiredParams")
        
        // If we have required parameters and no provided parameters, try to infer from conversation
        if (requiredParams.isNotEmpty() && providedParameters == null) {
            android.util.Log.d("MCP", "Tool has required params, attempting to extract from conversation history")
            
            // Try to extract parameters from conversation history using Grok
            chatAdapter.showTypingIndicator()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val inferredParams = inferParametersFromConversation(tool, requiredParams)
                    
                    withContext(Dispatchers.Main) {
                        chatAdapter.hideTypingIndicator()
                        
                        if (inferredParams.size == requiredParams.size) {
                            // All parameters inferred, invoke immediately
                            android.util.Log.d("MCP", "Successfully inferred all parameters from conversation: $inferredParams")
                            executeToolInvocation(appId, tool, inferredParams)
                        } else {
                            // Some parameters missing, start conversational collection
                            android.util.Log.d("MCP", "Could not infer all parameters, starting collection. Inferred: $inferredParams")
                            startParameterCollection(appId, tool, requiredParams, inferredParams)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MCP", "Error inferring parameters", e)
                    withContext(Dispatchers.Main) {
                        chatAdapter.hideTypingIndicator()
                        // Fallback to conversational collection
                        startParameterCollection(appId, tool, requiredParams, emptyMap())
                    }
                }
            }
            return
        }
        
        // Otherwise, invoke the tool immediately (either no required params, or params already provided)
        android.util.Log.d("MCP", "Invoking tool immediately (no required params or params provided)")
        executeToolInvocation(appId, tool, providedParameters)
    }
    
    private fun getRequiredParameters(tool: McpTool): List<String> {
        val inputSchema = tool.inputSchema
        val required = if (inputSchema.has("required")) {
            val reqArray = inputSchema.getJSONArray("required")
            (0 until reqArray.length()).map { reqArray.getString(it) }
        } else {
            emptyList()
        }
        return required
    }
    
    private fun startParameterCollection(
        appId: String, 
        tool: McpTool, 
        requiredParams: List<String>,
        alreadyCollected: Map<String, Any> = emptyMap()
    ) {
        android.util.Log.d("MCP", "Starting parameter collection for ${tool.name} with ${alreadyCollected.size} pre-collected params")
        
        // Initialize parameter collection state
        activeParameterCollection = ParameterCollectionState(
            appId = appId,
            tool = tool,
            requiredParams = requiredParams,
            collectedParams = alreadyCollected.toMutableMap()
        )
        android.util.Log.d("MCP", "Active parameter collection state initialized")
        
        // Update pills to show parameter suggestions immediately
        // Pills will communicate what parameters are needed - no verbose message needed
        showMcpToolSuggestions(appId)
        
        // If some parameters were already collected, and all are collected, invoke immediately
        if (areAllParametersCollected(activeParameterCollection!!)) {
            android.util.Log.d("MCP", "All parameters already collected! Invoking tool...")
            executeToolInvocation(appId, tool, alreadyCollected)
            activeParameterCollection = null
            // Refresh pills after parameter collection ends
            showMcpToolSuggestions(appId)
        }
        // Otherwise, pills will show what's needed - user will see and respond
    }
    
    private suspend fun continueParameterCollection(userMessage: String): String {
        val state = activeParameterCollection ?: return "Error: No active parameter collection"
        
        android.util.Log.d("MCP", "Continuing parameter collection with user message: $userMessage")
        
        // Collect the parameter value directly from user message (no AI generation)
        for (paramName in state.requiredParams) {
            if (!state.collectedParams.containsKey(paramName)) {
                // Assume the user's message is the value for the next missing parameter
                state.collectedParams[paramName] = userMessage
                android.util.Log.d("MCP", "Collected parameter '$paramName' = '$userMessage'")
                break // Only collect one parameter at a time
            }
        }
        
        android.util.Log.d("MCP", "Updated collected params: ${state.collectedParams}")
        
        // Update pills to reflect newly collected parameters
        withContext(Dispatchers.Main) {
            currentMcpAppContext?.let { showMcpToolSuggestions(it) }
        }
        
        // Check if all parameters are collected
        if (areAllParametersCollected(state)) {
            android.util.Log.d("MCP", "All parameters collected! Invoking tool...")
            
            // Invoke the tool with collected parameters
            withContext(Dispatchers.Main) {
                executeToolInvocation(state.appId, state.tool, state.collectedParams)
                activeParameterCollection = null
                // Refresh pills after parameter collection ends
                currentMcpAppContext?.let { showMcpToolSuggestions(it) }
            }
            
            // Return a simple confirmation (will be shown as AI message)
            return "✓"
        }
        
        // If more parameters needed, return a simple prompt for the next one
        val nextMissingParam = state.requiredParams.find { !state.collectedParams.containsKey(it) }
        return if (nextMissingParam != null) {
            "✓" // Just a check mark, pills will show the next parameter
        } else {
            "✓"
        }
    }
    
    private fun extractParametersFromConversation(state: ParameterCollectionState, aiResponse: String) {
        // Simple extraction: look for key-value patterns in the conversation
        // In a real implementation, we'd use NLP or have Grok return structured data
        
        android.util.Log.d("MCP", "Extracting parameters from conversation")
        android.util.Log.d("MCP", "Current collected params: ${state.collectedParams}")
        
        // For now, we'll use a simple heuristic:
        // Look at the last user message in history and try to match it with required params
        val lastUserMessages = state.conversationHistory.filter { it.first == "user" }.map { it.second }
        
        if (lastUserMessages.isNotEmpty()) {
            val lastMessage = lastUserMessages.last()
            android.util.Log.d("MCP", "Last user message: $lastMessage")
            
            // Try to match with required parameters
            for (paramName in state.requiredParams) {
                if (!state.collectedParams.containsKey(paramName)) {
                    // Simple heuristic: assume the last user message is the value for the next missing parameter
                    state.collectedParams[paramName] = lastMessage
                    android.util.Log.d("MCP", "Collected parameter '$paramName' = '$lastMessage'")
                    break // Only collect one parameter at a time
                }
            }
        }
        
        android.util.Log.d("MCP", "Updated collected params: ${state.collectedParams}")
    }
    
    private fun areAllParametersCollected(state: ParameterCollectionState): Boolean {
        val allCollected = state.requiredParams.all { state.collectedParams.containsKey(it) }
        android.util.Log.d("MCP", "All parameters collected: $allCollected (${state.collectedParams.size}/${state.requiredParams.size})")
        return allCollected
    }
    
    private fun buildParameterCollectionPrompt(tool: McpTool, requiredParams: List<String>): String {
        val params = requiredParams.joinToString(", ")
        return "To use the ${tool.title ?: tool.name} tool, I need the following information: $params. Please provide these details."
    }
    
    private suspend fun inferParametersFromConversation(tool: McpTool, requiredParams: List<String>): Map<String, Any> {
        android.util.Log.d("MCP", "Attempting to infer parameters from conversation for ${tool.name}")
        
        // Build conversation history
        val conversationHistory = buildConversationHistory()
        
        if (conversationHistory.isEmpty()) {
            android.util.Log.d("MCP", "No conversation history available")
            return emptyMap()
        }
        
        // Build parameter descriptions
        val inputSchema = tool.inputSchema
        val properties = if (inputSchema.has("properties")) {
            inputSchema.getJSONObject("properties")
        } else {
            org.json.JSONObject()
        }
        
        val paramDescriptions = requiredParams.map { paramName ->
            if (properties.has(paramName)) {
                val paramSchema = properties.getJSONObject(paramName)
                val paramDesc = paramSchema.optString("description", "")
                val paramType = paramSchema.optString("type", "string")
                "$paramName ($paramType): $paramDesc"
            } else {
                paramName
            }
        }
        
        // Ask Grok to extract parameters from conversation
        val systemPrompt = """Extract parameters for "${tool.title ?: tool.name}" from the conversation.

Required:
${paramDescriptions.joinToString("\n") { "- $it" }}

Return ONLY JSON with extracted values. Example: {"topping": "pepperoni"}
If nothing found: {}"""
        
        try {
            val conversationText = conversationHistory.joinToString("\n") { (role, content) ->
                "${role.uppercase()}: $content"
            }
            
            val fullPrompt = "$systemPrompt\n\nCONVERSATION:\n$conversationText"
            
            val grokResponse = externalAIService.generateResponse(fullPrompt, ModelType.EXTERNAL_GROK)
            android.util.Log.d("MCP", "Grok parameter inference response: $grokResponse")
            
            // Extract JSON from response
            val jsonMatch = Regex("\\{[^}]*\\}").find(grokResponse)
            if (jsonMatch != null) {
                val extractedJson = org.json.JSONObject(jsonMatch.value)
                val params = mutableMapOf<String, Any>()
                
                extractedJson.keys().forEach { key ->
                    if (requiredParams.contains(key)) {
                        params[key] = extractedJson.get(key)
                    }
                }
                
                android.util.Log.d("MCP", "Successfully inferred ${params.size} parameters: $params")
                return params
            } else {
                android.util.Log.e("MCP", "Could not extract JSON from Grok response")
                return emptyMap()
            }
        } catch (e: Exception) {
            android.util.Log.e("MCP", "Error inferring parameters from conversation", e)
            return emptyMap()
        }
    }
    
    private suspend fun handleMcpContextMessage(userMessage: String): String {
        android.util.Log.d("MCP", "Handling message in MCP context: $userMessage")
        
        val app = currentMcpAppContext?.let { connectedApps[it] }
        if (app == null) {
            android.util.Log.e("MCP", "MCP context app not found")
            return "Error: App context lost"
        }
        
        // Use Grok to intelligently route the message
        val routingDecision = analyzeMessageWithGrok(userMessage, app)
        
        return when (routingDecision.action) {
            "invoke_tool" -> {
                android.util.Log.d("MCP", "Grok decision: Invoke tool '${routingDecision.toolName}'")
                if (routingDecision.toolName != null) {
                    withContext(Dispatchers.Main) {
                        invokeMcpTool(app.id, routingDecision.toolName)
                    }
                    "" // Tool invoked, no text response
                } else {
                    "I couldn't determine which tool to invoke. Available tools: ${app.tools.joinToString(", ") { it.title ?: it.name }}"
                }
            }
            "modify_parameters" -> {
                android.util.Log.d("MCP", "Grok decision: Modify parameters")
                if (lastInvokedTool != null && routingDecision.parameters != null) {
                    handleToolParameterModification(userMessage, lastInvokedTool!!)
                } else {
                    generateContextualResponse(userMessage, app, routingDecision.response)
                }
            }
            "respond" -> {
                android.util.Log.d("MCP", "Grok decision: Generate response")
                generateContextualResponse(userMessage, app, routingDecision.response)
            }
            else -> {
                // Fallback
                generateContextualResponse(userMessage, app, null)
            }
        }
    }
    
    private data class RoutingDecision(
        val action: String,           // "invoke_tool", "modify_parameters", "respond"
        val toolName: String? = null, // Tool to invoke (if action is invoke_tool)
        val parameters: Map<String, Any>? = null, // Parameters to modify
        val response: String? = null  // Response text (if action is respond)
    )
    
    private suspend fun analyzeMessageWithGrok(userMessage: String, app: McpApp): RoutingDecision {
        // Build tool information for Grok
        val toolsJson = app.tools.joinToString("\n") { tool ->
            val paramsJson = if (tool.inputSchema.has("properties")) {
                val props = tool.inputSchema.getJSONObject("properties")
                props.keys().asSequence().joinToString(", ")
            } else {
                "none"
            }
            """  - name: "${tool.name}"
    title: "${tool.title ?: tool.name}"
    description: "${tool.description ?: ""}"
    parameters: [$paramsJson]"""
        }
        
        val lastToolInfo = lastInvokedTool?.let { 
            "Last invoked tool: ${it.tool.name} with parameters: ${it.lastParameters}"
        } ?: "No tool invoked yet"
        
        val conversationHistory = buildConversationHistory()
        val recentContext = conversationHistory.takeLast(5).joinToString("\n") { (role, content) ->
            "$role: $content"
        }
        
        val systemPrompt = """Route user intent for "${app.name}".

Tools:
$toolsJson

$lastToolInfo

Recent:
$recentContext

Actions:
1. invoke_tool - user wants to use/show a tool
2. modify_parameters - user wants to change current tool params
3. respond - answer questions, general chat

Return ONLY JSON:
{
  "action": "invoke_tool|modify_parameters|respond",
  "tool_name": "name (if invoke_tool)",
  "reasoning": "why"
}

User: "$userMessage"
Decision:"""

        try {
            val grokResponse = externalAIService.generateResponse(systemPrompt, ModelType.EXTERNAL_GROK)
            android.util.Log.d("MCP", "Grok routing response: $grokResponse")
            
            // Extract JSON from response
            val jsonMatch = Regex("\\{[\\s\\S]*?\\}").find(grokResponse)
            if (jsonMatch != null) {
                val json = org.json.JSONObject(jsonMatch.value)
                val action = json.optString("action", "respond")
                val toolName = json.optString("tool_name", null)
                val reasoning = json.optString("reasoning", "")
                
                android.util.Log.d("MCP", "Parsed decision - Action: $action, Tool: $toolName, Reasoning: $reasoning")
                
                // Parse parameters if present
                val parameters = if (json.has("parameters")) {
                    val paramsJson = json.getJSONObject("parameters")
                    val map = mutableMapOf<String, Any>()
                    paramsJson.keys().forEach { key ->
                        map[key] = paramsJson.get(key)
                    }
                    map
                } else {
                    null
                }
                
                return RoutingDecision(
                    action = action,
                    toolName = toolName.takeIf { !it.isNullOrEmpty() },
                    parameters = parameters
                )
            } else {
                android.util.Log.e("MCP", "Could not parse JSON from Grok response")
                return RoutingDecision(action = "respond")
            }
        } catch (e: Exception) {
            android.util.Log.e("MCP", "Error analyzing message with Grok", e)
            return RoutingDecision(action = "respond")
        }
    }
    
    private suspend fun generateContextualResponse(
        userMessage: String, 
        app: McpApp,
        suggestedResponse: String?
    ): String {
        // If Grok already provided a response, we could use it, but let's generate fresh with full context
        val toolList = app.tools.joinToString("\n") { tool ->
            "- **${tool.title ?: tool.name}**: ${tool.description ?: "No description"}"
        }
        
        val mcpContext = """You're helping with the "${app.name}" Mini App.

About: ${app.description}

Tools available:
$toolList

Keep responses concise. Don't narrate tool actions - the user sees them. Answer questions naturally."""

        val conversationHistory = buildConversationHistory()
        
        // Add MCP context as a system message
        val contextualizedHistory = listOf("system" to mcpContext) + conversationHistory
        
        return externalAIService.generateResponseWithHistory(userMessage, ModelType.EXTERNAL_GROK, contextualizedHistory)
    }
    
    private fun buildConversationHistory(): List<Pair<String, String>> {
        // Get all messages from the chat adapter
        val messages = chatAdapter.getMessages()
        
        // Convert to conversation history format (role, content)
        // Filter out MCP WebView messages as they're just UI rendering
        val history = mutableListOf<Pair<String, String>>()
        
        for (message in messages) {
            when (message.messageType) {
                MessageType.TEXT -> {
                    val role = if (message.isUser) "user" else "assistant"
                    history.add(role to message.text)
                }
                MessageType.MCP_WEBVIEW -> {
                    // Skip WebView messages, but could add a summary like:
                    // history.add("assistant" to "[Displayed ${message.mcpWebViewData?.toolName} result]")
                    // For now, skip to keep context cleaner
                }
                MessageType.BLINK -> {
                    // Skip blink messages
                }
            }
        }
        
        android.util.Log.d("MCP", "Built conversation history: ${history.size} messages from ${messages.size} total messages")
        return history
    }
    
    private fun shouldReinvokeTool(userMessage: String): Boolean {
        // Simple heuristic: check if user message contains words indicating a change/modification
        val modificationKeywords = listOf(
            "change", "switch", "update", "modify", "different", "instead", 
            "actually", "wait", "no", "rather", "prefer"
        )
        val lowerMessage = userMessage.lowercase()
        return modificationKeywords.any { lowerMessage.contains(it) }
    }
    
    private suspend fun handleToolParameterModification(userMessage: String, lastTool: LastInvokedTool): String {
        android.util.Log.d("MCP", "Handling tool parameter modification for: ${lastTool.tool.name}")
        
        // Use Grok to extract the new parameter values from the user message
        val toolSchemaJson = org.json.JSONObject().apply {
            put("tool_name", lastTool.tool.name)
            put("tool_title", lastTool.tool.title ?: lastTool.tool.name)
            put("tool_description", lastTool.tool.description ?: "")
            put("input_schema", lastTool.tool.inputSchema)
            put("current_parameters", org.json.JSONObject(lastTool.lastParameters))
        }
        
        // Ask Grok to extract parameter changes from user message
        val systemPrompt = """Extract parameter changes from user message.

Current parameters: ${lastTool.lastParameters}
User message: "$userMessage"

Return ONLY JSON with CHANGED values. Example: {"topping": "anchovies"}
If unclear: {}"""
        
        try {
            val grokResponse = externalAIService.generateResponse(systemPrompt, ModelType.EXTERNAL_GROK)
            android.util.Log.d("MCP", "Grok parameter extraction response: $grokResponse")
            
            // Try to parse JSON from response
            val jsonMatch = Regex("\\{[^}]+\\}").find(grokResponse)
            if (jsonMatch != null) {
                val extractedParams = org.json.JSONObject(jsonMatch.value)
                android.util.Log.d("MCP", "Extracted parameters: $extractedParams")
                
                // Merge with last parameters
                val updatedParams = lastTool.lastParameters.toMutableMap()
                extractedParams.keys().forEach { key ->
                    updatedParams[key] = extractedParams.get(key)
                }
                
                android.util.Log.d("MCP", "Updated parameters: $updatedParams")
                
                // Re-invoke the tool with updated parameters
                withContext(Dispatchers.Main) {
                    executeToolInvocation(lastTool.appId, lastTool.tool, updatedParams)
                }
                
                return "" // Don't show additional AI response, just re-invoke the tool
            } else {
                android.util.Log.e("MCP", "Could not extract JSON from Grok response")
                return "I couldn't understand which parameter you want to change. Could you be more specific?"
            }
        } catch (e: Exception) {
            android.util.Log.e("MCP", "Error handling parameter modification", e)
            return "Sorry, I encountered an error processing your request: ${e.message}"
        }
    }
    
    private fun executeToolInvocation(appId: String, tool: McpTool, providedParameters: Map<String, Any>?) {
        android.util.Log.d("MCP", "Executing tool invocation for: ${tool.name}")
        
        // Get the connected app
        val app = connectedApps[appId]
        if (app == null) {
            android.util.Log.e("MCP", "App not connected during execution: $appId")
            return
        }
        
        // Show typing indicator
        chatAdapter.showTypingIndicator()
        
        // Build arguments: use provided parameters if available, otherwise use defaults
        val argumentsMap = if (providedParameters != null) {
            android.util.Log.d("MCP", "Using provided parameters: $providedParameters")
            providedParameters.toMutableMap()
        } else {
            android.util.Log.d("MCP", "Building default arguments from schema")
            val arguments = buildToolArguments(tool)
            android.util.Log.d("MCP", "Built arguments: $arguments")
            
            // Convert JSONObject to Map
            val map = mutableMapOf<String, Any>()
            arguments.keys().forEach { key ->
                map[key] = arguments.get(key)
            }
            map
        }
        
        android.util.Log.d("MCP", "Final arguments for tool invocation: $argumentsMap")
        android.util.Log.d("MCP", "Output template: ${tool._meta?.get("openai/outputTemplate")}")
        
        // Execute tool invocation asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Call the MCP tool on IO
                val result = mcpService.callTool(app.serverUrl, tool.name, argumentsMap)
                android.util.Log.d("MCP", "Tool result: $result")
                
                // Get output template from tool metadata
                val outputTemplate = tool._meta?.get("openai/outputTemplate") as? String
                
                if (outputTemplate != null && outputTemplate.startsWith("ui://")) {
                    // Fetch the UI resource with inline display mode on IO
                    val resourceUri = outputTemplate
                    android.util.Log.d("MCP", "Fetching inline resource: $resourceUri")
                    
                    val resource = mcpService.readResource(app.serverUrl, resourceUri, displayMode = "inline")
                    android.util.Log.d("MCP", "Resource fetched: ${resource?.uri}")
                    
                    if (resource != null && resource.text != null) {
                        android.util.Log.d("MCP", "Resource URI: ${resource.uri}, mimeType: ${resource.mimeType}")
                        
                        // Build complete HTML document with Skybridge polyfill
                        val completeHtml = result?.let { buildHtmlWithPolyfill(resource.text!!, it) } ?: ""
                        
                        // Add WebView message to chat on Main
                        withContext(Dispatchers.Main) {
                            chatAdapter.hideTypingIndicator()
                            
                            val webViewMessage = ChatMessage(
                                text = "", // No text needed
                                isUser = false,
                                messageType = MessageType.MCP_WEBVIEW,
                                mcpWebViewData = McpWebViewData(
                                    appId = appId,
                                    toolName = tool.name,
                                    htmlContent = completeHtml,
                                    baseUrl = "https://persistent.oaistatic.com/",
                                    serverUrl = app.serverUrl,
                                    outputTemplate = outputTemplate,
                                    toolArguments = argumentsMap
                                )
                            )
                            chatAdapter.addMessage(webViewMessage)
                            conversationManager.addMessage(webViewMessage)
                            conversationManager.saveCurrentConversation()
                            
                            // Save last invoked tool
                            lastInvokedTool = LastInvokedTool(appId, tool, argumentsMap)
                            android.util.Log.d("MCP", "Saved last invoked tool: ${tool.name} with params: $argumentsMap")
                            
                            // Scroll to the new message
                            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                        }
                    } else {
                        throw Exception("No resources returned")
                    }
                } else {
                    // No UI template, show text result
                    throw Exception("No UI template available")
                }
            } catch (e: Exception) {
                android.util.Log.e("MCP", "Error invoking tool", e)
                withContext(Dispatchers.Main) {
                    chatAdapter.hideTypingIndicator()
                    val errorMessage = ChatMessage(
                        text = "Failed to invoke tool: ${e.message}",
                        isUser = false,
                        messageType = MessageType.TEXT
                    )
                    chatAdapter.addMessage(errorMessage)
                    chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }
            }
        }
    }
    
    private fun buildHtmlWithPolyfill(resourceText: String, toolResult: McpToolResult): String {
        // Build Skybridge polyfill (similar to McpToolInvocationActivity)
        val toolInput = org.json.JSONObject()
        val toolOutput = org.json.JSONObject()
        
        // Extract tool output from result
        try {
            val firstContent = toolResult.content.firstOrNull()
            if (firstContent is McpContent.Text) {
                toolOutput.put("text", firstContent.text)
            }
        } catch (e: Exception) {
            android.util.Log.e("MCP", "Error extracting tool output", e)
        }
        
        val skybridgePolyfill = """<script>
(function() {
    console.log('[Skybridge] Initializing OpenAI global API...');
    
    var openaiAPI = {
        // Layout globals
        displayMode: 'inline',
        theme: 'dark',
        locale: '${java.util.Locale.getDefault().toLanguageTag()}',
        maxHeight: 99999,
        safeArea: { insets: { top: 0, bottom: 0, left: 0, right: 0 } },
        userAgent: { 
            device: { type: 'mobile', os: 'android', screenWidth: window.screen.width, screenHeight: window.screen.height },
            capabilities: { hover: false, touch: true, mobile: true, desktop: false }
        },
        
        // State
        toolInput: $toolInput,
        toolOutput: $toolOutput,
        toolResponseMetadata: null,
        widgetState: null,
        
        // API Methods - Exact OpenAI spec
        callTool: function(name, args) { 
            console.log('[Skybridge] callTool called:', name, args);
            if (window.AndroidBridge && window.AndroidBridge.callTool) {
                window.AndroidBridge.callTool(name, JSON.stringify(args || {}));
            }
            return Promise.resolve({ 
                content: [], 
                isError: false 
            });
        },
        
        sendFollowUpMessage: function(payload) { 
            console.log('[Skybridge] sendFollowUpMessage called:', payload);
            if (window.AndroidBridge && window.AndroidBridge.sendFollowUpMessage) {
                window.AndroidBridge.sendFollowUpMessage(payload.prompt || '');
            }
            return Promise.resolve();
        },
        
        openExternal: function(payload) { 
            console.log('[Skybridge] openExternal called:', payload);
            if (window.AndroidBridge && window.AndroidBridge.openExternal) {
                window.AndroidBridge.openExternal(payload.href || '');
            }
        },
        
        requestDisplayMode: function(payload) { 
            console.log('[Skybridge] requestDisplayMode called:', payload);
            var requestedMode = payload.mode || 'inline';
            if (window.AndroidBridge && window.AndroidBridge.requestDisplayMode) {
                window.AndroidBridge.requestDisplayMode(requestedMode);
            }
            // On mobile, PiP is coerced to fullscreen
            var grantedMode = requestedMode === 'pip' ? 'fullscreen' : requestedMode;
            return Promise.resolve({ mode: grantedMode });
        },
        
        setWidgetState: function(state) { 
            console.log('[Skybridge] setWidgetState called:', state);
            if (window.AndroidBridge && window.AndroidBridge.setWidgetState) {
                window.AndroidBridge.setWidgetState(JSON.stringify(state || {}));
            }
            openaiAPI.widgetState = state;
            // Dispatch globals update event
            var event = new CustomEvent('openai:set_globals', { 
                detail: { globals: { widgetState: state } } 
            });
            window.dispatchEvent(event);
            return Promise.resolve();
        },
        
        sendEvent: function(eventName, data) { 
            console.log('[Skybridge] sendEvent called:', eventName, data);
            // Custom events can be dispatched here
        }
    };
    
    window.openai = openaiAPI;
    window.webplus = openaiAPI;
    Object.defineProperty(window, 'openai', { configurable: false, writable: false, value: openaiAPI });
    
    // Dispatch initial set_globals event with correct structure
    var setGlobalsEvent = new CustomEvent('openai:set_globals', { 
        detail: { globals: openaiAPI } 
    });
    var setGlobalsEventWebplus = new CustomEvent('webplus:set_globals', { 
        detail: { globals: openaiAPI } 
    });
    window.dispatchEvent(setGlobalsEvent);
    window.dispatchEvent(setGlobalsEventWebplus);
    console.log('[Skybridge] ✓ window.openai and window.webplus initialized');
    
    // Redispatch after a brief delay for late-loading scripts
    setTimeout(function() {
        window.dispatchEvent(setGlobalsEvent);
        window.dispatchEvent(setGlobalsEventWebplus);
    }, 10);
})();
</script>"""
        
        val earlyStub = """<script>
window.openai = window.openai || {
    displayMode: 'inline',
    theme: 'dark',
    locale: '${java.util.Locale.getDefault().toLanguageTag()}',
    maxHeight: 99999,
    safeArea: { insets: { top: 0, bottom: 0, left: 0, right: 0 } },
    userAgent: { 
        device: { type: 'mobile', os: 'android', screenWidth: window.screen.width, screenHeight: window.screen.height },
        capabilities: { hover: false, touch: true, mobile: true, desktop: false }
    },
    toolInput: {},
    toolOutput: {},
    toolResponseMetadata: null,
    widgetState: null,
    callTool: function() { return Promise.resolve({}) },
    sendFollowUpMessage: function() { return Promise.resolve() },
    openExternal: function() {},
    requestDisplayMode: function() { return Promise.resolve({ mode: 'inline' }) },
    setWidgetState: function() { return Promise.resolve() },
    sendEvent: function() {}
};
window.webplus = window.openai;
</script>"""
        
        return """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
$earlyStub
<style>
body {
    margin: 0;
    padding: 0;
    background-color: #000000;
    color: #FFFFFF !important;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    max-width: 428px;
}
/* Ensure all text elements are visible on dark background */
* {
    color: inherit;
}
h1, h2, h3, h4, h5, h6, p, span, div, a, button, label {
    color: #FFFFFF !important;
}
/* Override any black text */
[style*="color: black"],
[style*="color: #000"],
[style*="color: rgb(0, 0, 0)"] {
    color: #FFFFFF !important;
}
</style>
$skybridgePolyfill
</head>
<body>
$resourceText
</body>
</html>"""
    }
    
    private fun buildToolArguments(tool: McpTool): org.json.JSONObject {
        // Build arguments based on the tool's input schema
        val inputSchema = tool.inputSchema
        val arguments = org.json.JSONObject()
        
        android.util.Log.d("MCP", "========== BUILDING ARGUMENTS FOR ${tool.name} ==========")
        android.util.Log.d("MCP", "Full input schema: $inputSchema")
        
        // Check if the schema has required properties
        if (inputSchema.has("properties")) {
            val properties = inputSchema.getJSONObject("properties")
            val required = if (inputSchema.has("required")) {
                val reqArray = inputSchema.getJSONArray("required")
                (0 until reqArray.length()).map { reqArray.getString(it) }.toSet()
            } else {
                emptySet()
            }
            
            android.util.Log.d("MCP", "Properties: ${properties.keys().asSequence().toList()}")
            android.util.Log.d("MCP", "Required fields: $required")
            
            // For each property, provide a default value
            properties.keys().forEach { key ->
                val property = properties.getJSONObject(key)
                val type = property.optString("type", "string")
                val description = property.optString("description", "")
                
                android.util.Log.d("MCP", "  Property '$key': type=$type, required=${required.contains(key)}, desc=$description")
                
                // Provide default values for ALL fields (not just required)
                // This ensures the server has complete input
                when {
                    key == "pizzaTopping" -> {
                        arguments.put(key, "pepperoni")
                        android.util.Log.d("MCP", "    → Set to: pepperoni")
                    }
                    key == "latitude" -> {
                        arguments.put(key, 37.7749) // San Francisco
                        android.util.Log.d("MCP", "    → Set to: 37.7749")
                    }
                    key == "longitude" -> {
                        arguments.put(key, -122.4194)
                        android.util.Log.d("MCP", "    → Set to: -122.4194")
                    }
                    key == "location" -> {
                        arguments.put(key, "San Francisco, CA")
                        android.util.Log.d("MCP", "    → Set to: San Francisco, CA")
                    }
                    else -> {
                        // Provide default based on type for any other field
                        when (type) {
                            "string" -> {
                                arguments.put(key, "default")
                                android.util.Log.d("MCP", "    → Set to: default (string)")
                            }
                            "number", "integer" -> {
                                arguments.put(key, 1)
                                android.util.Log.d("MCP", "    → Set to: 1 (number)")
                            }
                            "boolean" -> {
                                arguments.put(key, true)
                                android.util.Log.d("MCP", "    → Set to: true (boolean)")
                            }
                        }
                    }
                }
            }
        }
        
        android.util.Log.d("MCP", "Final arguments: $arguments")
        android.util.Log.d("MCP", "=================================================")
        
        return arguments
    }
    
    private fun connectToMcpApp(appId: String) {
        android.util.Log.d("MCP", "Connecting to MCP app: $appId")
        
        // Find the app in the directory
        val app = AppDirectory.getFeaturedApps().find { it.id == appId }
        if (app == null) {
            android.util.Log.e("MCP", "App not found: $appId")
            Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, "Connecting to ${app.name}...", Toast.LENGTH_SHORT).show()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Step 1: Initialize connection with MCP server
                val capabilities = withContext(Dispatchers.IO) {
                    mcpService.initialize(app.serverUrl)
                }
                
                if (capabilities == null) {
                    throw Exception("Failed to initialize connection")
                }
                
                android.util.Log.d("MCP", "Initialized connection to ${app.name}")
                
                // Step 2: Fetch tools from MCP server
                val tools = withContext(Dispatchers.IO) {
                    mcpService.listTools(app.serverUrl)
                }
                
                android.util.Log.d("MCP", "Fetched ${tools.size} tools from ${app.name}")
                
                // Update connected apps
                val connectedApp = app.copy(
                    tools = tools,
                    connectionStatus = ConnectionStatus.CONNECTED
                )
                connectedApps[appId] = connectedApp
                
                // Refresh the adapter
                chatAdapter.notifyDataSetChanged()
                
                // Refresh the pills to show tools
                android.util.Log.d("MCP", "Refreshing pills to show tools for ${app.name}")
                showSingleMcpAppWithTools(connectedApp)
                
                Toast.makeText(this@MainActivity, "${app.name} connected with ${tools.size} tools!", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                android.util.Log.e("MCP", "Error connecting to ${app.name}: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                
                // Clear the "Connecting..." pill on error
                suggestionsLayout.removeAllViews()
                suggestionsScrollView.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun handleMcpConnectionLost(serverUrl: String) {
        android.util.Log.d("MCP", "Handling connection loss for: $serverUrl")
        
        // Find the app that was disconnected
        val disconnectedApp = connectedApps.values.find { it.serverUrl == serverUrl }
        if (disconnectedApp != null) {
            android.util.Log.d("MCP", "App disconnected: ${disconnectedApp.name}")
            
            // Remove from connected apps
            connectedApps.remove(disconnectedApp.id)
            
            // Refresh UI to show Connect button again
            chatAdapter.notifyDataSetChanged()
            
            // Optionally show a toast
            Toast.makeText(this, "${disconnectedApp.name} disconnected", Toast.LENGTH_SHORT).show()
            
            // Auto-reconnect after 2 seconds
            CoroutineScope(Dispatchers.Main).launch {
                delay(2000)
                android.util.Log.d("MCP", "Auto-reconnecting to ${disconnectedApp.name}...")
                connectToMcpApp(disconnectedApp.id)
            }
        } else {
            android.util.Log.w("MCP", "Could not find disconnected app for URL: $serverUrl")
        }
    }
    
    private fun showMcpAppDetails(appId: String) {
        android.util.Log.d("MCP", "Showing app details for: $appId")
        
        // Find the app in the directory
        val allApps = AppDirectory.getFeaturedApps()
        val app = allApps.find { it.id == appId }
        
        if (app != null) {
            // Check if app is connected and get tools
            val connectedApp = connectedApps[appId]
            val isConnected = connectedApp != null
            val toolsCount = connectedApp?.tools?.size ?: 0
            
            // Prepare tools data for intent
            val toolNames = connectedApp?.tools?.map { it.name } ?: emptyList()
            val toolTitles = connectedApp?.tools?.map { it.title ?: it.name } ?: emptyList()
            val toolDescriptions = connectedApp?.tools?.map { it.description ?: "" } ?: emptyList()
            
            // Launch app details activity
            val intent = Intent(this, McpAppDetailsActivity::class.java).apply {
                putExtra("app_id", app.id)
                putExtra("app_name", app.name)
                putExtra("app_description", app.description)
                putExtra("app_icon", app.icon)
                putExtra("app_server_url", app.serverUrl)
                putExtra("is_connected", isConnected)
                putExtra("tools_count", toolsCount)
                putStringArrayListExtra("tool_names", ArrayList(toolNames))
                putStringArrayListExtra("tool_titles", ArrayList(toolTitles))
                putStringArrayListExtra("tool_descriptions", ArrayList(toolDescriptions))
            }
            startActivityForResult(intent, REQUEST_CODE_APP_DETAILS)
        } else {
            android.util.Log.w("MCP", "App not found: $appId")
            Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show()
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
        
        // Clear MCP context when returning to cards view
        if (shouldShowCards) {
            currentMcpAppContext = null
            lastInvokedTool = null
            android.util.Log.d("MCP", "Cleared MCP app context (returned to cards)")
            
            // Update pills to show recent apps
            showRecentApps()
            android.util.Log.d("MCP", "Updated pills to show recent apps after returning to cards")
        }
        
        // Show/hide tab layout based on cards visibility
        cardTypeTabLayout.visibility = if (shouldShowCards) View.VISIBLE else View.GONE
        android.util.Log.d("TabLayout", "Tab layout visibility: ${if (shouldShowCards) "VISIBLE" else "GONE"}")
        
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
    
    private fun setupTabLayout() {
        // Set up tab selection listener
        cardTypeTabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                currentTab = when (tab.position) {
                    0 -> TabType.APPS  // Apps is now first tab
                    1 -> TabType.BLINKS  // Blinks is now second tab
                    else -> TabType.APPS
                }
                android.util.Log.d("TabLayout", "Tab selected: $currentTab")
                // Update adapter to filter by tab type
                chatAdapter.setCardType(currentTab)
            }
            
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                // No action needed
            }
            
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                // No action needed
            }
        })
        
        // Set initial tab to Apps (position 0)
        cardTypeTabLayout.getTabAt(0)?.select()
        
        // Initially hide tabs (they show only when cards are visible)
        cardTypeTabLayout.visibility = View.GONE
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
