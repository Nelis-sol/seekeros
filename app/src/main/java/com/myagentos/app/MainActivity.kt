package com.myagentos.app

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var modelManager: ModelManager
    private lateinit var externalAIService: ExternalAIService
    private lateinit var appManager: AppManager
    private var selectedModel: ModelType? = null
    private var installedApps: List<AppInfo> = emptyList()
    private var isUpdatingPills = false // Flag to prevent infinite loops

    companion object {
        private var savedMessages: MutableList<ChatMessage>? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Always use dark theme
        setTheme(R.style.Theme_AgentOS_Dark)
        
        setContentView(R.layout.activity_main)

        // Force dark background
        val rootView = findViewById<android.view.View>(android.R.id.content)
        rootView.setBackgroundColor(android.graphics.Color.BLACK)

        modelManager = ModelManager(this)
        externalAIService = ExternalAIService()
        appManager = AppManager(this)

        setupUI()
        checkFirstLaunch()
    }
    
    override fun onResume() {
        super.onResume()
        // Ensure proper keyboard handling - this is critical for input field visibility
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        
        // Ensure input field is focused and keyboard is shown
        messageInput.post {
            messageInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(messageInput, InputMethodManager.SHOW_FORCED)
        }
    }

    private fun setupUI() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        suggestionsLayout = findViewById(R.id.suggestionsLayout)
        suggestionsScrollView = findViewById(R.id.suggestionsScrollView)

        // Setup Chat
        val messages = savedMessages ?: mutableListOf()
        chatAdapter = ChatAdapter(messages, true) // true = dark mode
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter

        // Don't show welcome message - let user start typing immediately
        // if (messages.isEmpty()) {
        //     updateWelcomeMessage()
        // }

        // Setup send button
        sendButton.setOnClickListener {
            sendMessage()
        }
        
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
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
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

        // Add user message to chat
        chatAdapter.addMessage(ChatMessage(message, isUser = true))
        messageInput.text.clear()
        
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val aiResponse = when (selectedModel) {
                    ModelType.EXTERNAL_CHATGPT, ModelType.EXTERNAL_GROK -> {
                        externalAIService.generateResponse(userMessage, selectedModel!!)
                    }
                    null -> "Please select an AI model first."
                }
                
                withContext(Dispatchers.Main) {
                    chatAdapter.addMessage(ChatMessage(aiResponse, isUser = false))
                    chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                }
            } catch (e: Exception) {
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
                detectAppsInText(text)
                // Apply inline pills with safe flag
                applyInlinePills(text)
            }
        })
    }
    
    // Apply inline pills to the text input
    private fun applyInlinePills(text: String) {
        if (text.isEmpty() || installedApps.isEmpty() || isUpdatingPills) return
        
        val spannableString = android.text.SpannableString(text)
        val textLower = text.lowercase()
        var hasPills = false
        
        // Find all app names in the text
        for (app in installedApps) {
            val appNameLower = app.appName.lowercase()
            if (textLower.contains(appNameLower) && appNameLower.length > 2) {
                // Find all occurrences of this app name
                var startIndex = 0
                while (true) {
                    val index = textLower.indexOf(appNameLower, startIndex)
                    if (index == -1) break
                    
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
            suggestionsScrollView.visibility = android.view.View.GONE
            return
        }

        val detectedApps = mutableListOf<AppInfo>()
        val textLower = text.lowercase()

        // First, detect app names
        for (app in installedApps) {
            val appNameLower = app.appName.lowercase()
            if (textLower.contains(appNameLower) && appNameLower.length > 2) {
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
    
    // Detect intent actions in text and return apps that support them
    private fun detectIntentInText(textLower: String): List<AppInfo> {
        val appsForIntent = mutableListOf<AppInfo>()
        
        // Map common phrases to intent actions
        val intentPhrases = mapOf(
            "send message" to android.content.Intent.ACTION_SEND,
            "share" to android.content.Intent.ACTION_SEND,
            "call" to android.content.Intent.ACTION_DIAL,
            "dial" to android.content.Intent.ACTION_DIAL,
            "share photo" to android.content.Intent.ACTION_SEND,
            "share image" to android.content.Intent.ACTION_SEND,
            "view" to android.content.Intent.ACTION_VIEW,
            "open" to android.content.Intent.ACTION_VIEW
        )
        
        // Check if text contains any intent phrase
        for ((phrase, action) in intentPhrases) {
            if (textLower.contains(phrase)) {
                // Find all apps that support this intent
                val packageManager = packageManager
                val intent = when (action) {
                    android.content.Intent.ACTION_SEND -> {
                        if (phrase.contains("photo") || phrase.contains("image")) {
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
                
                // If we found a match, don't check other phrases
                if (appsForIntent.isNotEmpty()) {
                    break
                }
            }
        }
        
        return appsForIntent
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
        val paddingPx = (12 * resources.displayMetrics.density).toInt() // 12dp padding
        pill.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        pill.background = getDrawable(R.drawable.pill_background)
        
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
        layoutParams.setMargins(0, 0, 12, 0) // More margin between pills
        pill.layoutParams = layoutParams
        
        // Add ripple effect for better touch feedback
        pill.foreground = getDrawable(android.R.drawable.list_selector_background)
        
        pill.setOnClickListener {
            // Launch the app when pill is clicked
            val success = appManager.launchApp(app.packageName)
            if (!success) {
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
        val paddingPx = (12 * resources.displayMetrics.density).toInt() // 12dp padding (same as app pills)
        pill.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        pill.background = getDrawable(R.drawable.pill_background)
        
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
        layoutParams.setMargins(0, 0, 12, 0) // Same margin as app pills
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
}




