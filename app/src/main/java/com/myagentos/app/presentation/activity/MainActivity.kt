package com.myagentos.app.presentation.activity

import com.myagentos.app.R
import com.myagentos.app.presentation.adapter.SimpleChatAdapter
import com.myagentos.app.data.manager.ModelManager
import com.myagentos.app.data.manager.AppManager
import com.myagentos.app.data.manager.UsageStatsManager
import com.myagentos.app.data.service.BlinkService
import com.myagentos.app.data.manager.WalletManager
import com.myagentos.app.data.service.ExternalAIService
import com.myagentos.app.data.manager.ConversationManager
import com.myagentos.app.data.service.McpService
import com.myagentos.app.data.manager.BrowserManager
import com.myagentos.app.data.source.AppDirectory
import com.myagentos.app.domain.model.MessageType
import com.myagentos.app.data.model.ModelType
import com.myagentos.app.data.model.McpTool
import com.myagentos.app.data.model.McpApp
import com.myagentos.app.domain.model.IntentCapability
import com.myagentos.app.domain.model.AppInfo
import com.myagentos.app.domain.model.Job
import com.myagentos.app.domain.model.ChatMessage

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
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
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
import com.myagentos.app.domain.repository.ConversationRepository
import com.myagentos.app.data.repository.ConversationRepositoryImpl
import com.myagentos.app.domain.repository.AIRepository
import com.myagentos.app.data.repository.AIRepositoryImpl
import com.myagentos.app.domain.usecase.SendMessageUseCase
import com.myagentos.app.domain.usecase.LoadConversationUseCase
import com.myagentos.app.domain.usecase.DeleteConversationUseCase
import com.myagentos.app.domain.repository.BrowserRepository
import com.myagentos.app.data.repository.BrowserRepositoryImpl
import com.myagentos.app.domain.repository.McpRepository
import com.myagentos.app.data.repository.McpRepositoryImpl
import com.myagentos.app.presentation.viewmodel.ChatViewModel
import com.myagentos.app.presentation.viewmodel.ChatViewModelFactory
import com.myagentos.app.presentation.viewmodel.BrowserViewModel
import com.myagentos.app.presentation.viewmodel.BrowserViewModelFactory
import com.myagentos.app.presentation.viewmodel.McpViewModel
import com.myagentos.app.presentation.viewmodel.McpViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.myagentos.app.presentation.manager.TrayManager
import com.myagentos.app.presentation.manager.JobGridManager
import com.myagentos.app.presentation.manager.BrowserUIManager
import com.myagentos.app.presentation.manager.MCPManager
import com.myagentos.app.presentation.manager.DialogManager
import com.myagentos.app.presentation.manager.PillsCoordinator
import com.myagentos.app.presentation.manager.AgentManager
import com.myagentos.app.util.UIHelpers
import com.myagentos.app.util.IntentHelpers
import com.myagentos.app.util.PermissionHelpers
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

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
    private lateinit var widgetDisplayArea: android.widget.ScrollView
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var hiddenTray: android.widget.LinearLayout
    private lateinit var rightTray: android.widget.LinearLayout
    private lateinit var jobGridOverlay: View
    
    // App Embedding variables
    private var embeddedAppContainer: FrameLayout? = null
    private var isAppEmbedded = false
    private var embeddedAppPackage: String? = null
    private lateinit var modelManager: ModelManager
    private lateinit var aiRepository: AIRepository
    private lateinit var appManager: AppManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var loadConversationUseCase: LoadConversationUseCase
    private lateinit var deleteConversationUseCase: DeleteConversationUseCase
    private lateinit var blinkService: BlinkService
    private lateinit var walletManager: WalletManager
    private lateinit var mcpRepository: McpRepository
    private var selectedModel: ModelType? = null
    
    // ViewModels for MVVM architecture
    private val chatViewModel: ChatViewModel by lazy {
        val factory = ChatViewModelFactory(
            conversationRepository,
            sendMessageUseCase,
            loadConversationUseCase,
            deleteConversationUseCase
        )
        ViewModelProvider(this, factory)[ChatViewModel::class.java]
    }
    
    private val browserViewModel: BrowserViewModel by lazy {
        val factory = BrowserViewModelFactory(browserRepository)
        ViewModelProvider(this, factory)[BrowserViewModel::class.java]
    }
    
    private val mcpViewModel: McpViewModel by lazy {
        val factory = McpViewModelFactory(mcpRepository)
        ViewModelProvider(this, factory)[McpViewModel::class.java]
    }
    
    // App detection state
    private var installedApps: List<AppInfo> = emptyList()
    private var hasShownPermissionDialog = false // Flag to show permission dialog only once per session
    private var isUpdatingPills = false // Flag to prevent infinite loops (moved from PillsCoordinator)
    
    // PillsCoordinator (Phase 4 - Step 2: BIGGEST EXTRACTION!)
    private lateinit var pillsCoordinator: PillsCoordinator
    
    // Browser-related variables
    private lateinit var webView: android.webkit.WebView
    private lateinit var browserMenuButton: ImageButton
    private lateinit var tabManagerButton: ImageButton
    private lateinit var closeChatButton: ImageButton
    private lateinit var browserRepository: BrowserRepository
    
    // Managers for UI components (MVVM Phase 4 - Extraction)
    private lateinit var trayManager: TrayManager
    private lateinit var jobGridManager: JobGridManager
    private lateinit var browserUIManager: BrowserUIManager
    private lateinit var mcpManager: MCPManager
    private lateinit var dialogManager: DialogManager
    private lateinit var favoritesManager: com.myagentos.app.data.manager.FavoritesManager
    private lateinit var agentManager: AgentManager
    
    // Solana Mobile Wallet Adapter (for x402 payments)
    private lateinit var activityResultSender: ActivityResultSender
    
    // Keyboard state variables
    private var isKeyboardVisible = true // Initially true since input is auto-focused
    private var wasKeyboardVisibleBeforeRightTray = false // Track keyboard state before opening right tray

    // Triple-tap detection for job grid
    private var lastTapTime = 0L
    private var tapCount = 0
    
    // Right edge swipe detection (to differentiate from taps on UI elements)
    private var lastRightEdgeTouchX = 0f
    private var lastRightEdgeTouchY = 0f
    private var lastRightEdgeTouchTime = 0L
    
    // Tab state variables for Blinks vs Apps
    private lateinit var cardTypeTabLayout: com.google.android.material.tabs.TabLayout
    private var currentTab: TabType = TabType.APPS  // Default to Apps tab

    companion object {
        private var savedMessages: MutableList<ChatMessage>? = null
        private const val REQUEST_CODE_APP_DETAILS = 1001
    }
    
    // Override dispatchTouchEvent to intercept right-edge gestures before system
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val edgeThreshold = 100 * resources.displayMetrics.density // Wide edge (100dp) for swipe detection
        val isNearRightEdge = event.rawX > (screenWidth - edgeThreshold)
        
        // Only block right edge gestures for SWIPES (not taps on UI elements like heart button)
        // We need to detect if this is a swipe gesture by checking for ACTION_MOVE
        if (isNearRightEdge && event.action == MotionEvent.ACTION_DOWN) {
            // Store the initial touch position to detect swipes
            lastRightEdgeTouchX = event.rawX
            lastRightEdgeTouchY = event.rawY
            lastRightEdgeTouchTime = System.currentTimeMillis()
        }
        
        // Only intercept if it's actually a SWIPE gesture (horizontal movement)
        if (isNearRightEdge && event.action == MotionEvent.ACTION_MOVE) {
            val deltaX = event.rawX - lastRightEdgeTouchX
            val deltaY = event.rawY - lastRightEdgeTouchY
            val timeDelta = System.currentTimeMillis() - lastRightEdgeTouchTime
            
            // Detect horizontal swipe (more X movement than Y, and moving left)
            // LOWER threshold for better swipe detection: 5dp minimum movement
            val swipeThreshold = 5 * resources.displayMetrics.density
            if (Math.abs(deltaX) > swipeThreshold && Math.abs(deltaX) > Math.abs(deltaY) && deltaX < 0 && timeDelta < 500) {
                android.util.Log.e("RightEdgeBlock", "🚫 RIGHT EDGE SWIPE detected at x=${event.rawX} (deltaX=${deltaX.toInt()}px)")
                // This is a swipe - pass to TrayManager and block system
                if (::trayManager.isInitialized) {
                    trayManager.handleRightEdgeTouch(event, screenWidth, edgeThreshold)
                }
                return true
            }
        }
        
        // Let TrayManager handle edge swipe detection ONLY when tray is NOT already open
        // When tray is open, let normal touch handling work for UI elements inside it
        if (::trayManager.isInitialized && isNearRightEdge && !trayManager.isRightTrayVisible()) {
            val consumed = trayManager.handleRightEdgeTouch(event, screenWidth, edgeThreshold)
            if (consumed) {
                android.util.Log.e("RightEdgeBlock", "🚫 TrayManager consumed ${event.action}")
                return true
            }
        }
        
        // Handle triple-tap detection (only when NOT near right edge)
        if (!isNearRightEdge && event.action == MotionEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 300) { // 300ms tap window
                tapCount++
                if (tapCount == 3) {
                    // Triple tap detected
                    android.util.Log.d("JobGrid", "TRIPLE TAP DETECTED at (${event.x}, ${event.y})")
                    jobGridManager.show()
                    tapCount = 0
                    return true // Consume the event
                }
            } else {
                tapCount = 1
            }
            lastTapTime = currentTime
        }
        
        // Let normal event handling continue (including heart button clicks!)
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ActivityResultSender for Mobile Wallet Adapter (must be done early in lifecycle)
        activityResultSender = ActivityResultSender(this)
        
        // Note: MCP state clearing moved to MCPManager initialization (Phase 4)
        
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
        
        // Request exclusive gesture control for right edge - MAXIMUM BLOCKING
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Set exclusion rects - WIDE edge to completely block system gestures
            val edgeWidth = (200 * resources.displayMetrics.density).toInt() // 200dp - MAXIMUM allowed
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            
            val rightEdgeRect = android.graphics.Rect(
                screenWidth - edgeWidth,  // left
                0,                         // top
                screenWidth,               // right
                screenHeight               // bottom
            )
            
            window.decorView.systemGestureExclusionRects = listOf(rightEdgeRect)
            android.util.Log.e("GestureControl", "🚫🚫🚫 MAXIMUM BLOCKING: $rightEdgeRect (width=${edgeWidth}px = 200dp)")
            
            // Re-apply on every layout change to ensure it persists
            window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    window.decorView.systemGestureExclusionRects = listOf(rightEdgeRect)
                }
            }
            
            // Also set it on insets change
            window.decorView.setOnApplyWindowInsetsListener { view, insets ->
                val gestureInsets = insets.systemGestureInsets
                android.util.Log.e("GestureControl", "System gesture insets: left=${gestureInsets.left}, right=${gestureInsets.right}")
                
                // Re-apply exclusion rects
                view.systemGestureExclusionRects = listOf(rightEdgeRect)
                android.util.Log.e("GestureControl", "🚫🚫🚫 Re-applied 200dp exclusion rect (MAXIMUM)")
                
                insets
            }
        }

        modelManager = ModelManager(this)
        // Initialize repository pattern for AI service
        val externalAIService = ExternalAIService()
        aiRepository = AIRepositoryImpl(externalAIService)
        appManager = AppManager(this)
        usageStatsManager = UsageStatsManager(this)
        // Initialize repository pattern for conversation management
        val conversationManager = ConversationManager(this)
        conversationRepository = ConversationRepositoryImpl(conversationManager)
        // Initialize use cases
        sendMessageUseCase = SendMessageUseCase(conversationRepository, aiRepository)
        loadConversationUseCase = LoadConversationUseCase(conversationRepository)
        deleteConversationUseCase = DeleteConversationUseCase(conversationRepository)
        blinkService = BlinkService()
        walletManager = WalletManager(this)
        // Initialize repository pattern for MCP service
        val mcpService = McpService.getInstance()
        mcpRepository = McpRepositoryImpl(mcpService)
        
        // Initialize FavoritesManager (must be before setupUI since chatAdapter needs it)
        favoritesManager = com.myagentos.app.data.manager.FavoritesManager(this)
        
        // Reset any stale MCP connections from previous app session
        mcpRepository.resetConnections()
        
        // Set up connection loss listener to auto-reconnect or show Connect button
        mcpRepository.setOnConnectionLost { serverUrl ->
            android.util.Log.d("MCP", "Connection lost to: $serverUrl")
            runOnUiThread {
                handleMcpConnectionLost(serverUrl)
            }
        }

        setupUI()
        
        // Setup ViewModel observers AFTER setupUI (MVVM architecture)
        // This ensures all repositories are initialized before ViewModels are created
        setupViewModelObservers()
        
        checkFirstLaunch()
        // Note: showRecentApps() is called after apps are loaded in loadInstalledApps()
        
        // Handle intent extras
        handleIntentExtras()
        
        // Load test blinks for development/testing
        loadTestBlinks()
    }
    
    /**
     * Setup observers for ViewModel state changes (MVVM Architecture)
     * This enables reactive UI updates based on ViewModel state
     */
    private fun setupViewModelObservers() {
        // Observe ChatViewModel state
        lifecycleScope.launch {
            chatViewModel.messages.collect { messages ->
                // Update chat adapter when messages change
                android.util.Log.d("MVVM", "ChatViewModel messages updated: ${messages.size} messages")
                // Note: We're keeping the existing adapter pattern for now
                // Future improvement: directly sync adapter with ViewModel state
            }
        }
        
        lifecycleScope.launch {
            chatViewModel.isLoading.collect { isLoading ->
                android.util.Log.d("MVVM", "ChatViewModel loading state: $isLoading")
                // Future: Show/hide loading indicator
            }
        }
        
        lifecycleScope.launch {
            chatViewModel.error.collect { error ->
                error?.let {
                    android.util.Log.e("MVVM", "ChatViewModel error: $it")
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                    chatViewModel.clearError()
                }
            }
        }
        
        lifecycleScope.launch {
            chatViewModel.selectedModel.collect { model ->
                android.util.Log.d("MVVM", "ChatViewModel selected model: $model")
                // Sync ViewModel model selection with local state
                if (model != selectedModel && model != null) {
                    selectedModel = model
                }
            }
        }
        
        // Observe BrowserViewModel state
        lifecycleScope.launch {
            browserViewModel.currentUrl.collect { url ->
                android.util.Log.d("MVVM", "BrowserViewModel current URL: $url")
                // Future: Update URL bar
            }
        }
        
        lifecycleScope.launch {
            browserViewModel.isLoading.collect { isLoading ->
                android.util.Log.d("MVVM", "BrowserViewModel loading: $isLoading")
                // Future: Show/hide browser loading indicator
            }
        }
        
        lifecycleScope.launch {
            browserViewModel.error.collect { error ->
                error?.let {
                    android.util.Log.e("MVVM", "BrowserViewModel error: $it")
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    browserViewModel.clearError()
                }
            }
        }
        
        // Observe McpViewModel state
        lifecycleScope.launch {
            mcpViewModel.connectedApps.collect { apps ->
                android.util.Log.d("MVVM", "McpViewModel connected apps: ${apps.size}")
                // Future: Sync with local connectedApps map
            }
        }
        
        lifecycleScope.launch {
            mcpViewModel.connectionStatus.collect { status ->
                android.util.Log.d("MVVM", "McpViewModel connection status: $status")
                // Future: Update UI based on connection status
            }
        }
        
        lifecycleScope.launch {
            mcpViewModel.error.collect { error ->
                error?.let {
                    android.util.Log.e("MVVM", "McpViewModel error: $it")
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    mcpViewModel.clearError()
                }
            }
        }
        
        android.util.Log.i("MVVM", "ViewModel observers setup complete - reactive state management active!")
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
        
        // Check if we're in an MCP context first
        val mcpContext = mcpManager.getCurrentContext()
        if (mcpContext != null) {
            // We're connected to an MCP app - show its tool pills
            android.util.Log.d("MainActivity", "Resuming with MCP context: $mcpContext, showing tool pills")
            pillsCoordinator.showMcpToolSuggestions(mcpContext)
        } else {
            // No MCP context - show recent apps
            pillsCoordinator.showRecentApps()
        }
    }

    private fun setupUI() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        suggestionsLayout = findViewById(R.id.suggestionsLayout)
        suggestionsScrollView = findViewById(R.id.suggestionsScrollView)
        historyButton = findViewById(R.id.historyButton)
        newChatButton = findViewById(R.id.newChatButton)
        widgetDisplayArea = findViewById(R.id.widgetDisplayArea)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        hiddenTray = findViewById(R.id.hiddenTray)
        rightTray = findViewById(R.id.rightTray)
        val rightEdgeDetector = findViewById<View>(R.id.rightEdgeDetector)
        
        // Initialize repository pattern for browser management (BEFORE ViewModels)
        val browserManager = BrowserManager(this)
        browserRepository = BrowserRepositoryImpl(browserManager)
        
        // Initialize tab layout for Blinks vs Apps
        cardTypeTabLayout = findViewById(R.id.cardTypeTabLayout)
        setupTabLayout()
        
        // Initialize radial menu views
        jobGridOverlay = findViewById(R.id.jobGridOverlay)
        embeddedAppContainer = findViewById(R.id.embeddedAppContainer)
        
        // Initialize browser views
        webView = findViewById(R.id.webView)
        browserMenuButton = findViewById(R.id.browserMenuButton)
        tabManagerButton = findViewById(R.id.tabManagerButton)
        closeChatButton = findViewById(R.id.closeChatButton)
        
        // Set up close chat button click listener (handled by BrowserUIManager)
        // Note: Click listener is set in BrowserUIManager when entering chat mode

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
                // Provide connected apps state (using MCPManager - Phase 4)
                mcpManager.getConnectedApps()
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
        
        // Set FavoritesManager for handling MCP app favorites
        chatAdapter.setFavoritesManager(favoritesManager)
        
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
            // Hide the top tray to focus on chat screen
            trayManager.hideTray()
            
            // Clear current conversation and start fresh
            conversationRepository.startNewConversation()
            chatAdapter.clearMessages()
            messageInput.setText("")
            hideKeyboardAndClearFocus()
            
            // Clear MCP app context and parameter collection
            mcpManager.clearState()
            android.util.Log.d("MCP", "Cleared MCP app context and parameter collection (new chat button)")
            
            // Show recent apps pills (will respect cleared MCP context)
            pillsCoordinator.showRecentApps()
            android.util.Log.d("MCP", "Updated pills to show recent apps after new chat")
        }
        
        
        // Initialize AgentManager
        agentManager = AgentManager(this)
        
        // Setup agent profile click listeners
        setupAgentProfileListeners()
        
        // Initialize TrayManager (Phase 4 - Extraction)
        trayManager = TrayManager(
            hiddenTray = hiddenTray,
            rightTray = rightTray,
            swipeRefreshLayout = swipeRefreshLayout,
            chatRecyclerView = chatRecyclerView,
            widgetDisplayArea = widgetDisplayArea
        )
        trayManager.setup()
        trayManager.setOnRightTrayVisibilityChanged { isVisible ->
            // Handle keyboard state when right tray visibility changes
            if (!isVisible && wasKeyboardVisibleBeforeRightTray) {
                // Restore keyboard if it was visible before
                messageInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        
        // Initialize JobGridManager (Phase 4 - Extraction)
        jobGridManager = JobGridManager(this, jobGridOverlay)
        jobGridManager.setup()
        jobGridManager.setOnJobExecute { job ->
            executeJob(job)
        }
        
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
        
        // Initialize BrowserUIManager (Phase 4 - Extraction)
        browserUIManager = BrowserUIManager(
            context = this,
            webView = webView,
            browserMenuButton = browserMenuButton,
            tabManagerButton = tabManagerButton,
            closeChatButton = closeChatButton,
            messageInput = messageInput,
            sendButton = sendButton,
            swipeRefreshLayout = swipeRefreshLayout,
            chatRecyclerView = chatRecyclerView,
            suggestionsScrollView = suggestionsScrollView,
            browserRepository = browserRepository
        )
        browserUIManager.setup()
        browserUIManager.setCallbacks(
            chatAdapter = chatAdapter,
            conversationRepository = conversationRepository,
            aiRepository = aiRepository,
            onHideKeyboard = { hideKeyboardAndClearFocus() },
            trayManager = trayManager
        )
        
        // Initialize DialogManager (Phase 4 - Step 3)
        dialogManager = DialogManager(this)
        
        // Initialize MCPManager (Phase 4 - Step 1: BIGGEST EXTRACTION!)
        mcpManager = MCPManager(
            context = this,
            mcpRepository = mcpRepository,
            aiRepository = aiRepository,
            activityResultSender = activityResultSender
        )
        mcpManager.setCallbacks(
            onToolInvoked = { appId, tool ->
                android.util.Log.d("MCP", "Tool invoked callback: ${tool.name}")
                // Add user message to chat
                val toolTitle = tool.title ?: tool.name
                val userMessage = ChatMessage(toolTitle, isUser = true, messageType = MessageType.TEXT)
                chatAdapter.addMessage(userMessage)
                chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                // Hide cards and show chat
                chatAdapter.setShowCards(false)
                cardTypeTabLayout.visibility = View.GONE
                updateCardsVisibility()
            },
            onConnectionStatusChanged = { appId, status ->
                android.util.Log.d("MCP", "Connection status changed: $appId -> $status")
                // Refresh adapter
                chatAdapter.notifyDataSetChanged()
            },
            onToolResultReceived = { appId, tool, result, mcpToolResult, toolArguments ->
                // Launch coroutine for async widget fetching
                lifecycleScope.launch {
                    android.util.Log.e("MCP", ">>> TOOL RESULT CALLBACK TRIGGERED!")
                    android.util.Log.e("MCP", "Tool result received: ${result.take(100)}")
                    android.util.Log.e("MCP", "mcpToolResult: $mcpToolResult")
                    android.util.Log.e("MCP", "mcpToolResult?.content: ${mcpToolResult?.content}")
                    android.util.Log.e("MCP", "mcpToolResult?._meta: ${mcpToolResult?._meta}")
                
                // Check if result has a widget template in _meta
                val hasWidgetTemplate = mcpToolResult?._meta?.get("openai/outputTemplate") != null ||
                                       mcpToolResult?._meta?.get("openai/resultCanProduceWidget") == true
                
                android.util.Log.e("MCP", ">>> hasWidgetTemplate: $hasWidgetTemplate")
                android.util.Log.e("MCP", ">>> outputTemplate: ${mcpToolResult?._meta?.get("openai/outputTemplate")}")
                
                // Check if result contains HTML content for WebView (direct HTML in content)
                val hasHtmlContent = mcpToolResult?.content?.any { content ->
                    android.util.Log.e("MCP", ">>> Checking content type: ${content?.javaClass?.simpleName}")
                    android.util.Log.e("MCP", ">>> Content: $content")
                    if (content is com.myagentos.app.data.model.McpContent.Text) {
                        android.util.Log.e("MCP", ">>> Text content length: ${content.text.length}")
                        android.util.Log.e("MCP", ">>> First 200 chars: ${content.text.take(200)}")
                        val containsHtml = content.text.contains("<html") || content.text.contains("<!DOCTYPE") || content.text.contains("<div") || content.text.contains("<script")
                        android.util.Log.e("MCP", ">>> Contains HTML tags: $containsHtml")
                        containsHtml
                    } else {
                        false
                    }
                } ?: false
                
                android.util.Log.e("MCP", ">>> hasHtmlContent: $hasHtmlContent")
                android.util.Log.e("MCP", ">>> Should create WebView: ${hasHtmlContent || hasWidgetTemplate}")
                
                val aiMessage = if ((hasHtmlContent || hasWidgetTemplate) && mcpToolResult != null) {
                    // Create WebView message
                    val mcpApp = AppDirectory.getFeaturedApps().find { it.id == appId }
                    
                    // Extract template URL for use later (fullscreen re-fetch)
                    val templateUrl = mcpToolResult._meta?.get("openai/outputTemplate") as? String
                    
                    // Get HTML content - either directly from content or from widget template
                    val htmlContent = if (hasHtmlContent) {
                        // Direct HTML in content
                        mcpToolResult.content?.firstOrNull()?.let {
                            when (it) {
                                is com.myagentos.app.data.model.McpContent.Text -> it.text
                                else -> result
                            }
                        } ?: result
                    } else {
                        // Widget template - fetch from server
                        android.util.Log.e("MCP", ">>> Widget template URL: $templateUrl")
                        
                        if (templateUrl != null && templateUrl.startsWith("ui://")) {
                            // Use MCP resources/read to fetch widget HTML
                            android.util.Log.e("MCP", ">>> Fetching widget via MCP resources/read: $templateUrl")
                            
                            try {
                                val htmlContent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    // Use McpService to read the resource
                                    val resource = mcpRepository.readResource(
                                        serverUrl = mcpApp?.serverUrl ?: "",
                                        uri = templateUrl,
                                        displayMode = "inline"
                                    )
                                    
                                    if (resource != null && resource.text != null) {
                                        val html = resource.text
                                        android.util.Log.e("MCP", ">>> Successfully fetched widget HTML via MCP!")
                                        android.util.Log.e("MCP", ">>> HTML length: ${html.length}")
                                        android.util.Log.e("MCP", ">>> HTML preview: ${html.take(300)}")
                                        html
                                    } else {
                                        android.util.Log.e("MCP", ">>> ERROR: Resource text is null")
                                        createFallbackHtml(tool, templateUrl, mcpToolResult.structuredContent)
                                    }
                                }
                                htmlContent
                            } catch (e: Exception) {
                                android.util.Log.e("MCP", ">>> ERROR fetching widget via MCP: ${e.message}", e)
                                e.printStackTrace()
                                createFallbackHtml(tool, templateUrl, mcpToolResult.structuredContent)
                            }
                        } else {
                            result
                        }
                    }
                    
                    android.util.Log.e("MCP", ">>> CREATING MCP_WEBVIEW MESSAGE!")
                    android.util.Log.e("MCP", "  - htmlContent length: ${htmlContent.length}")
                    android.util.Log.e("MCP", "  - htmlContent preview: ${htmlContent.take(300)}")
                    android.util.Log.e("MCP", "  - appId: $appId")
                    android.util.Log.e("MCP", "  - toolName: ${tool.name}")
                    android.util.Log.e("MCP", "  - serverUrl: ${mcpApp?.serverUrl}")
                    
                    android.util.Log.e("MCP", ">>> Creating Apps SDK data objects...")
                    android.util.Log.e("MCP", "  - toolArguments: $toolArguments")
                    android.util.Log.e("MCP", "  - structuredContent: ${mcpToolResult.structuredContent}")
                    android.util.Log.e("MCP", "  - _meta: ${mcpToolResult._meta}")
                    
                    ChatMessage(
                        text = "Rendered ${tool.title ?: tool.name}!",
                        isUser = false,
                        messageType = com.myagentos.app.domain.model.MessageType.MCP_WEBVIEW,
                        mcpWebViewData = com.myagentos.app.domain.model.McpWebViewData(
                            appId = appId,
                            toolName = tool.name,
                            htmlContent = htmlContent,
                            serverUrl = mcpApp?.serverUrl,
                            outputTemplate = templateUrl, // Pass the template URL for fullscreen re-fetch
                            toolArguments = toolArguments, // Pass the tool arguments for fullscreen re-fetch
                            // Apps SDK required data
                            toolInput = org.json.JSONObject(toolArguments),
                            toolOutput = mcpToolResult.structuredContent,
                            toolResponseMetadata = mcpToolResult._meta
                        )
                    )
                } else {
                    // Regular text message
                    android.util.Log.e("MCP", ">>> CREATING TEXT MESSAGE (NOT WEBVIEW)")
                    android.util.Log.e("MCP", "  - hasHtmlContent: $hasHtmlContent")
                    android.util.Log.e("MCP", "  - hasWidgetTemplate: $hasWidgetTemplate")
                    android.util.Log.e("MCP", "  - mcpToolResult: $mcpToolResult")
                    android.util.Log.e("MCP", "  - result: $result")
                    ChatMessage(result, isUser = false)
                }
                
                android.util.Log.e("MCP", ">>> MESSAGE CREATED, adding to adapter...")
                android.util.Log.e("MCP", "  - message.messageType: ${aiMessage.messageType}")
                android.util.Log.e("MCP", "  - message.text: ${aiMessage.text}")
                android.util.Log.e("MCP", "  - message.mcpWebViewData: ${aiMessage.mcpWebViewData}")
                
                    chatAdapter.addMessage(aiMessage)
                    conversationRepository.addMessage(aiMessage)
                    chatRecyclerView.postDelayed({
                        chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                    }, 50)
                }
            },
            onParameterCollectionStarted = { appId, tool ->
                android.util.Log.e("MCP", ">>> PARAMETER COLLECTION STARTED for: ${tool.name}")
                
                // Use Grok to naturally collect parameters through conversation
                lifecycleScope.launch {
                    try {
                        val collectionState = mcpManager.getActiveCollectionState()
                        val requiredParams = collectionState?.requiredParams ?: emptyList()
                        android.util.Log.e("MCP", "Required params: $requiredParams")
                        
                        if (requiredParams.isNotEmpty()) {
                            // Get parameter details from tool schema
                            val parameterDescriptions = mutableListOf<String>()
                            val inputSchema = tool.inputSchema
                            
                            if (inputSchema.has("properties")) {
                                val properties = inputSchema.getJSONObject("properties")
                                requiredParams.forEach { paramName ->
                                    if (properties.has(paramName)) {
                                        val paramSchema = properties.getJSONObject(paramName)
                                        val description = paramSchema.optString("description", paramName)
                                        parameterDescriptions.add("- **$paramName**: $description")
                                    } else {
                                        parameterDescriptions.add("- **$paramName**")
                                    }
                                }
                            }
                            
                            // Create a natural prompt for Grok to ask for parameters
                            val grokPrompt = """
                                The user wants to use the "${tool.title ?: tool.name}" tool.
                                
                                This tool requires the following parameters:
                                ${parameterDescriptions.joinToString("\n")}
                                
                                Ask the user for these parameters in a natural, conversational way. 
                                When they respond, extract the parameter values and format your response EXACTLY as:
                                TOOL_PARAMS: {"param1": "value1", "param2": "value2"}
                                
                                This special format will trigger tool execution.
                            """.trimIndent()
                            
                            android.util.Log.e("MCP", "Sending to Grok: $grokPrompt")
                            
                            // Send to Grok (must use IO dispatcher for network calls)
                            chatAdapter.showTypingIndicator()
                            val grokResponse = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                aiRepository.generateResponseWithHistory(
                                    grokPrompt, 
                                    selectedModel ?: ModelType.EXTERNAL_GROK,
                                    buildConversationHistory()
                                )
                            }
                            chatAdapter.hideTypingIndicator()
                            
                            // Add Grok's question to chat
                            val aiMessage = ChatMessage(grokResponse, isUser = false)
                            chatAdapter.addMessage(aiMessage)
                            conversationRepository.addMessage(aiMessage)
                            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MCP", "Error in parameter collection: ${e.message}", e)
                        chatAdapter.hideTypingIndicator()
                    }
                }
            },
            onNeedToShowToolPills = { appId ->
                android.util.Log.d("MCP", "Need to show tool pills for: $appId")
                pillsCoordinator.showMcpToolSuggestions(appId)
            },
            chatAdapter = chatAdapter,
            chatRecyclerView = chatRecyclerView
        )
        
        // Initialize PillsCoordinator (Phase 4 - Step 2: BIGGEST EXTRACTION!)
        pillsCoordinator = PillsCoordinator(
            context = this,
            messageInput = messageInput,
            suggestionsLayout = suggestionsLayout,
            suggestionsScrollView = suggestionsScrollView,
            appManager = appManager,
            mcpRepository = mcpRepository,
            installedApps = installedApps
        )
        pillsCoordinator.setCallbacks(object : PillsCoordinator.PillsCallbacks {
            override fun onAppPillClicked(app: AppInfo) {
                // Launch the app directly
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this@MainActivity, "Could not launch ${app.appName}", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onMcpAppPillClicked(mcpApp: McpApp) {
                // Connect directly without showing confirmation dialog
                connectToMcpApp(mcpApp.id)
            }
            
            override fun onMcpToolPillClicked(appId: String, tool: McpTool) {
                android.util.Log.e("McpToolPill", ">>> CALLBACK TRIGGERED: Tool clicked: ${tool.name}")
                
                // Clear the text input field - user has completed their action
                messageInput.setText("")
                
                // Hide the suggestion pills
                suggestionsScrollView.visibility = View.GONE
                
                // Invoke the tool
                android.util.Log.e("McpToolPill", ">>> INVOKING TOOL: ${tool.name}")
                invokeMcpTool(appId, tool.name, null)
            }
            
            override fun onParameterPillClicked(paramName: String, paramSchema: org.json.JSONObject?, required: Boolean) {
                // Handle parameter pill click - would need proper implementation
                android.util.Log.d("ParameterPill", "Parameter clicked: $paramName")
            }
            
            override fun onIntentPillClicked(app: AppInfo, capability: IntentCapability) {
                // Show parameter input dialog for intents that require parameters
                if (capability.parameters.isNotEmpty()) {
                    showParameterInputDialog(app, capability)
                } else {
                    // Launch the app with the specific intent (no parameters needed)
                    val success = appManager.launchAppWithIntent(app.packageName, capability)
                    if (!success) {
                        Toast.makeText(this@MainActivity, "Could not launch ${app.appName} with ${capability.displayName}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            override fun onAppSuggestionsRequested(apps: List<AppInfo>) {
                showAppSuggestions(apps)
            }
            
            override fun onMcpAppConnectionRequested(appId: String) {
                connectToMcpApp(appId)
            }
            
            override fun onParameterInputRequested(app: AppInfo, capability: IntentCapability) {
                showParameterInputDialog(app, capability)
            }
            
            override fun isBrowserVisible(): Boolean = browserUIManager.isVisible()
            
            override fun isInMcpContext(): Boolean = mcpManager.isInMcpContext()
            
            override fun getCurrentMcpContext(): String? = mcpManager.getCurrentContext()
            
            override fun getConnectedMcpApp(appId: String): McpApp? = mcpManager.getConnectedApp(appId)
            
            override fun isMcpAppConnected(appId: String): Boolean = mcpManager.isAppConnected(appId)
            
            override fun getMcpParameterCollectionState(): Any? = mcpManager.getParameterCollectionState()
            
            override fun getRecentlyUsedApps(limit: Int): List<AppInfo> {
                // Get recently used apps from UsageStatsManager
                return usageStatsManager.getRecentlyUsedApps(limit)
            }
        })
        
        // Register favorites listener to update Right Tray when favorites change
        favoritesManager.addListener(object : com.myagentos.app.data.manager.FavoritesManager.FavoritesListener {
            override fun onFavoritesChanged(favoriteAppIds: Set<String>) {
                runOnUiThread {
                    populateFavoritesInRightTray()
                }
            }
        })
        
        // Initial population of Right Tray with favorites
        populateFavoritesInRightTray()
        
        // Now that all managers are initialized, update UI state
        updateWelcomeAreaVisibility()
    }

    private fun checkFirstLaunch() {
        if (modelManager.isFirstLaunch()) {
            showModelSelectionDialog()
        } else {
            selectedModel = modelManager.getSelectedModel()
            initializeSelectedModel()
        }
    }

    private fun initializeSelectedModel() {
        selectedModel = modelManager.getSelectedModel()
        // Sync with ChatViewModel (MVVM)
        selectedModel?.let { chatViewModel.selectModel(it) }
        
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

    override fun onBackPressed() {
        // Intercept back button to close Right Tray instead of exiting app
        if (::trayManager.isInitialized && trayManager.isRightTrayVisible()) {
            android.util.Log.e("RightEdgeBlock", "🚫 Back pressed - closing Right Tray instead of app")
            trayManager.hideRightTray()
        } else {
            // Default behavior (exit app)
            super.onBackPressed()
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
        if (browserUIManager.isVisible()) {
            if (browserUIManager.isUrl(message)) {
                // Navigate to URL
                browserUIManager.loadUrl(message)
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
        conversationRepository.addMessage(userMessage)
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
        android.util.Log.d("MCP", "Active parameter collection: ${mcpManager.isCollectingParameters()}")
        android.util.Log.d("MCP", "Current MCP context: ${mcpManager.getCurrentContext()}")
        
        CoroutineScope(Dispatchers.IO).launch {
            android.util.Log.e("AI_RESPONSE", "Coroutine started on thread: ${Thread.currentThread().name}")
            try {
                android.util.Log.e("AI_RESPONSE", "About to call API...")
                
                // Intelligent routing based on context (using MCPManager - Phase 4)
                val aiResponse = when {
                    // 1. Active parameter collection - collecting required params
                    mcpManager.isCollectingParameters() -> {
                        android.util.Log.d("MCP", "Route: Parameter collection conversation")
                        mcpManager.continueParameterCollection(userMessage)
                    }
                    
                    // 2. In MCP context - check for tool invocation or parameter changes
                    mcpManager.isInMcpContext() -> {
                        android.util.Log.d("MCP", "Route: MCP context message")
                        mcpManager.handleContextMessage(userMessage) {
                            buildConversationHistory()
                        }
                    }
                    
                    // 3. Regular conversation - no MCP context
                    else -> {
                        android.util.Log.d("MCP", "Route: Regular conversation")
                        when (selectedModel) {
                            ModelType.EXTERNAL_CHATGPT, ModelType.EXTERNAL_GROK -> {
                                val conversationHistory = buildConversationHistory()
                                android.util.Log.d("MCP", "Built conversation history with ${conversationHistory.size} messages")
                                aiRepository.generateResponseWithHistory(userMessage, selectedModel!!, conversationHistory)
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
                        
                        conversationRepository.addMessage(aiMessage)
                        conversationRepository.saveCurrentConversation()
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
                
                // Update PillsCoordinator with loaded apps and show recent apps
                withContext(Dispatchers.Main) {
                    if (::pillsCoordinator.isInitialized) {
                        pillsCoordinator.updateInstalledApps(installedApps)
                        android.util.Log.d("PillsCoordinator", "Updated with ${installedApps.size} apps")
                        // Now show recent apps (after apps are loaded)
                        pillsCoordinator.showRecentApps()
                        android.util.Log.d("RecentApps", "Showing recent apps after loading ${installedApps.size} apps")
                    }
                }
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
                pillsCoordinator.detectAppsInText(text)
                // Apply inline pills with safe flag
                pillsCoordinator.applyInlinePills(text)
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
    
    
    // Conversation History Methods (now handled by ConversationHistoryActivity)
    
    private fun loadConversation(conversationId: Long) {
        val messages = loadConversationUseCase.execute(conversationId)
        if (messages != null) {
            // Clear current messages and load the conversation
            chatAdapter.clearMessages()
            
            // Clear MCP app context when loading a conversation (using MCPManager - Phase 4)
            mcpManager.clearState()
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
        conversationRepository.startNewConversation()
        chatAdapter.clearMessages()
        
        // Clear MCP app context (using MCPManager - Phase 4)
        mcpManager.clearState()
        android.util.Log.d("MCP", "Cleared MCP app context (new conversation)")
        
        // Update pills to show recent apps
        pillsCoordinator.showRecentApps()
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
        
        // Clear any existing dynamic blinks to prevent duplicates on activity recreate
        chatAdapter.clearDynamicBlinkCards()
        
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
    
    private fun updateWelcomeAreaVisibility() {
        val hasMessages = chatAdapter.itemCount > 0
        // Keep widgetDisplayArea GONE since cards are shown in RecyclerView instead
        // If VISIBLE, it blocks touch events to RecyclerView below it in the FrameLayout
        widgetDisplayArea.visibility = View.GONE
        trayManager.updateState()
        updateCardsVisibility()
    }
    
    // Delegate functions for TrayManager (Phase 4 - will be removed once all calls are updated)
    private fun showTray() = trayManager.showTray()
    private fun hideTray() = trayManager.hideTray()
    private fun showRightTray() {
        // Hide keyboard when opening right tray
        hideKeyboardAndClearFocus()
        trayManager.showRightTray()
    }
    private fun hideRightTray() = trayManager.hideRightTray()
    private fun toggleRightTray() = trayManager.toggleRightTray()
    private fun updateSwipeRefreshState() = trayManager.updateState()
    
    
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
        android.util.Log.e("CardsVisibility", ">>> updateCardsVisibility CALLED, isRightTrayVisible=${trayManager.isRightTrayVisible()}")
        
        // CRITICAL: If right tray is visible, do NOTHING - don't change any state
        if (trayManager.isRightTrayVisible()) {
            android.util.Log.e("CardsVisibility", "!!! RIGHT TRAY IS VISIBLE - SKIPPING ALL UPDATES !!!")
            return
        }
        
        // Cards show only when keyboard is NOT active AND there are no messages
        val hasMessages = chatAdapter.getMessages().isNotEmpty()
        val shouldShowCards = !isKeyboardVisible && !hasMessages
        android.util.Log.e("CardsVisibility", "isKeyboardVisible: $isKeyboardVisible, hasMessages: $hasMessages, isRightTrayVisible: ${trayManager.isRightTrayVisible()}, shouldShowCards: $shouldShowCards")
        android.util.Log.d("CardsVisibility", "Current itemCount: ${chatAdapter.itemCount}")
        chatAdapter.setShowCards(shouldShowCards)
        android.util.Log.d("CardsVisibility", "After setShowCards - itemCount: ${chatAdapter.itemCount}")
        
        // Clear MCP context when returning to cards view (using MCPManager - Phase 4)
        if (shouldShowCards) {
            mcpManager.clearState()
            android.util.Log.d("MCP", "Cleared MCP app context (returned to cards)")
            
            // Update pills to show recent apps
            pillsCoordinator.showRecentApps()
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


    // Job Grid Functions (moved to JobGridManager - Phase 4)
    
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
    

    
    private fun handleOverlayIntent(intent: Intent) {
        // Show job grid (from floating overlay double-tap)
        if (intent.getBooleanExtra("SHOW_JOB_GRID", false)) {
            window.decorView.post {
                jobGridManager.show()
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
    
    // Job execution callback for JobGridManager (Phase 4)
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
            browserUIManager.isVisible() -> {
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
                val response = aiRepository.generateResponse(contextMessage, ModelType.EXTERNAL_GROK)
                val apiTime = System.currentTimeMillis() - apiStartTime
                android.util.Log.d("JobGrid", "Grok API call completed in ${apiTime / 1000.0}s")
                
                withContext(Dispatchers.Main) {
                    // Hide typing indicator
                    chatAdapter.hideTypingIndicator()
                    
                    // Add job execution message
                    val jobMessage = ChatMessage("🤖 **${job.name}** executed:\n\n$response", isUser = false)
                    chatAdapter.addMessage(jobMessage)
                    conversationRepository.addMessage(jobMessage)
                    
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
    
    // Delegate functions for BrowserUIManager (Phase 4)
    private fun showBrowser() = browserUIManager.show()
    private fun hideBrowser() = browserUIManager.hide()
    private fun isUrl(text: String) = browserUIManager.isUrl(text)
    private fun loadUrl(url: String) = browserUIManager.loadUrl(url)
    
    // Chat with page content - delegates to BrowserUIManager and handles AI response
    private fun chatWithPageContent(userQuery: String) {
        // Show typing indicator
        chatAdapter.showTypingIndicator()
        chatRecyclerView.postDelayed({
            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }, 50)
        
        // Use BrowserUIManager to extract content and get context
        browserUIManager.chatWithPage(userQuery) { contextMessage, pageContent, extractionTime, contentLength, totalStartTime ->
            // Add user message to chat
            val userMessage = ChatMessage(userQuery, isUser = true)
            chatAdapter.addMessage(userMessage)
            conversationRepository.addMessage(userMessage)
            
            // Send to Grok with context
        val apiStartTime = System.currentTimeMillis()
        android.util.Log.d("Browser", "Starting Grok API call, context length: ${contextMessage.length} characters")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                    val response = aiRepository.generateResponse(contextMessage, ModelType.EXTERNAL_GROK)
                val apiTime = System.currentTimeMillis() - apiStartTime
                val totalTime = System.currentTimeMillis() - totalStartTime
                android.util.Log.d("Browser", "Grok API call completed in ${apiTime}ms")
                
                withContext(Dispatchers.Main) {
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
                        conversationRepository.addMessage(aiMessage)
                    
                    // Scroll to bottom
                    chatRecyclerView.postDelayed({
                        chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                    }, 50)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    chatAdapter.hideTypingIndicator()
                    val errorMessage = ChatMessage("Sorry, I couldn't process that. Error: ${e.message}", isUser = false)
                    chatAdapter.addMessage(errorMessage)
                }
            }
        }
    }
    }
    
    // Delegate functions for MCPManager (Phase 4 - Step 1)
    private fun connectToMcpApp(appId: String) {
        mcpManager.connectToApp(appId) { success, error ->
            if (success) {
                // Refresh pills to show tools
                mcpManager.getConnectedApp(appId)?.let { connectedApp ->
                    showSingleMcpAppWithTools(connectedApp)
                }
            } else {
                // Clear the "Connecting..." pill on error
                suggestionsLayout.removeAllViews()
                suggestionsScrollView.visibility = View.GONE
            }
        }
    }
    
    /**
     * Start a new chat with an MCP app (triggered from favorites)
     */
    private fun startNewChatWithMcpApp(app: McpApp) {
        android.util.Log.e("MainActivity", "🚀 Starting new chat with MCP app: ${app.name}")
        
        // 1. Clear current chat and start new conversation
        chatAdapter.clearMessages()
        conversationRepository.startNewConversation()
        
        // 2. FORCE chat mode (not cards)
        chatAdapter.setShowCards(false)
        updateCardsVisibility()
        
        // 3. Hide browser if visible
        if (browserUIManager.isVisible()) {
            browserUIManager.hide()
        }
        
        // 4. Ensure we're on the main chat screen (not Apps tab)
        currentTab = TabType.APPS // Keep track but don't show cards
        
        // 5. Show keyboard for input
        messageInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
        
        // 6. Add opening message from user
        val openingMessage = ChatMessage("Open ${app.name}", isUser = true)
        chatAdapter.addMessage(openingMessage)
        conversationRepository.addMessage(openingMessage)
        
        // 7. Scroll to show the message
        chatRecyclerView.post {
            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
        
        // 8. Connect to MCP app and show tools in pills
        mcpManager.connectToApp(app.id) { success, error ->
            if (success) {
                android.util.Log.e("MainActivity", "✅ MCP app ${app.name} connected successfully")
                
                // Show the connected app with tools in pills
                mcpManager.getConnectedApp(app.id)?.let { connectedApp ->
                    runOnUiThread {
                        // Show MCP tool pills using PillsCoordinator
                        android.util.Log.e("MainActivity", "🎯 Showing MCP tools for app: ${app.id}")
                        pillsCoordinator.showMcpToolSuggestions(app.id)
                        
                        // Add AI acknowledgment message
                        val acknowledgmentMessage = ChatMessage(
                            "Connected to ${app.name}! You can now use the available tools shown above the keyboard.",
                            isUser = false
                        )
                        chatAdapter.addMessage(acknowledgmentMessage)
                        conversationRepository.addMessage(acknowledgmentMessage)
                        
                        // Scroll to bottom to show the new messages
                        chatRecyclerView.post {
                            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                        }
                    }
                }
            } else {
                android.util.Log.e("MainActivity", "❌ Failed to connect to MCP app: $error")
                
                // Show error message
                runOnUiThread {
                    val errorMessage = ChatMessage(
                        "Sorry, I couldn't connect to ${app.name}. ${error ?: "Unknown error"}",
                        isUser = false
                    )
                    chatAdapter.addMessage(errorMessage)
                    conversationRepository.addMessage(errorMessage)
                }
            }
        }
    }
    
    private fun invokeMcpTool(appId: String, toolName: String, providedParameters: Map<String, Any>? = null) {
        android.util.Log.e("MainActivity", ">>> invokeMcpTool called: $toolName from $appId")
        mcpManager.invokeTool(appId, toolName, providedParameters)
    }
    
    /**
     * Create fallback HTML when widget template can't be fetched
     */
    private fun createFallbackHtml(tool: com.myagentos.app.data.model.McpTool, templateUrl: String, structuredContent: org.json.JSONObject?): String {
        val structuredData = structuredContent?.toString() ?: "{}"
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>MCP Widget</title>
                <style>
                    body {
                        font-family: system-ui, -apple-system, sans-serif;
                        padding: 20px;
                        background: #1a1a1a;
                        color: #ffffff;
                    }
                    h2 { color: #00d4ff; }
                    pre {
                        background: #2a2a2a;
                        padding: 15px;
                        border-radius: 8px;
                        overflow-x: auto;
                    }
                    .warning {
                        background: #ff6b00;
                        color: white;
                        padding: 12px;
                        border-radius: 8px;
                        margin: 10px 0;
                    }
                </style>
            </head>
            <body>
                <h2>${tool.title ?: tool.name}</h2>
                <div class="warning">⚠️ Widget HTML could not be loaded</div>
                <p><strong>Template:</strong> $templateUrl</p>
                <p><strong>Structured Data:</strong></p>
                <pre>$structuredData</pre>
            </body>
            </html>
        """.trimIndent()
    }
    
    private fun showMcpAppDetails(appId: String) {
        android.util.Log.d("MCP", "Showing app details for: $appId")
        
        // Find the app in the directory
        val allApps = AppDirectory.getFeaturedApps()
        val app = allApps.find { it.id == appId }
        
        if (app != null) {
            // Check if app is connected and get tools
            val connectedApp = mcpManager.getConnectedApp(appId)
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
    
    private fun handleMcpConnectionLost(serverUrl: String) {
        android.util.Log.d("MCP", "Handling connection loss for: $serverUrl")
        
        // Find the app that was disconnected
        val disconnectedApp = mcpManager.getConnectedApps().values.find { it.serverUrl == serverUrl }
        if (disconnectedApp != null) {
            android.util.Log.d("MCP", "App disconnected: ${disconnectedApp.name}")
            
            // Disconnect via MCPManager
            mcpManager.disconnectFromApp(disconnectedApp.id)
            
            // Refresh UI
            chatAdapter.notifyDataSetChanged()
            
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
    
    // Build conversation history for AI context
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
                    // Skip WebView messages for cleaner context
                }
                MessageType.BLINK -> {
                    // Skip blink messages
                }
            }
        }
        
        android.util.Log.d("MCP", "Built conversation history: ${history.size} messages from ${messages.size} total messages")
        return history
    }
    
    // Delegate functions for DialogManager (Phase 4 - Step 3)
    private fun showModelSelectionDialog() {
        dialogManager.showModelSelectionDialog { modelType ->
            selectedModel = modelType
            modelManager.setSelectedModel(modelType)
            modelManager.setFirstLaunchCompleted()
            // Update ChatViewModel with selected model (MVVM)
            chatViewModel.selectModel(modelType)
            initializeSelectedModel()
        }
    }
    
    private fun showMcpAppConnectionDialog(mcpApp: McpApp) {
        dialogManager.showMcpAppConnectionDialog(
            mcpApp = mcpApp,
            onConnect = { connectToMcpApp(mcpApp.id) },
            onShowDetails = {
                val intent = Intent(this, McpAppDetailsActivity::class.java)
                intent.putExtra("APP_ID", mcpApp.id)
                startActivityForResult(intent, REQUEST_CODE_APP_DETAILS)
            }
        )
    }
    
    private fun showParameterInputDialog(app: AppInfo, capability: IntentCapability) {
        dialogManager.showParameterInputDialog(app, capability) { parameters ->
            val success = appManager.launchAppWithCallback(app.packageName, capability, parameters, this)
            if (!success) {
                Toast.makeText(this, "Could not launch ${app.appName} with ${capability.displayName}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showUsageStatsPermissionDialog() {
        dialogManager.showUsageStatsPermissionDialog(
            onOpenSettings = {
                usageStatsManager.requestUsageStatsPermission()
            },
            onCheckPermission = {
                // Check permission and show recent apps if granted
                pillsCoordinator.showRecentApps()
            }
        )
    }
    
    // Helper function to hide keyboard and clear focus (Phase 4 - Using UIHelpers)
    private fun hideKeyboardAndClearFocus() {
        UIHelpers.hideKeyboard(this, messageInput)
    }
    
    /**
     * Populate Right Tray with favorite MCP apps (iPhone-style icons)
     */
    private fun populateFavoritesInRightTray() {
        val promptsContainer = findViewById<LinearLayout>(R.id.promptsContainer)
        promptsContainer.removeAllViews()
        
        val favoriteAppIds = favoritesManager.getFavoriteMcpApps()
        val allApps = AppDirectory.getFeaturedApps()
        
        android.util.Log.d("MainActivity", "Populating Right Tray with ${favoriteAppIds.size} favorites")
        
        favoriteAppIds.forEach { appId ->
            val app = allApps.find { it.id == appId }
            if (app != null) {
                // Create iPhone-style app icon layout (icon + label)
                val appContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 8)
                    }
                    setPadding(4, 8, 4, 8)
                    
                    // Ensure container is clickable
                    isClickable = true
                    isFocusable = true
                    
                    // Handle click in touch listener (having both onClick and onTouch can conflict)
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                android.util.Log.e("MainActivity", "👆 TOUCH DOWN on ${app.name}")
                                alpha = 0.5f
                                true // Consume the event
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                android.util.Log.e("MainActivity", "👆 TOUCH UP on ${app.name}")
                                alpha = 1.0f
                                
                                // Trigger the click action HERE
                                android.util.Log.e("MainActivity", "🎯🎯🎯 Favorite MCP app clicked: ${app.name}")
                                
                                // Hide right tray after selection
                                trayManager.hideRightTray()
                                
                                // Start a new chat
                                startNewChatWithMcpApp(app)
                                
                                true // Consume the event
                            }
                            android.view.MotionEvent.ACTION_CANCEL -> {
                                android.util.Log.e("MainActivity", "👆 TOUCH CANCEL on ${app.name}")
                                alpha = 1.0f
                                true
                            }
                            else -> false
                        }
                    }
                }
                
                // App icon (rounded square with actual app image)
                val iconView = ImageView(this).apply {
                    val sizeInDp = 60
                    val sizeInPx = (sizeInDp * resources.displayMetrics.density).toInt()
                    
                    layoutParams = LinearLayout.LayoutParams(sizeInPx, sizeInPx)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    
                    // Set rounded corners background
                    background = resources.getDrawable(R.drawable.pill_background_mcp, null)
                    clipToOutline = true
                    setPadding(0, 0, 0, 0)
                    
                    // Load app icon using Coil (same as MCP cards)
                    if (!app.icon.isNullOrEmpty()) {
                        val imageLoader = coil.ImageLoader(this@MainActivity)
                        val request = coil.request.ImageRequest.Builder(this@MainActivity)
                            .data(app.icon)
                            .target(this)
                            .placeholder(R.drawable.ic_apps)
                            .error(R.drawable.ic_apps)
                            .build()
                        imageLoader.enqueue(request)
                        
                        android.util.Log.d("MainActivity", "Loading icon for ${app.name} from: ${app.icon}")
                    } else {
                        // No icon specified, use default
                        setImageResource(R.drawable.ic_apps)
                    }
                }
                
                // App label
                val labelView = TextView(this).apply {
                    text = app.name
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 10f
                    gravity = android.view.Gravity.CENTER
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 4, 0, 0)
                    }
                }
                
                appContainer.addView(iconView)
                appContainer.addView(labelView)
                promptsContainer.addView(appContainer)
                
                android.util.Log.d("MainActivity", "Added favorite icon for: ${app.name}")
            }
        }
        
        // If no favorites, show a message
        if (favoriteAppIds.isEmpty()) {
            val emptyMessage = TextView(this).apply {
                text = "No favorites yet.\n\nTap ❤️ on an app to add it here!"
                setTextColor(android.graphics.Color.GRAY)
                textSize = 11f
                gravity = android.view.Gravity.CENTER
                setPadding(8, 32, 8, 8)
            }
            promptsContainer.addView(emptyMessage)
        }
    }
    
    /**
     * Setup dynamic agent profile display
     */
    private fun setupAgentProfileListeners() {
        val agentsContainer = findViewById<LinearLayout>(R.id.agentsContainer)
        
        // Observe agents from database
        lifecycleScope.launch {
            agentManager.getAllAgentsFlow().collect { agents ->
                withContext(Dispatchers.Main) {
                    // Clear existing views
                    agentsContainer.removeAllViews()
                    
                    // Add each agent to the container
                    agents.forEach { agent ->
                        val agentView = createAgentView(agent)
                        agentsContainer.addView(agentView)
                    }
                    
                    // Always add the Add Agent button at the end
                    val addAgentView = createAddAgentButton()
                    agentsContainer.addView(addAgentView)
                }
            }
        }
    }
    
    /**
     * Create an agent view dynamically
     */
    private fun createAgentView(agent: com.myagentos.app.domain.model.Agent): View {
        val agentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (14 * resources.displayMetrics.density).toInt()
            }
        }
        
        // Agent icon
        val agentIcon = View(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                (40 * resources.displayMetrics.density).toInt(),
                (40 * resources.displayMetrics.density).toInt()
            )
            agent.iconResId?.let { setBackgroundResource(it) }
            setOnClickListener {
                lifecycleScope.launch {
                    agentManager.getAgent(agent.id)?.let { fullAgent ->
                        agentManager.showAgentProfile(fullAgent)
                    }
                }
            }
        }
        
        // Agent name
        val agentName = TextView(this).apply {
            text = agent.name
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (3 * resources.displayMetrics.density).toInt()
            }
        }
        
        agentLayout.addView(agentIcon)
        agentLayout.addView(agentName)
        
        return agentLayout
    }
    
    /**
     * Create the Add Agent button
     */
    private fun createAddAgentButton(): View {
        val addAgentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Add button
        val addButton = ImageButton(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                (40 * resources.displayMetrics.density).toInt(),
                (40 * resources.displayMetrics.density).toInt()
            )
            setBackgroundResource(R.drawable.add_agent_background)
            setImageResource(R.drawable.ic_add)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val padding = (8 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            contentDescription = "Add new agent"
            setOnClickListener {
                // Launch agent creation activity
                val intent = Intent(context, AgentCreationActivity::class.java)
                startActivityForResult(intent, AgentManager.REQUEST_CODE_CREATE_AGENT)
            }
        }
        
        // Add label
        val addLabel = TextView(this).apply {
            text = "Add"
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (3 * resources.displayMetrics.density).toInt()
            }
        }
        
        addAgentLayout.addView(addButton)
        addAgentLayout.addView(addLabel)
        
        return addAgentLayout
    }
    
    /**
     * Start a new chat with a specific agent
     */
    private fun startChatWithAgent(agent: com.myagentos.app.domain.model.Agent) {
        // Hide the tray
        trayManager.hideTray()
        
        // Clear current conversation
        conversationRepository.startNewConversation()
        chatAdapter.clearMessages()
        
        // Set the agent's system prompt for the conversation
        // TODO: When AI integration is complete, inject the agent's system prompt and personality prompt
        
        // Show a message indicating which agent is active
        val welcomeMessage = ChatMessage(
            text = "Starting chat with ${agent.name}!\n\n${agent.description}",
            isUser = false
        )
        chatAdapter.addMessage(welcomeMessage)
        
        // Focus on input
        messageInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
        
        android.util.Log.d("AgentProfile", "Started chat with ${agent.name}")
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
    
    // Show app suggestion pills (delegated to PillsCoordinator)
    private fun showAppSuggestions(apps: List<AppInfo>) {
        // This function is now handled by PillsCoordinator
        // Keep this stub for compatibility with existing code
        android.util.Log.d("MainActivity", "showAppSuggestions called with ${apps.size} apps")
    }
    
    // Show single MCP app with tools (delegated to PillsCoordinator)
    private fun showSingleMcpAppWithTools(mcpApp: McpApp) {
        android.util.Log.d("MainActivity", "showSingleMcpAppWithTools called for ${mcpApp.name}")
        // Show the MCP tool pills using PillsCoordinator
        pillsCoordinator.showMcpToolSuggestions(mcpApp.id)
    }

}
