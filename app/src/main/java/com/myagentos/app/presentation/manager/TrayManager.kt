package com.myagentos.app.presentation.manager

import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * TrayManager - Manages top and right edge tray functionality
 * 
 * Responsibilities:
 * - Pull-to-reveal top tray (hidden tray with widgets)
 * - Swipe from right edge to open right tray
 * - Swipe gestures for showing/hiding trays
 * - Tray state management
 * 
 * Extracted from MainActivity to reduce complexity
 */
class TrayManager(
    private val hiddenTray: LinearLayout,
    private val rightTray: LinearLayout,
    private val swipeRefreshLayout: SwipeRefreshLayout,
    private val chatRecyclerView: RecyclerView,
    private val widgetDisplayArea: ScrollView
) {
    
    // State variables
    private var isTrayVisible = false
    @Volatile private var isRightTrayVisible = false
    private var startY = 0f
    private var startX = 0f
    private var isTracking = false
    private var isTrackingRight = false
    
    // WebView touch tracking for pull-to-reveal from browser
    private var webViewStartY = 0f
    private var webViewStartScrollY = 0
    
    // Callbacks
    private var onRightTrayVisibilityChanged: ((Boolean) -> Unit)? = null
    
    /**
     * Initialize tray setup
     */
    fun setup() {
        setupTopTray()
        setupRightTray()
        android.util.Log.d("TrayManager", "Tray setup complete")
    }
    
    /**
     * Setup pull-to-reveal top tray
     */
    private fun setupTopTray() {
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
        swipeRefreshLayout.setSize(SwipeRefreshLayout.DEFAULT)
        
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
        
        // Setup upward swipe listener for closing tray
        setupUpwardSwipeListener()
        
        // Setup tray click to close (fallback method)
        hiddenTray.setOnClickListener {
            if (isTrayVisible) {
                hideTray()
            }
        }
        
        // Initially hide the tray
        hideTray()
    }
    
    /**
     * Setup right edge tray
     */
    private fun setupRightTray() {
        // Initially hide right tray (set position without animation)
        rightTray.visibility = View.GONE
        isRightTrayVisible = false
        // Set initial translation after layout
        rightTray.post {
            rightTray.translationX = rightTray.width.toFloat()
        }
        
        // Setup overlay click to close
        rightTray.post {
            val rootView = rightTray.rootView
            val overlay = rootView.findViewById<View>(rootView.context.resources.getIdentifier("rightTrayOverlay", "id", rootView.context.packageName))
            overlay?.setOnClickListener {
                android.util.Log.e("RightTrayOverlay", "Overlay clicked - closing Right Tray")
                hideRightTray()
            }
        }
        
        // NOTE: Don't set touch listener on rightTray parent - it blocks child clicks!
        // Swipe-to-close is already handled in MainActivity's handleRightEdgeTouch()
    }
    
    /**
     * Toggle top tray visibility
     */
    private fun toggleTray() {
        android.util.Log.d("TrayToggle", "toggleTray called, current state: isTrayVisible=$isTrayVisible")
        if (isTrayVisible) {
            hideTray()
        } else {
            showTray()
        }
    }
    
    /**
     * Show top tray
     */
    fun showTray() {
        android.util.Log.d("TrayToggle", "showTray called")
        // Zero latency: direct layout parameter change
        val layoutParams = hiddenTray.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
        hiddenTray.layoutParams = layoutParams
        isTrayVisible = true
        android.util.Log.d("TrayToggle", "Tray shown, isTrayVisible=$isTrayVisible")
        updateSwipeRefreshState()
    }
    
    /**
     * Hide top tray
     */
    fun hideTray() {
        android.util.Log.d("TrayToggle", "hideTray called")
        // Zero latency: direct layout parameter change
        val layoutParams = hiddenTray.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.height = 0
        hiddenTray.layoutParams = layoutParams
        isTrayVisible = false
        android.util.Log.d("TrayToggle", "Tray hidden, isTrayVisible=$isTrayVisible")
        updateSwipeRefreshState()
    }
    
    /**
     * Update swipe refresh state based on RecyclerView scroll position
     */
    private fun updateSwipeRefreshState() {
        val canScrollUp = chatRecyclerView.canScrollVertically(-1)
        swipeRefreshLayout.isEnabled = !canScrollUp && !isTrayVisible
        android.util.Log.d("SwipeRefresh", "isEnabled=${swipeRefreshLayout.isEnabled}, canScrollUp=$canScrollUp, isTrayVisible=$isTrayVisible")
    }
    
    /**
     * Setup upward swipe listener to close top tray
     */
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
        widgetDisplayArea.setOnTouchListener(touchListener)
        
        // Apply to hiddenTray for upward swipe to close when tray is visible
        hiddenTray.setOnTouchListener(touchListener)
    }
    
    /**
     * Toggle right edge tray visibility
     */
    fun toggleRightTray() {
        android.util.Log.d("RightTrayToggle", "toggleRightTray called, current state: isRightTrayVisible=$isRightTrayVisible")
        if (isRightTrayVisible) {
            hideRightTray()
        } else {
            showRightTray()
        }
    }
    
    /**
     * Show right edge tray
     */
    fun showRightTray() {
        android.util.Log.e("RightTrayToggle", "==================== showRightTray CALLED ====================")
        
        // CRITICAL: Set flag FIRST before any UI changes
        isRightTrayVisible = true
        android.util.Log.e("RightTrayToggle", "Set isRightTrayVisible=true")
        
        // Show overlay (click outside to close)
        val rootView = rightTray.rootView
        val overlay = rootView.findViewById<View>(rootView.context.resources.getIdentifier("rightTrayOverlay", "id", rootView.context.packageName))
        overlay?.visibility = View.VISIBLE
        
        // Show the tray immediately (no post needed since we're canceling events properly)
        rightTray.translationX = 0f
        rightTray.visibility = View.VISIBLE
        
        // Notify callback
        onRightTrayVisibilityChanged?.invoke(true)
        
        android.util.Log.e("RightTrayToggle", "Tray shown, visibility=${rightTray.visibility}, overlay visible")
        android.util.Log.e("RightTrayToggle", "==================== showRightTray DONE ====================")
    }
    
    /**
     * Hide right edge tray
     */
    fun hideRightTray() {
        android.util.Log.e("RightTrayToggle", "==================== hideRightTray CALLED ====================")
        
        // CRITICAL: Set flag FIRST before any UI changes
        isRightTrayVisible = false
        android.util.Log.e("RightTrayToggle", "Set isRightTrayVisible=false")
        
        // Hide overlay
        val rootView = rightTray.rootView
        val overlay = rootView.findViewById<View>(rootView.context.resources.getIdentifier("rightTrayOverlay", "id", rootView.context.packageName))
        overlay?.visibility = View.GONE
        
        // Hide the tray immediately
        rightTray.translationX = rightTray.width.toFloat()
        rightTray.visibility = View.GONE
        
        // Notify callback
        onRightTrayVisibilityChanged?.invoke(false)
        
        android.util.Log.e("RightTrayToggle", "Tray hidden, visibility=${rightTray.visibility}, overlay hidden")
        android.util.Log.e("RightTrayToggle", "==================== hideRightTray DONE ====================")
    }
    
    /**
     * Handle touch events for right edge swipe detection
     * Called from Activity's dispatchTouchEvent
     * 
     * @return true if event was consumed, false otherwise
     */
    fun handleRightEdgeTouch(event: MotionEvent, screenWidth: Int, edgeThreshold: Float): Boolean {
        val isNearRightEdge = event.rawX > (screenWidth - edgeThreshold)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
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
                        android.util.Log.e("RightEdgeIntercept", "✅ LEFT SWIPE DETECTED! Opening tray - CONSUMING EVENT")
                        showRightTray()
                        isTrackingRight = false
                        return true // Consume event
                    }
                    // Swipe right to close (if tray is open)
                    else if (isRightTrayVisible && deltaX > 80) {
                        android.util.Log.e("RightEdgeIntercept", "✅ RIGHT SWIPE DETECTED! Closing tray - CONSUMING EVENT")
                        hideRightTray()
                        isTrackingRight = false
                        return true // Consume event
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isTrackingRight) {
                    android.util.Log.d("RightEdgeIntercept", "ACTION_UP - resetting tracking (tray state unchanged)")
                    isTrackingRight = false
                    // If tray was opened during this gesture, keep it open!
                    if (isRightTrayVisible) {
                        android.util.Log.e("RightEdgeIntercept", "🔒 Right Tray is OPEN - keeping it open despite ACTION_UP")
                        return true // Consume to prevent any other handlers
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                // CRITICAL: System gesture sends CANCEL - but we KEEP the tray open!
                if (isTrackingRight) {
                    android.util.Log.e("RightEdgeIntercept", "⚠️ ACTION_CANCEL detected (likely system gesture)")
                    android.util.Log.e("RightEdgeIntercept", "🔒 Right Tray state: ${if (isRightTrayVisible) "OPEN" else "CLOSED"}")
                    
                    // If tray is open, KEEP IT OPEN despite CANCEL
                    if (isRightTrayVisible) {
                        android.util.Log.e("RightEdgeIntercept", "🔒 IGNORING CANCEL - keeping Right Tray OPEN!")
                        isTrackingRight = false
                        return true // Consume the cancel event
                    }
                    
                    isTrackingRight = false
                }
            }
        }
        
        return false // Don't consume by default
    }
    
    /**
     * Get WebView touch listener for pull-to-reveal from browser
     */
    fun getWebViewTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    webViewStartY = event.rawY
                    // Note: WebView's scrollY needs to be checked in onTouch
                    false // Don't consume the event
                }
                MotionEvent.ACTION_MOVE -> {
                    // This will need WebView.scrollY from the caller
                    // For now, return false
                    false
                }
                else -> false
            }
        }
    }
    
    /**
     * Handle WebView pull-to-reveal
     * Call this from WebView's touch listener
     */
    fun handleWebViewPullDown(scrollY: Int, startScrollY: Int, deltaY: Float): Boolean {
        // Check if we're at the top of the WebView and pulling down
        if (scrollY == 0 && startScrollY == 0 && deltaY > 50) {
            // Show the hidden tray
            if (!isTrayVisible) {
                showTray()
            }
            return true // Consume the event
        }
        return false
    }
    
    /**
     * Set callback for right tray visibility changes
     */
    fun setOnRightTrayVisibilityChanged(callback: (Boolean) -> Unit) {
        onRightTrayVisibilityChanged = callback
    }
    
    /**
     * Check if top tray is visible
     */
    fun isTrayVisible(): Boolean = isTrayVisible
    
    /**
     * Check if right tray is visible
     */
    fun isRightTrayVisible(): Boolean = isRightTrayVisible
    
    /**
     * Update swipe refresh state (call when UI state changes)
     */
    fun updateState() {
        updateSwipeRefreshState()
    }
}

