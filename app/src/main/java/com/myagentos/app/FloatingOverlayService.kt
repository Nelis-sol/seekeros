package com.myagentos.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isButtonVisible = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        android.util.Log.d("FloatingOverlayService", "Service onCreate() called")
        
        // Create floating button
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_overlay, null)
        
        // Set up window manager parameters for small floating button
        val density = resources.displayMetrics.density
        val width = (80 * density).toInt()   // 80dp in pixels (narrower)
        val height = (120 * density).toInt() // 120dp in pixels (taller)
        
        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }
        
        params.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        val screenWidth = resources.displayMetrics.widthPixels
        params.x = screenWidth - (width / 2)  // Position so half extends beyond right edge
        params.y = 0
        
        // Allow the window to extend beyond screen boundaries
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        
        // Add view to window
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager?.addView(floatingView, params)
        
        // Set up touch listener for drag and tap
        setupTouchListener(params)
        
        Toast.makeText(this, "Floating button active! Tap to open AgentOS", Toast.LENGTH_SHORT).show()
    }
    
    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var isDragging = false
            
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Record initial position
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        
                        // Only consider it dragging if moved more than 10px
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true
                            params.x = initialX + deltaX.toInt()
                            params.y = initialY + deltaY.toInt()
                            windowManager?.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                    
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // Tap - open main app
                            v.performClick()
                            openMainApp()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }
    
    private fun openMainApp() {
        // Check if OverlayActivity is already running using a simpler approach
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningTasks = activityManager.getRunningTasks(1)
        
        val topActivity = runningTasks.firstOrNull()?.topActivity
        val topClassName = topActivity?.className
        
        android.util.Log.d("FloatingOverlayService", "Button pressed. Top activity: $topClassName")
        
        // Check if OverlayActivity is already running
        val isOverlayRunning = topClassName == OverlayActivity::class.java.name
        
        if (isOverlayRunning) {
            // Close the overlay by sending a broadcast
            android.util.Log.d("FloatingOverlayService", "Closing OverlayActivity")
            val closeIntent = Intent("com.myagentos.app.CLOSE_OVERLAY")
            sendBroadcast(closeIntent)
        } else {
            // Open the transparent overlay activity
            android.util.Log.d("FloatingOverlayService", "Opening OverlayActivity")
            val intent = Intent(this, OverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }
    }

    private fun hideFloatingButton() {
        if (isButtonVisible && floatingView != null) {
            windowManager?.removeView(floatingView)
            isButtonVisible = false
        }
    }
    
    private fun showFloatingButton() {
        if (!isButtonVisible && floatingView != null) {
            val density = resources.displayMetrics.density
            val width = (80 * density).toInt()
            val height = (120 * density).toInt()
            
            val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams(
                    width,
                    height,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams(
                    width,
                    height,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            }
            
            params.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            val screenWidth = resources.displayMetrics.widthPixels
            params.x = screenWidth - (width / 2)
            params.y = 0
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            
            windowManager?.addView(floatingView, params)
            setupTouchListener(params)
            isButtonVisible = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("FloatingOverlayService", "Service onDestroy() called - button being removed")
        floatingView?.let { windowManager?.removeView(it) }
        Toast.makeText(this, "Floating button removed", Toast.LENGTH_SHORT).show()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("FloatingOverlayService", "Service onStartCommand() called")
        // Return START_STICKY so the service restarts if killed
        return START_STICKY
    }
}

