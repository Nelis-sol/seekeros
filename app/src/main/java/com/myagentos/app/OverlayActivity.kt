package com.myagentos.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayActivity : AppCompatActivity(), CoroutineScope by CoroutineScope(Dispatchers.Main) {
    
    // Double-tap detection for job grid
    private var lastTapTime = 0L
    private var tapCount = 0
    private var isJobGridVisible = false
    
    // Job Grid related variables
    private lateinit var jobGridOverlay: View
    private val jobs = mutableListOf<Job>()
    
    // Top tray related variables
    private lateinit var hiddenTray: View
    private var isTrayVisible = false
    
    // SwipeRefreshLayout for pull-to-reveal
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    
    // Swipe-up gesture detection for hiding tray
    private var trayInitialY = 0f
    private var isTrayTracking = false
    
    // Right tray related variables
    private lateinit var rightTray: View
    private var isRightTrayVisible = false
    
    // Right-edge gesture detection
    private var rightStartX = 0f
    private var isTrackingRight = false
    
    // Chat related variables
    private lateinit var chatRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var messageInput: android.widget.EditText
    private lateinit var sendButton: android.widget.ImageButton
    private lateinit var chatAdapter: SimpleChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var externalAIService: ExternalAIService
    private lateinit var inputLayout: android.widget.LinearLayout
    private var isKeyboardVisible = false
    private var hasConversation = false // Track if user has sent at least one message
    
    // Broadcast receiver for closing overlay
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.myagentos.app.CLOSE_OVERLAY") {
                finish()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use transparent overlay theme
        setTheme(R.style.Theme_AgentOS_Overlay)
        
        // Make window transparent
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Use the new simple overlay layout
        setContentView(R.layout.activity_overlay)
        
        // CRITICAL: Disable system gesture navigation on edges so we can handle right-edge swipes
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
                android.util.Log.d("OverlayActivity", "System gesture insets: left=${gestureInsets.left}, right=${gestureInsets.right}")
                
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
                android.util.Log.d("OverlayActivity", "Set gesture exclusion rect: $rightEdgeRect")
                
                insets
            }
        }
        
        // Set up the overlay
        setupOverlay()
        
        // Set up job grid
        setupJobGrid()
        
        // Set up top tray
        setupTopTray()
        
        // Set up right tray
        setupRightTray()
        
        // Set up chat
        setupChat()
        
        // Set up close on back press
        setupBackPress()
        
        // Register broadcast receiver for close command
        val filter = IntentFilter("com.myagentos.app.CLOSE_OVERLAY")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Unregister broadcast receiver
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {
            // Receiver was not registered
        }
    }
    
    // Override dispatchTouchEvent to intercept right-edge gestures before system (matching MainActivity)
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
                        android.util.Log.d("OverlayActivity", "DOUBLE TAP DETECTED at (${event.x}, ${event.y})")
                        showJobGrid()
                        tapCount = 0
                        return true // Consume the event
                    }
                } else {
                    tapCount = 1
                }
                lastTapTime = currentTime
                
                // Handle right-edge tracking
                if (isNearRightEdge || isRightTrayVisible) {
                    rightStartX = event.rawX
                    isTrackingRight = true
                    android.util.Log.d("OverlayActivity", "Right-edge ACTION_DOWN at x=${event.rawX}, screenWidth=$screenWidth, nearEdge=$isNearRightEdge, trayVisible=$isRightTrayVisible")
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTrackingRight) {
                    val deltaX = event.rawX - rightStartX
                    android.util.Log.d("OverlayActivity", "Right-edge ACTION_MOVE deltaX=$deltaX")
                    
                    // Swipe left from right edge to open
                    if (!isRightTrayVisible && isNearRightEdge && deltaX < -100) {
                        android.util.Log.d("OverlayActivity", "LEFT SWIPE DETECTED! Opening tray - CONSUMING EVENT")
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
                        android.util.Log.d("OverlayActivity", "RIGHT SWIPE DETECTED! Closing tray - CONSUMING EVENT")
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
                    android.util.Log.d("OverlayActivity", "Right-edge touch ended, resetting tracking")
                    isTrackingRight = false
                }
            }
        }
        
        // Let normal event handling continue
        return super.dispatchTouchEvent(event)
    }
    
    
    private fun setupOverlay() {
        try {
            android.util.Log.d("OverlayActivity", "Setting up simple transparent overlay")
            
            // Make window and root transparent
            window.decorView.rootView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // Make window adjust for keyboard - use ADJUST_PAN to move input field up
            window.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            )
            
            // Set up close button
            val closeButton = findViewById<ImageButton>(R.id.closeButton)
            closeButton?.setOnClickListener {
                android.util.Log.d("OverlayActivity", "Close button clicked")
                finish()
            }
            
            android.util.Log.d("OverlayActivity", "Simple overlay setup complete!")
        } catch (e: Exception) {
            android.util.Log.e("OverlayActivity", "Error setting up overlay: ${e.message}", e)
        }
    }
    
    private fun setupBackPress() {
        // Close overlay when back is pressed
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isJobGridVisible -> hideJobGrid()
                    isTrayVisible -> hideTopTray()
                    isRightTrayVisible -> hideRightTray()
                    else -> {
                        android.util.Log.d("OverlayActivity", "Back pressed - closing overlay")
                        finish()
                    }
                }
            }
        })
    }
    
    // Job Grid Functions
    private fun setupJobGrid() {
        jobGridOverlay = findViewById(R.id.jobGridOverlay)
        
        // Set up click listener for overlay to close grid
        jobGridOverlay.setOnClickListener {
            hideJobGrid()
        }
        
        // Load default jobs for demo
        loadDefaultJobs()
    }
    
    private fun showJobGrid() {
        if (isJobGridVisible) return
        
        android.util.Log.d("OverlayActivity", "Showing job grid")
        
        isJobGridVisible = true
        jobGridOverlay.visibility = View.VISIBLE
        
        // Update job slots display
        updateJobSlots()
    }
    
    private fun hideJobGrid() {
        if (!isJobGridVisible) return
        
        android.util.Log.d("OverlayActivity", "Hiding job grid")
        
        isJobGridVisible = false
        jobGridOverlay.visibility = View.GONE
    }
    
    private fun updateJobSlots() {
        // Update the 6 job slots with current jobs
        for (i in 0 until 6) {
            val slotId = when (i) {
                0 -> R.id.jobSlot1
                1 -> R.id.jobSlot2
                2 -> R.id.jobSlot3
                3 -> R.id.jobSlot4
                4 -> R.id.jobSlot5
                5 -> R.id.jobSlot6
                else -> continue
            }
            
            val slot = jobGridOverlay.findViewById<FrameLayout>(slotId)
            slot.visibility = View.VISIBLE
            
            if (i == 0) {
                // First slot is always "New Job" - already set up in layout
                continue
            }
            
            // Clear existing views (except for slot 1 which has static content)
            slot.removeAllViews()
            
            if (i - 1 < jobs.size) {
                // Show existing job
                val job = jobs[i - 1]
                setupJobSlot(slot, job)
            } else {
                // Show empty slot
                setupEmptySlot(slot)
            }
        }
    }
    
    private fun setupJobSlot(slot: FrameLayout, job: Job) {
        slot.removeAllViews()
        
        // Create container for consistent positioning (matching MainActivity)
        val container = FrameLayout(this)
        container.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        // Add icon in center (only if not empty) - using emoji text like MainActivity
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
        
        // Add job name at bottom (matching MainActivity styling)
        if (job.name.isNotEmpty()) {
            val nameText = TextView(this)
            nameText.text = job.name
            nameText.textSize = 12f
            nameText.setTextColor(android.graphics.Color.WHITE)
            nameText.alpha = 0.8f
            nameText.gravity = android.view.Gravity.CENTER
            nameText.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER or android.view.Gravity.BOTTOM
            ).apply {
                bottomMargin = 12.dpToPx()
            }
            container.addView(nameText)
        }
        
        slot.addView(container)
        
        // Set click listener to execute the job
        slot.setOnClickListener {
            android.util.Log.d("OverlayActivity", "Job clicked: ${job.name}")
            hideJobGrid()
            // For now, just show a toast - we'll implement full job execution later
            android.widget.Toast.makeText(this, "Job '${job.name}' would execute here", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupEmptySlot(slot: FrameLayout) {
        // Empty slot - no content, just the background
    }
    
    private fun loadDefaultJobs() {
        jobs.clear()
        jobs.addAll(listOf(
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
        ))
        android.util.Log.d("OverlayActivity", "Loaded ${jobs.size} default jobs")
    }
    
    // Top Tray Functions
    private fun setupTopTray() {
        hiddenTray = findViewById(R.id.hiddenTray)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        
        // Setup SwipeRefreshLayout for pull-to-reveal tray (matching MainActivity)
        swipeRefreshLayout.setOnRefreshListener {
            android.util.Log.d("OverlayActivity", "onRefresh triggered! isTrayVisible=$isTrayVisible")
            // Immediately stop refresh and toggle tray with zero latency
            swipeRefreshLayout.isRefreshing = false
            
            // Only toggle if tray is not already visible (prevent double-trigger)
            if (!isTrayVisible) {
                showTopTray()
            } else {
                android.util.Log.d("OverlayActivity", "Ignoring onRefresh - tray already visible")
            }
        }
        
        // Enable refresh functionality
        swipeRefreshLayout.isEnabled = true
        
        // Completely disable the refresh indicator for zero latency (matching MainActivity)
        swipeRefreshLayout.setColorSchemeColors(android.graphics.Color.TRANSPARENT)
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(android.graphics.Color.TRANSPARENT)
        swipeRefreshLayout.setSize(androidx.swiperefreshlayout.widget.SwipeRefreshLayout.DEFAULT)
        
        // Set minimal distance to trigger for faster response
        swipeRefreshLayout.setDistanceToTriggerSync(100)
        
        // Completely hide the refresh indicator by setting it to never show
        try {
            val progressCircle = swipeRefreshLayout.javaClass.getDeclaredField("mCircleView")
            progressCircle.isAccessible = true
            val circleView = progressCircle.get(swipeRefreshLayout) as android.view.View
            circleView.visibility = View.GONE
        } catch (e: Exception) {
            android.util.Log.d("OverlayActivity", "Could not hide refresh indicator: ${e.message}")
        }
        
        // Set up click listeners for tray buttons (placeholder functionality)
        findViewById<ImageButton>(R.id.historyButton)?.setOnClickListener {
            android.util.Log.d("OverlayActivity", "History button clicked")
            android.widget.Toast.makeText(this, "History feature coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        findViewById<ImageButton>(R.id.newChatButton)?.setOnClickListener {
            android.util.Log.d("OverlayActivity", "New Chat button clicked")
            android.widget.Toast.makeText(this, "New Chat feature coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        findViewById<ImageButton>(R.id.addAgentButton)?.setOnClickListener {
            android.util.Log.d("OverlayActivity", "Add Agent button clicked")
            android.widget.Toast.makeText(this, "Add Agent feature coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // Add swipe-up gesture to hide tray
        hiddenTray.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isTrayVisible) {
                        trayInitialY = event.y
                        isTrayTracking = true
                        android.util.Log.d("OverlayActivity", "Starting tray swipe-up tracking from y=${event.y}")
                    }
                    true // Consume the down event to ensure we get subsequent events
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isTrayTracking && isTrayVisible) {
                        val deltaY = event.y - trayInitialY
                        android.util.Log.d("OverlayActivity", "Tray swipe deltaY=$deltaY")
                        if (deltaY < -80) { // Reduced threshold for easier dismissal
                            android.util.Log.d("OverlayActivity", "Swipe-up threshold reached! deltaY=$deltaY")
                            hideTopTray()
                            isTrayTracking = false
                            return@setOnTouchListener true // Consume the event
                        }
                    }
                    isTrayTracking // Return true if we're tracking, false otherwise
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasTracking = isTrayTracking
                    isTrayTracking = false
                    wasTracking // Return true if we were tracking, false otherwise
                }
                else -> false
            }
        }
    }
    
    private fun showTopTray() {
        if (isTrayVisible) return
        
        android.util.Log.d("OverlayActivity", "Showing top tray")
        
        isTrayVisible = true
        hiddenTray.visibility = View.VISIBLE
        
        // Animate the tray height to make it visible
        val layoutParams = hiddenTray.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        layoutParams.height = 100.dpToPx() // Increased height for better text readability
        hiddenTray.layoutParams = layoutParams
        
        // Disable SwipeRefreshLayout when tray is visible (matching MainActivity)
        swipeRefreshLayout.isEnabled = false
    }
    
    private fun hideTopTray() {
        if (!isTrayVisible) return
        
        android.util.Log.d("OverlayActivity", "Hiding top tray")
        
        isTrayVisible = false
        hiddenTray.visibility = View.GONE
        
        // Reset the tray height
        val layoutParams = hiddenTray.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        layoutParams.height = 0
        hiddenTray.layoutParams = layoutParams
        
        // Re-enable SwipeRefreshLayout when tray is hidden (matching MainActivity)
        swipeRefreshLayout.isEnabled = true
    }
    
    // Right Tray Functions
    private fun setupRightTray() {
        rightTray = findViewById(R.id.rightTray)
        
        // Initially hide right tray (set position without animation)
        rightTray.visibility = View.GONE
        isRightTrayVisible = false
        
        // Set initial translation after layout
        rightTray.post {
            rightTray.translationX = rightTray.width.toFloat()
        }
        
        android.util.Log.d("OverlayActivity", "Right tray setup complete")
    }
    
    private fun showRightTray() {
        if (isRightTrayVisible) return
        
        android.util.Log.d("OverlayActivity", "Showing right tray")
        
        // Set flag FIRST before any UI changes
        isRightTrayVisible = true
        
        // Show the tray immediately
        rightTray.translationX = 0f
        rightTray.visibility = View.VISIBLE
    }
    
    private fun hideRightTray() {
        if (!isRightTrayVisible) return
        
        android.util.Log.d("OverlayActivity", "Hiding right tray")
        
        // Hide the tray
        isRightTrayVisible = false
        rightTray.translationX = rightTray.width.toFloat()
        rightTray.visibility = View.GONE
    }
    
    // Chat Functions
    private fun setupChat() {
        // Initialize AI service
        externalAIService = ExternalAIService()
        
        // Add invisible placeholder to prevent welcome header from showing
        messages.add(ChatMessage(" ", isUser = false))
        
        // Set up RecyclerView
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        chatRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        chatAdapter = SimpleChatAdapter(messages, showCards = false) // Don't show cards or welcome header
        chatRecyclerView.adapter = chatAdapter
        
        // Set up input and send button
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        inputLayout = findViewById(R.id.inputLayout)
        
        // Set up keyboard visibility listener to adjust transparency
        setupKeyboardListener()
        
        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                messageInput.text.clear()
            }
        }
        
        // Handle send action from keyboard
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val message = messageInput.text.toString().trim()
                if (message.isNotEmpty()) {
                    sendMessage(message)
                    messageInput.text.clear()
                }
                true
            } else {
                false
            }
        }
        
        android.util.Log.d("OverlayActivity", "Chat setup complete")
    }
    
    private fun setupKeyboardListener() {
        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val keyboardNowVisible = keypadHeight > screenHeight * 0.15 // Keyboard is visible if it takes more than 15% of screen
            
            android.util.Log.d("OverlayActivity", "Keyboard check: screenHeight=$screenHeight, keypadHeight=$keypadHeight, isVisible=$keyboardNowVisible, wasVisible=$isKeyboardVisible, hasConversation=$hasConversation")
            
            // Update transparency based on keyboard state and conversation state
            if (keyboardNowVisible != isKeyboardVisible) {
                isKeyboardVisible = keyboardNowVisible
                updateTransparency()
            }
        }
    }
    
    private fun updateTransparency() {
        // Use darker background (75% opacity) if keyboard is visible OR if there's a conversation
        val shouldBeDark = isKeyboardVisible || hasConversation
        
        if (shouldBeDark) {
            android.util.Log.d("OverlayActivity", "Setting dark background (keyboard=$isKeyboardVisible, hasConversation=$hasConversation)")
            chatRecyclerView.setBackgroundColor(android.graphics.Color.parseColor("#BF000000")) // 75% opacity
            inputLayout.setBackgroundColor(android.graphics.Color.parseColor("#BF000000")) // 75% opacity
        } else {
            android.util.Log.d("OverlayActivity", "Setting transparent background")
            chatRecyclerView.setBackgroundColor(android.graphics.Color.parseColor("#33000000")) // 20% opacity
            inputLayout.setBackgroundColor(android.graphics.Color.parseColor("#33000000")) // 20% opacity
        }
    }
    
    private fun sendMessage(message: String) {
        // Mark that we have a conversation now
        if (!hasConversation) {
            hasConversation = true
            updateTransparency() // Update transparency to darker for better message readability
        }
        
        // Add user message
        messages.add(ChatMessage(message, isUser = true))
        chatAdapter.notifyItemInserted(messages.size - 1)
        chatRecyclerView.scrollToPosition(messages.size - 1)
        
        android.util.Log.d("OverlayActivity", "Sending message to Grok: $message")
        
        // Get AI response
        launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    externalAIService.generateResponse(message, ModelType.EXTERNAL_GROK)
                }
                android.util.Log.d("OverlayActivity", "Received Grok response: $response")
                
                // Add AI response
                messages.add(ChatMessage(response, isUser = false))
                chatAdapter.notifyItemInserted(messages.size - 1)
                chatRecyclerView.scrollToPosition(messages.size - 1)
            } catch (e: Exception) {
                android.util.Log.e("OverlayActivity", "Error getting Grok response", e)
                
                // Add error message
                messages.add(ChatMessage("Sorry, I encountered an error: ${e.message}", isUser = false))
                chatAdapter.notifyItemInserted(messages.size - 1)
                chatRecyclerView.scrollToPosition(messages.size - 1)
            }
        }
    }
    
    // Extension function to convert dp to pixels
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}

