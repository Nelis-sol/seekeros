package com.myagentos.app.presentation.activity

import com.myagentos.app.R

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.ImageLoader
import coil.request.ImageRequest

class McpAppDetailsActivity : AppCompatActivity() {
    
    private lateinit var bannerImage: ImageView
    private lateinit var appIcon: ImageView
    private lateinit var appName: TextView
    private lateinit var appTagline: TextView
    private lateinit var toolsCount: TextView
    private lateinit var appDescription: TextView
    private lateinit var moreButton: TextView
    private lateinit var toolsSection: LinearLayout
    private lateinit var toolsListContainer: LinearLayout
    private lateinit var connectButton: Button
    private lateinit var saveButton: Button
    private lateinit var developerName: TextView
    private lateinit var serverUrl: TextView
    private var isDescriptionExpanded = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mcp_app_details)
        
        // Initialize views
        bannerImage = findViewById(R.id.bannerImage)
        appIcon = findViewById(R.id.appIcon)
        appName = findViewById(R.id.appName)
        appTagline = findViewById(R.id.appTagline)
        toolsCount = findViewById(R.id.toolsCount)
        appDescription = findViewById(R.id.appDescription)
        moreButton = findViewById(R.id.moreButton)
        toolsSection = findViewById(R.id.toolsSection)
        toolsListContainer = findViewById(R.id.toolsListContainer)
        connectButton = findViewById(R.id.connectButton)
        saveButton = findViewById(R.id.saveButton)
        developerName = findViewById(R.id.developerName)
        serverUrl = findViewById(R.id.serverUrl)
        
        // Get app data from intent
        val appId = intent.getStringExtra("app_id") ?: ""
        val appNameText = intent.getStringExtra("app_name") ?: ""
        val appDescriptionText = intent.getStringExtra("app_description") ?: ""
        val appIconUrl = intent.getStringExtra("app_icon") ?: ""
        val appServerUrl = intent.getStringExtra("app_server_url") ?: ""
        val isConnected = intent.getBooleanExtra("is_connected", false)
        val toolsCountValue = intent.getIntExtra("tools_count", 0)
        
        // Populate views
        appName.text = appNameText
        appTagline.text = "MCP Mini App"
        appDescription.text = appDescriptionText
        toolsCount.text = toolsCountValue.toString()
        
        // Extract developer name from description or use default
        developerName.text = "OpenAI"
        
        // Display server URL (extract domain)
        try {
            val domain = appServerUrl.substringAfter("://").substringBefore("/")
            serverUrl.text = domain
        } catch (e: Exception) {
            serverUrl.text = appServerUrl
        }
        
        // Update connect button text based on connection status
        if (isConnected) {
            connectButton.text = "CONNECTED"
            connectButton.isEnabled = false
            connectButton.alpha = 0.6f
        }
        
        // Load banner image (same as app icon for now)
        if (appIconUrl.isNotEmpty()) {
            val imageLoader = ImageLoader(this)
            val bannerRequest = ImageRequest.Builder(this)
                .data(appIconUrl)
                .size(coil.size.Size.ORIGINAL)
                .allowHardware(true)
                .target(
                    onSuccess = { result ->
                        bannerImage.setImageDrawable(result)
                    }
                )
                .placeholder(R.drawable.stock_photo_banner)
                .error(R.drawable.stock_photo_banner)
                .build()
            imageLoader.enqueue(bannerRequest)
            
            // Also load app icon
            val iconRequest = ImageRequest.Builder(this)
                .data(appIconUrl)
                .size(coil.size.Size.ORIGINAL)
                .allowHardware(true)
                .target(
                    onSuccess = { result ->
                        appIcon.setImageDrawable(result)
                    }
                )
                .placeholder(R.drawable.ic_apps)
                .error(R.drawable.ic_apps)
                .build()
            imageLoader.enqueue(iconRequest)
        } else {
            bannerImage.setImageResource(R.drawable.stock_photo_banner)
            appIcon.setImageResource(R.drawable.ic_apps)
        }
        
        // Load and display tools if connected
        if (isConnected && toolsCountValue > 0) {
            val toolNames = intent.getStringArrayListExtra("tool_names") ?: emptyList()
            val toolTitles = intent.getStringArrayListExtra("tool_titles") ?: emptyList()
            val toolDescriptions = intent.getStringArrayListExtra("tool_descriptions") ?: emptyList()
            loadTools(toolNames, toolTitles, toolDescriptions)
        }
        
        // Set up "more" button to expand description
        moreButton.setOnClickListener {
            isDescriptionExpanded = !isDescriptionExpanded
            if (isDescriptionExpanded) {
                appDescription.maxLines = Int.MAX_VALUE
                moreButton.text = "less"
            } else {
                appDescription.maxLines = 4
                moreButton.text = "more"
            }
        }
        
        // Set up connect button
        connectButton.setOnClickListener {
            // Connect to the app and go back
            val resultIntent = Intent().apply {
                putExtra("action", "connect")
                putExtra("app_id", appId)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
        
        // Set up save button
        saveButton.setOnClickListener {
            // TODO: Implement save to favorites functionality
            android.widget.Toast.makeText(this, "App saved to favorites", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // Set up back button
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
    }
    
    private fun loadTools(toolNames: List<String>, toolTitles: List<String>, toolDescriptions: List<String>) {
        if (toolNames.isEmpty()) return
        
        toolsSection.visibility = View.VISIBLE
        toolsListContainer.removeAllViews()
        
        // Create a tool item for each tool
        toolNames.indices.forEach { i ->
            val toolTitle = toolTitles.getOrElse(i) { toolNames[i] }
            val toolDesc = toolDescriptions.getOrElse(i) { "No description available" }
            
            // Create custom tool item view
            val toolCard = androidx.cardview.widget.CardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (12 * resources.displayMetrics.density).toInt()
                }
                radius = 12 * resources.displayMetrics.density
                setCardBackgroundColor(android.graphics.Color.parseColor("#1C1C1E"))
                cardElevation = 0f
            }
            
            val toolContent = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                val padding = (16 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
            
            val toolTitleView = TextView(this).apply {
                text = toolTitle
                setTextColor(android.graphics.Color.WHITE)
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            val toolDescView = TextView(this).apply {
                text = toolDesc
                setTextColor(android.graphics.Color.parseColor("#CCCCCC"))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8 * resources.displayMetrics.density).toInt()
                }
            }
            
            toolContent.addView(toolTitleView)
            toolContent.addView(toolDescView)
            toolCard.addView(toolContent)
            toolsListContainer.addView(toolCard)
        }
    }
}
