package com.myagentos.app.presentation.manager

import android.content.Context
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.myagentos.app.domain.model.AppInfo
import com.myagentos.app.data.manager.AppManager
import com.myagentos.app.data.model.McpApp
import com.myagentos.app.data.model.McpTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * PillsManager - Manages app pills, detection, and suggestions
 * 
 * Responsibilities:
 * - App detection in user input (installed apps + MCP apps)
 * - Pills display (recent apps, detected apps, MCP tools)
 * - Inline pill rendering in EditText
 * - App suggestions UI
 * 
 * Extracted from MainActivity to reduce complexity
 */
class PillsManager(
    private val context: Context,
    private val messageInput: EditText,
    private val suggestionsScrollView: ScrollView,
    private val suggestionsLayout: LinearLayout,
    private val appManager: AppManager
) {
    
    // State variables
    private var installedApps: List<AppInfo> = emptyList()
    private var isUpdatingPills = false
    private var isBrowserVisible = false
    private var currentMcpAppContext: String? = null
    
    // Callbacks
    private var onAppSelected: ((AppInfo) -> Unit)? = null
    private var onMcpAppConnect: ((String) -> Unit)? = null
    private var onMcpToolInvoke: ((String, McpTool) -> Unit)? = null
    private var onMcpAppDetails: ((String) -> Unit)? = null
    private var connectedAppsProvider: (() -> Map<String, McpApp>)? = null
    private var pillSpanCreator: ((AppInfo) -> Any)? = null
    private var mcpAppPillCreator: ((McpApp) -> LinearLayout)? = null
    private var mcpToolPillCreator: ((String, McpTool) -> LinearLayout)? = null
    
    /**
     * Initialize pills manager and load installed apps
     */
    fun setup() {
        loadInstalledApps()
        setupAppDetection()
        android.util.Log.d("PillsManager", "Pills manager initialized")
    }
    
    /**
     * Load installed apps for detection
     */
    private fun loadInstalledApps() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                installedApps = appManager.getInstalledApps()
                android.util.Log.d("PillsManager", "Loaded ${installedApps.size} apps")
            } catch (e: Exception) {
                android.util.Log.e("PillsManager", "Error loading apps", e)
            }
        }
    }
    
    /**
     * Setup app detection on text change
     */
    private fun setupAppDetection() {
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
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
    
    /**
     * Check if we should trigger app detection based on word boundaries
     */
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
    
    /**
     * Detect app names in text and show pills
     */
    private fun detectAppsInText(text: String) {
        // PRIORITY CHECK: If we're in MCP app context, show tool pills immediately
        // Don't do any app detection at all
        if (currentMcpAppContext != null) {
            android.util.Log.d("PillsManager", "In MCP context, showing tool pills for: $currentMcpAppContext")
            // showMcpToolSuggestions(currentMcpAppContext!!)
            // This will be handled via callback
            return
        }
        
        if (text.isEmpty()) {
            // Show recent apps when text input is cleared (only if browser is not visible)
            if (!isBrowserVisible) {
                // showRecentApps() - handled via callback
            }
            return
        }
        
        // Don't show app suggestions when browser is visible
        if (isBrowserVisible) {
            suggestionsScrollView.visibility = View.GONE
            return
        }
        
        val detectedApps = mutableListOf<AppInfo>()
        val textLower = text.lowercase()
        
        // Detect installed app names - only match complete words
        for (app in installedApps) {
            val appNameLower = app.appName.lowercase()
            if (appNameLower.length > 2 && isCompleteWordMatch(textLower, appNameLower)) {
                detectedApps.add(app)
            }
        }
        
        // Show suggestions if we detected apps
        if (detectedApps.isNotEmpty()) {
            showAppSuggestions(detectedApps)
        } else {
            suggestionsScrollView.visibility = View.GONE
        }
    }
    
    /**
     * Apply inline pills to the text input
     */
    private fun applyInlinePills(text: String) {
        if (text.isEmpty() || isUpdatingPills || isBrowserVisible) return
        
        val spannableString = SpannableString(text)
        val textLower = text.lowercase()
        
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
                    
                    // Create clickable span for the app name (using callback)
                    // This would require more complex implementation
                    
                    startIndex = endIndex
                }
            }
        }
        
        // Note: Full inline pill rendering requires more complex span handling
        // For now, this is a placeholder that can be expanded
    }
    
    /**
     * Show app suggestions pills
     */
    private fun showAppSuggestions(apps: List<AppInfo>) {
        suggestionsLayout.removeAllViews()
        
        for (app in apps) {
            // Create pill view (via callback)
            // This allows MainActivity to provide custom pill rendering
        }
        
        suggestionsScrollView.visibility = View.VISIBLE
    }
    
    /**
     * Check if text contains complete word match
     */
    private fun isCompleteWordMatch(text: String, word: String): Boolean {
        val pattern = Regex("""(^|[\s\p{Punct}])${Regex.escape(word)}($|[\s\p{Punct}])""")
        return pattern.find(text) != null
    }
    
    /**
     * Check if word is at specific position in text
     */
    private fun isCompleteWordMatchAtPosition(text: String, word: String, position: Int): Boolean {
        // Check character before
        val charBefore = if (position > 0) text[position - 1] else ' '
        val isWordBoundaryBefore = charBefore.isWhitespace() || charBefore in ".,!?;:-"
        
        // Check character after
        val endPosition = position + word.length
        val charAfter = if (endPosition < text.length) text[endPosition] else ' '
        val isWordBoundaryAfter = charAfter.isWhitespace() || charAfter in ".,!?;:-"
        
        return isWordBoundaryBefore && isWordBoundaryAfter
    }
    
    /**
     * Set browser visibility state
     */
    fun setBrowserVisible(visible: Boolean) {
        isBrowserVisible = visible
        if (visible) {
            suggestionsScrollView.visibility = View.GONE
        }
    }
    
    /**
     * Set current MCP app context
     */
    fun setMcpAppContext(appId: String?) {
        currentMcpAppContext = appId
    }
    
    /**
     * Get current MCP app context
     */
    fun getMcpAppContext(): String? = currentMcpAppContext
    
    /**
     * Show recent apps (called when no text in input)
     */
    fun showRecentApps() {
        android.util.Log.d("PillsManager", "showRecentApps() called")
        
        // PRIORITY CHECK: If we're in MCP app context, show tool pills instead
        if (currentMcpAppContext != null) {
            android.util.Log.d("PillsManager", "In MCP context, should show tool pills for: $currentMcpAppContext")
            // Handled via callback to MainActivity
            return
        }
        
        // This will be implemented via callback to MainActivity
        // as it requires access to UsageStatsManager and permissions
    }
    
    /**
     * Hide all suggestions
     */
    fun hideSuggestions() {
        suggestionsScrollView.visibility = View.GONE
    }
    
    /**
     * Clear pills state
     */
    fun clearState() {
        currentMcpAppContext = null
        suggestionsLayout.removeAllViews()
        suggestionsScrollView.visibility = View.GONE
    }
    
    /**
     * Set callbacks
     */
    fun setCallbacks(
        onAppSelected: (AppInfo) -> Unit,
        onMcpAppConnect: (String) -> Unit,
        onMcpToolInvoke: (String, McpTool) -> Unit,
        onMcpAppDetails: (String) -> Unit,
        connectedAppsProvider: () -> Map<String, McpApp>,
        pillSpanCreator: (AppInfo) -> Any,
        mcpAppPillCreator: (McpApp) -> LinearLayout,
        mcpToolPillCreator: (String, McpTool) -> LinearLayout
    ) {
        this.onAppSelected = onAppSelected
        this.onMcpAppConnect = onMcpAppConnect
        this.onMcpToolInvoke = onMcpToolInvoke
        this.onMcpAppDetails = onMcpAppDetails
        this.connectedAppsProvider = connectedAppsProvider
        this.pillSpanCreator = pillSpanCreator
        this.mcpAppPillCreator = mcpAppPillCreator
        this.mcpToolPillCreator = mcpToolPillCreator
    }
    
    /**
     * Reload installed apps
     */
    fun reloadApps() {
        loadInstalledApps()
    }
    
    /**
     * Get installed apps list
     */
    fun getInstalledApps(): List<AppInfo> = installedApps
}

