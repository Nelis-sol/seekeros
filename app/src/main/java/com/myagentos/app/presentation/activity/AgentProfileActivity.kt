package com.myagentos.app.presentation.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.myagentos.app.R
import com.myagentos.app.presentation.adapter.AgentProfilePagerAdapter
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

/**
 * Full-screen activity for displaying agent profile details with tabs
 */
class AgentProfileActivity : AppCompatActivity() {
    
    private lateinit var backButton: Button
    private lateinit var agentIconLarge: ImageView
    private lateinit var agentName: TextView
    private lateinit var agentCreator: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: AgentProfilePagerAdapter
    
    // Wallet connection status
    private var isWalletConnected: Boolean = false
    
    // Mobile Wallet Adapter
    private lateinit var activityResultSender: ActivityResultSender
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_profile)
        
        // Initialize views
        backButton = findViewById(R.id.backButton)
        agentIconLarge = findViewById(R.id.agentIconLarge)
        agentName = findViewById(R.id.agentName)
        agentCreator = findViewById(R.id.agentCreator)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        
        // Get agent data from intent
        val name = intent.getStringExtra("agent_name") ?: ""
        val creator = intent.getStringExtra("agent_creator") ?: ""
        val iconResId = intent.getIntExtra("agent_icon_res_id", 0)
        
        // Populate views
        agentName.text = name
        agentCreator.text = "by $creator"
        
        // Set icon if available
        if (iconResId != 0) {
            agentIconLarge.setBackgroundResource(iconResId)
        }
        
        // Initialize ActivityResultSender in onCreate (required for MWA)
        activityResultSender = ActivityResultSender(this)
        
        // Setup tabs
        setupTabs()
        
        // Back button
        backButton.setOnClickListener {
            finish()
        }
    }
    
    /**
     * Setup tabs for the agent profile
     */
    private fun setupTabs() {
        // Create adapter
        pagerAdapter = AgentProfilePagerAdapter(this)
        viewPager.adapter = pagerAdapter
        
        // Setup tab layout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "About"
                1 -> tab.text = "Wallet"
                2 -> tab.text = "Email"
            }
        }.attach()
    }
    
    /**
     * Switch to Wallet tab
     */
    fun switchToWalletTab() {
        viewPager.currentItem = 1
    }
    
    /**
     * Switch to Email tab
     */
    fun switchToEmailTab() {
        viewPager.currentItem = 2
    }
    
    /**
     * Check if wallet is connected
     */
    fun isWalletConnected(): Boolean {
        return isWalletConnected
    }
    
    /**
     * Set wallet connection status
     */
    fun setWalletConnected(connected: Boolean) {
        isWalletConnected = connected
    }
    
    /**
     * Get ActivityResultSender for fragments
     */
    fun getActivityResultSender(): ActivityResultSender {
        return activityResultSender
    }
}
