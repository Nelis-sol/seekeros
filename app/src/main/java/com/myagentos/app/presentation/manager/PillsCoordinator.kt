package com.myagentos.app.presentation.manager

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.*
import coil.ImageLoader
import coil.request.ImageRequest
import com.myagentos.app.R
import com.myagentos.app.domain.model.AppInfo
import com.myagentos.app.domain.model.IntentCapability
import com.myagentos.app.data.model.McpApp
import com.myagentos.app.data.model.McpTool
import com.myagentos.app.data.source.AppDirectory
import com.myagentos.app.data.manager.AppManager
import com.myagentos.app.domain.repository.McpRepository
import org.json.JSONObject

/**
 * PillsCoordinator - Manages all pill-related UI logic and app detection
 * 
 * Responsibilities:
 * - Inline pill rendering (custom spans)
 * - App detection in text input
 * - MCP app detection and keyword matching
 * - Pill creation (app, MCP, tool, parameter pills)
 * - Pill display management and suggestions
 * 
 * Extracted from MainActivity to reduce complexity and improve maintainability.
 */
class PillsCoordinator(
    private val context: Context,
    private val messageInput: EditText,
    private val suggestionsLayout: LinearLayout,
    private val suggestionsScrollView: HorizontalScrollView,
    private val appManager: AppManager,
    private val mcpRepository: McpRepository,
    installedApps: List<AppInfo>
) {
    
    // Mutable list of installed apps (can be updated after initialization)
    private var installedApps: List<AppInfo> = installedApps
    
    // Update the installed apps list (called after async loading)
    fun updateInstalledApps(apps: List<AppInfo>) {
        installedApps = apps
        android.util.Log.d("PillsCoordinator", "Updated with ${apps.size} apps")
    }
    
    // Callbacks for MainActivity integration
    interface PillsCallbacks {
        fun onAppPillClicked(app: AppInfo)
        fun onMcpAppPillClicked(mcpApp: McpApp)
        fun onMcpToolPillClicked(appId: String, tool: McpTool)
        fun onParameterPillClicked(paramName: String, paramSchema: JSONObject?, required: Boolean)
        fun onIntentPillClicked(app: AppInfo, capability: IntentCapability)
        fun onAppSuggestionsRequested(apps: List<AppInfo>)
        fun onMcpAppConnectionRequested(appId: String)
        fun onParameterInputRequested(app: AppInfo, capability: IntentCapability)
        fun isBrowserVisible(): Boolean
        fun isInMcpContext(): Boolean
        fun getCurrentMcpContext(): String?
        fun getConnectedMcpApp(appId: String): McpApp?
        fun isMcpAppConnected(appId: String): Boolean
        fun getMcpParameterCollectionState(): Any? // ParameterCollectionState
        fun getRecentlyUsedApps(limit: Int): List<AppInfo> // Get recently used apps from UsageStatsManager
    }
    
    private var callbacks: PillsCallbacks? = null
    private var isUpdatingPills = false
    
    fun setCallbacks(callbacks: PillsCallbacks) {
        this.callbacks = callbacks
    }
    
    // ============================================================================
    // INLINE PILLS LOGIC
    // ============================================================================
    
    /**
     * Apply inline pills to the text input
     */
    fun applyInlinePills(text: String) {
        if (text.isEmpty() || isUpdatingPills || callbacks?.isBrowserVisible() == true) return
        
        val spannableString = SpannableString(text)
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
                    val clickableSpan = object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            // Show suggestions for this specific app
                            callbacks?.onAppSuggestionsRequested(listOf(app))
                        }
                        
                        override fun updateDrawState(ds: android.text.TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = false // Remove underline
                        }
                    }
                    
                    // Create custom span for the pill with icon and styling
                    val pillSpan = createPillSpan(app)
                    
                    // Apply pill styling with custom span
                    spannableString.setSpan(clickableSpan, index, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(pillSpan, index, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(ForegroundColorSpan(Color.WHITE), index, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), index, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    
                    hasPills = true
                    startIndex = endIndex
                }
            }
        }
        
        // Apply the spannable string if we found any pills
        if (hasPills) {
            isUpdatingPills = true
            try {
                messageInput.setText(spannableString)
                messageInput.setSelection(spannableString.length)
            } finally {
                isUpdatingPills = false // Always reset flag
            }
        }
    }
    
    /**
     * Create a custom span for the pill with icon and styling
     */
    private fun createPillSpan(app: AppInfo): ReplacementSpan {
        return object : ReplacementSpan() {
            override fun getSize(paint: android.graphics.Paint, text: CharSequence?, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int {
                val iconSize = (16 * context.resources.displayMetrics.density).toInt() // Smaller icon: 16dp
                val horizontalPadding = (8 * context.resources.displayMetrics.density).toInt() // Less padding: 8dp
                val iconTextGap = (4 * context.resources.displayMetrics.density).toInt() // Gap between icon and text: 4dp
                val verticalPadding = (8 * context.resources.displayMetrics.density).toInt() // Vertical padding: 8dp
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
                val iconSize = (16 * context.resources.displayMetrics.density).toInt() // Smaller icon: 16dp
                val horizontalPadding = (8 * context.resources.displayMetrics.density).toInt() // Less padding: 8dp
                val iconTextGap = (4 * context.resources.displayMetrics.density).toInt() // Gap between icon and text: 4dp
                val verticalPadding = (8 * context.resources.displayMetrics.density).toInt() // Vertical padding: 8dp
                val cornerRadius = 12 * context.resources.displayMetrics.density // Smaller radius: 12dp
                
                // Measure text
                val textWidth = paint.measureText(text, start, end)
                val totalWidth = horizontalPadding + iconSize + iconTextGap + textWidth + horizontalPadding
                val totalHeight = (bottom - top).toFloat()
                
                // Draw background with rounded corners (fill the entire allocated space)
                val rect = android.graphics.RectF(x, top.toFloat(), x + totalWidth, bottom.toFloat())
                val bgPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = Color.parseColor("#3A3A3C")
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
                
                // Draw border
                val borderPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = Color.parseColor("#4A4A4C")
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1 * context.resources.displayMetrics.density
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
                    color = Color.WHITE
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
                val textX = x + horizontalPadding + iconSize + iconTextGap
                canvas.drawText(text ?: "", start, end, textX, y.toFloat(), textPaint)
            }
        }
    }
    
    // ============================================================================
    // APP DETECTION LOGIC
    // ============================================================================
    
    /**
     * Detect app names and intent names in text and show pills
     */
    fun detectAppsInText(text: String) {
        // PRIORITY CHECK: If we're in MCP app context, show tool pills immediately
        // Don't do any app detection at all
        if (callbacks?.isInMcpContext() == true) {
            android.util.Log.d("MCP", "In MCP context, showing tool pills for: ${callbacks?.getCurrentMcpContext()}")
            callbacks?.getCurrentMcpContext()?.let { showMcpToolSuggestions(it) }
            return
        }
        
        if (text.isEmpty()) {
            // Show recent apps when text input is cleared (only if browser is not visible)
            if (callbacks?.isBrowserVisible() != true) {
                showRecentApps()
            }
            return
        }
        
        // Don't show app suggestions when browser is visible
        if (callbacks?.isBrowserVisible() == true) {
            suggestionsScrollView.visibility = View.GONE
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
            suggestionsScrollView.visibility = View.GONE
        }
    }
    
    /**
     * Detect MCP apps in text by name or description keywords
     */
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
    
    /**
     * Extract relevant keywords from app name and description for matching
     */
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
    
    /**
     * Detect intent actions in text
     */
    private fun detectIntentInText(textLower: String): List<AppInfo> {
        val detectedApps = mutableListOf<AppInfo>()
        
        // Common intent action phrases
        val intentPhrases = mapOf(
            "open" to listOf("app", "application"),
            "launch" to listOf("app", "application"),
            "start" to listOf("app", "application"),
            "run" to listOf("app", "application"),
            "use" to listOf("app", "application"),
            "access" to listOf("app", "application")
        )
        
        for ((action, objects) in intentPhrases) {
            if (textLower.contains(action)) {
                for (obj in objects) {
                    if (textLower.contains(obj)) {
                        // Found an intent phrase, show recent apps
                        detectedApps.addAll(installedApps.take(5)) // Show top 5 recent apps
                        return detectedApps
                    }
                }
            }
        }
        
        return detectedApps
    }
    
    // ============================================================================
    // PILL CREATION METHODS
    // ============================================================================
    
    /**
     * Show combined suggestions for both installed apps and MCP apps
     */
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
        
        suggestionsScrollView.visibility = View.VISIBLE
    }
    
    /**
     * Create a pill for an MCP app
     */
    private fun createMcpAppPill(mcpApp: McpApp): LinearLayout {
        val pill = LinearLayout(context)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * context.resources.displayMetrics.density).toInt() // Same as regular app pills
        val verticalPaddingPx = (8 * context.resources.displayMetrics.density).toInt()
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        
        // Check if app is connected (using callbacks)
        val isConnected = callbacks?.isMcpAppConnected(mcpApp.id) == true
        
        // Use same background as regular app pills for consistency
        pill.background = context.getDrawable(R.drawable.pill_background_compact)
        
        // Create icon view (load from URL or use placeholder)
        val iconView = ImageView(context)
        val iconSizePx = (32 * context.resources.displayMetrics.density).toInt() // Same size as regular app pills
        iconView.layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        iconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        
        // Load icon from URL if available
        if (!mcpApp.icon.isNullOrEmpty()) {
            val imageLoader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
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
        val textView = TextView(context)
        val status = if (isConnected) " ✓" else ""
        textView.text = mcpApp.name + status
        textView.setTextColor(Color.WHITE)
        textView.textSize = 14f
        textView.typeface = Typeface.DEFAULT_BOLD
        val textPaddingPx = (6 * context.resources.displayMetrics.density).toInt() // Same gap as regular app pills
        textView.setPadding(textPaddingPx, 0, 0, 0)
        
        // Add views to pill
        pill.addView(iconView)
        pill.addView(textView)
        
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * context.resources.displayMetrics.density).toInt()
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        // Add ripple effect
        pill.foreground = context.getDrawable(android.R.drawable.list_selector_background)
        
        // Click to connect to MCP app
        pill.setOnClickListener {
            callbacks?.onMcpAppPillClicked(mcpApp)
        }
        
        return pill
    }
    
    /**
     * Create a pill for an app
     */
    private fun createAppPill(app: AppInfo, label: String = app.appName): LinearLayout {
        val pill = LinearLayout(context)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * context.resources.displayMetrics.density).toInt() // 10dp horizontal padding
        val verticalPaddingPx = (8 * context.resources.displayMetrics.density).toInt() // 8dp vertical padding
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        pill.background = context.getDrawable(R.drawable.pill_background_compact)
        
        // Create icon view
        val iconView = ImageView(context)
        iconView.setImageDrawable(app.icon)
        val iconSizePx = (32 * context.resources.displayMetrics.density).toInt() // 32dp icon (matches inline pill scale better)
        iconView.layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        iconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        
        // Create text view
        val textView = TextView(context)
        textView.text = label // Use provided label
        textView.setTextColor(Color.WHITE)
        textView.textSize = 14f
        textView.typeface = Typeface.DEFAULT_BOLD
        val textPaddingPx = (6 * context.resources.displayMetrics.density).toInt() // 6dp gap between icon and text
        textView.setPadding(textPaddingPx, 0, 0, 0)
        
        // Add views to pill
        pill.addView(iconView)
        pill.addView(textView)
        
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * context.resources.displayMetrics.density).toInt() // 16dp right margin
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        // Add ripple effect for better touch feedback
        pill.foreground = context.getDrawable(android.R.drawable.list_selector_background)
        
        pill.setOnClickListener {
            callbacks?.onAppPillClicked(app)
        }
        
        return pill
    }
    
    /**
     * Create a pill for an intent capability
     */
    private fun createIntentPill(app: AppInfo, capability: IntentCapability): LinearLayout {
        val pill = LinearLayout(context)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * context.resources.displayMetrics.density).toInt() // 10dp horizontal padding
        val verticalPaddingPx = (8 * context.resources.displayMetrics.density).toInt() // 8dp vertical padding
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        pill.background = context.getDrawable(R.drawable.pill_background_compact)
        
        // Create icon view using the app icon (not generic action icon)
        val iconView = ImageView(context)
        iconView.setImageDrawable(app.icon) // Use app icon instead of capability.icon
        val iconSizePx = (32 * context.resources.displayMetrics.density).toInt() // 32dp icon (same as app pills)
        iconView.layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        iconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        
        // Create text view for intent action name
        val textView = TextView(context)
        textView.text = capability.displayName
        textView.setTextColor(Color.WHITE)
        textView.textSize = 14f
        textView.typeface = Typeface.DEFAULT_BOLD
        val textPaddingPx = (6 * context.resources.displayMetrics.density).toInt() // 6dp gap between icon and text
        textView.setPadding(textPaddingPx, 0, 0, 0)
        
        // Add views to pill
        pill.addView(iconView)
        pill.addView(textView)
        
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * context.resources.displayMetrics.density).toInt() // 16dp right margin
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        // Add ripple effect for better touch feedback
        pill.foreground = context.getDrawable(android.R.drawable.list_selector_background)
        
        pill.setOnClickListener {
            callbacks?.onIntentPillClicked(app, capability)
        }
        
        return pill
    }
    
    // ============================================================================
    // MCP TOOL SUGGESTIONS
    // ============================================================================
    
    /**
     * Show MCP tool suggestions for the current app context
     */
    fun showMcpToolSuggestions(appId: String) {
        suggestionsLayout.removeAllViews()
        
        // Get the connected MCP app (using callbacks)
        val mcpApp = callbacks?.getConnectedMcpApp(appId)
        if (mcpApp == null || mcpApp.tools.isEmpty()) {
            suggestionsScrollView.visibility = View.GONE
            return
        }
        
        android.util.Log.d("MCP", "Showing tool suggestions for ${mcpApp.name}: ${mcpApp.tools.size} tools")
        
        // If we're collecting parameters, show parameter pills first (using callbacks)
        val paramState = callbacks?.getMcpParameterCollectionState()
        if (paramState != null) {
            android.util.Log.d("MCP", "Parameter collection active, showing parameter pills")
            
            // Get tool's input schema
            val inputSchema = (paramState as? Any)?.let { 
                // This would need to be properly typed based on the actual ParameterCollectionState
                // For now, we'll skip parameter pills and just show tools
                null
            }
            
            // Skip parameter pills for now - would need proper typing
        }
        
        // Show all tools as pills
        android.util.Log.e("PillsCoordinator", "Showing ${mcpApp.tools.size} MCP tools for app: $appId")
        for (tool in mcpApp.tools) {
            android.util.Log.e("PillsCoordinator", "Creating pill for tool: ${tool.name}")
            val pill = createMcpToolPill(appId, tool)
            suggestionsLayout.addView(pill)
        }
        
        suggestionsScrollView.visibility = View.VISIBLE
    }
    
    /**
     * Create a pill for an MCP tool
     */
    private fun createMcpToolPill(appId: String, tool: McpTool): LinearLayout {
        val pill = LinearLayout(context)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        val horizontalPaddingPx = (10 * context.resources.displayMetrics.density).toInt()
        val verticalPaddingPx = (8 * context.resources.displayMetrics.density).toInt()
        pill.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
        pill.background = context.getDrawable(R.drawable.pill_background_compact)
        
        // Get the MCP app to use its icon (using callbacks)
        val mcpApp = callbacks?.getConnectedMcpApp(appId)
        
        // Create icon view
        val iconView = ImageView(context)
        val iconSizePx = (32 * context.resources.displayMetrics.density).toInt()
        iconView.layoutParams = LinearLayout.LayoutParams(iconSizePx, iconSizePx)
        iconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        
        // Load app icon for the tool
        if (mcpApp != null && !mcpApp.icon.isNullOrEmpty()) {
            val imageLoader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
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
        val textView = TextView(context)
        textView.text = tool.title ?: tool.name
        textView.setTextColor(Color.WHITE)
        textView.textSize = 14f
        textView.typeface = Typeface.DEFAULT_BOLD
        val textPaddingPx = (6 * context.resources.displayMetrics.density).toInt()
        textView.setPadding(textPaddingPx, 0, 0, 0)
        
        pill.addView(iconView)
        pill.addView(textView)
        
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val rightMarginPx = (16 * context.resources.displayMetrics.density).toInt()
        layoutParams.setMargins(0, 0, rightMarginPx, 0)
        pill.layoutParams = layoutParams
        
        // Add ripple effect
        pill.foreground = context.getDrawable(android.R.drawable.list_selector_background)
        
        // Click to invoke this tool
        pill.setOnClickListener {
            android.util.Log.e("PillsCoordinator", ">>> MCP TOOL PILL CLICKED: ${tool.name} from app: $appId")
            callbacks?.onMcpToolPillClicked(appId, tool)
        }
        
        return pill
    }
    
    // ============================================================================
    // RECENT APPS
    // ============================================================================
    
    /**
     * Show recent apps as pills
     */
    fun showRecentApps() {
        suggestionsLayout.removeAllViews()
        
        // Get recently used apps from UsageStatsManager via callback
        val recentApps = callbacks?.getRecentlyUsedApps(10) ?: emptyList()
        
        if (recentApps.isEmpty()) {
            // Fallback to first 5 installed apps if no recent apps available
            android.util.Log.d("PillsCoordinator", "No recent apps available, using first 5 installed apps")
            showFallbackApps()
            return
        }
        
        if (recentApps.size == 1) {
            // Single app - show with all intents
            val app = recentApps[0]
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
            for (app in recentApps) {
                val pill = createAppPill(app) // Show app name
                suggestionsLayout.addView(pill)
            }
        }

        suggestionsScrollView.visibility = View.VISIBLE
    }
    
    /**
     * Show fallback apps (first few installed apps) when recent apps are not available
     */
    private fun showFallbackApps() {
        val fallbackApps = installedApps.take(5)
        
        if (fallbackApps.isEmpty()) {
            suggestionsScrollView.visibility = View.GONE
            return
        }
        
        for (app in fallbackApps) {
            val pill = createAppPill(app)
            suggestionsLayout.addView(pill)
        }
        
        suggestionsScrollView.visibility = View.VISIBLE
    }
    
    // ============================================================================
    // UTILITY METHODS
    // ============================================================================
    
    /**
     * Check if a word is a complete word match in text
     */
    private fun isCompleteWordMatch(text: String, word: String): Boolean {
        val index = text.indexOf(word)
        if (index == -1) return false
        return isCompleteWordMatchAtPosition(text, word, index)
    }
    
    /**
     * Check if a word is a complete word match at a specific position
     */
    private fun isCompleteWordMatchAtPosition(text: String, word: String, position: Int): Boolean {
        // Check character before the word
        if (position > 0) {
            val charBefore = text[position - 1]
            if (charBefore.isLetterOrDigit()) return false
        }
        
        // Check character after the word
        val endPosition = position + word.length
        if (endPosition < text.length) {
            val charAfter = text[endPosition]
            if (charAfter.isLetterOrDigit()) return false
        }
        
        return true
    }
    
    /**
     * Clear all suggestions
     */
    fun clearSuggestions() {
        suggestionsLayout.removeAllViews()
        suggestionsScrollView.visibility = View.GONE
    }
}
