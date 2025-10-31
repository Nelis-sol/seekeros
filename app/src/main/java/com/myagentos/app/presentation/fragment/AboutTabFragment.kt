package com.myagentos.app.presentation.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.myagentos.app.R
import com.myagentos.app.data.storage.WalletStorage
import com.myagentos.app.util.Base58Encoder
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.launch

/**
 * About tab fragment for agent profile
 */
class AboutTabFragment : Fragment() {
    
    private lateinit var agentDescription: TextView
    private lateinit var agentPersonalityPrompt: TextView
    private lateinit var agentSystemPrompt: TextView
    private lateinit var addWalletButton: Button
    private lateinit var generateEmailButton: Button
    private lateinit var copyWalletButton: Button
    private lateinit var disconnectWalletButton: Button
    private lateinit var viewInboxButton: Button
    private lateinit var walletAddressText: TextView
    private lateinit var emailAddressText: TextView
    private lateinit var emailSection: LinearLayout
    private lateinit var walletConnectedButtons: LinearLayout
    
    // Mobile Wallet Adapter
    private lateinit var walletAdapter: MobileWalletAdapter
    private var walletPublicKey: String? = null
    
    // Agent data
    private var agentName: String = ""
    private var agentCreator: String = ""
    private var agentDescriptionText: String = ""
    private var agentPersonalityPromptText: String = ""
    private var agentSystemPromptText: String = ""
    private var agentIconResId: Int = 0
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.tab_about, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        agentDescription = view.findViewById(R.id.agentDescription)
        agentPersonalityPrompt = view.findViewById(R.id.agentPersonalityPrompt)
        agentSystemPrompt = view.findViewById(R.id.agentSystemPrompt)
        addWalletButton = view.findViewById(R.id.addWalletButton)
        generateEmailButton = view.findViewById(R.id.generateEmailButton)
        copyWalletButton = view.findViewById(R.id.copyWalletButton)
        disconnectWalletButton = view.findViewById(R.id.disconnectWalletButton)
        viewInboxButton = view.findViewById(R.id.viewInboxButton)
        walletAddressText = view.findViewById(R.id.walletAddressText)
        emailAddressText = view.findViewById(R.id.emailAddressText)
        emailSection = view.findViewById(R.id.emailSection)
        walletConnectedButtons = view.findViewById(R.id.walletConnectedButtons)
        
        // Get agent data from parent activity
        val activity = requireActivity() as? com.myagentos.app.presentation.activity.AgentProfileActivity
        activity?.let {
            agentName = it.intent.getStringExtra("agent_name") ?: ""
            agentCreator = it.intent.getStringExtra("agent_creator") ?: ""
            agentDescriptionText = it.intent.getStringExtra("agent_description") ?: ""
            agentPersonalityPromptText = it.intent.getStringExtra("agent_personality_prompt") ?: ""
            agentSystemPromptText = it.intent.getStringExtra("agent_system_prompt") ?: ""
            agentIconResId = it.intent.getIntExtra("agent_icon_res_id", 0)
        }
        
        // Populate views
        agentDescription.text = agentDescriptionText
        agentPersonalityPrompt.text = agentPersonalityPromptText
        agentSystemPrompt.text = agentSystemPromptText
        
        // Initialize Mobile Wallet Adapter
        setupWalletAdapter()
        
        // Set up click listeners
        addWalletButton.setOnClickListener {
            connectToWallet()
        }
        
        generateEmailButton.setOnClickListener {
            generateEmail()
        }
        
        copyWalletButton.setOnClickListener {
            if (walletPublicKey != null) {
                copyToClipboard("Wallet Address", walletPublicKey!!)
            }
        }
        
        disconnectWalletButton.setOnClickListener {
            disconnectWallet()
        }
        
        viewInboxButton.setOnClickListener {
            // Navigate to Email tab
            (requireActivity() as? com.myagentos.app.presentation.activity.AgentProfileActivity)?.switchToEmailTab()
        }
        
        // Check for existing wallet connection
        checkExistingWalletConnection()
    }
    
    /**
     * Check for existing wallet connection on app start
     */
    private fun checkExistingWalletConnection() {
        if (WalletStorage.isWalletConnected(requireContext())) {
            val savedAddress = WalletStorage.getWalletAddress(requireContext())
            if (savedAddress != null) {
                walletPublicKey = savedAddress
                updateUIForConnectedWallet(savedAddress)
            }
        }
    }
    
    /**
     * Update UI when wallet is already connected
     */
    private fun updateUIForConnectedWallet(address: String) {
        // Update UI to show wallet is connected
        walletAddressText.text = address
        walletAddressText.visibility = View.VISIBLE
        
        // Set up copy functionality for wallet address
        walletAddressText.setOnClickListener {
            copyToClipboard("Wallet Address", address)
        }
        
        // Hide add wallet button, show connected buttons
        addWalletButton.visibility = View.GONE
        walletConnectedButtons.visibility = View.VISIBLE
        
        // Enable email section
        emailSection.alpha = 1.0f
        generateEmailButton.isEnabled = true
        
        // Update parent activity wallet status
        (requireActivity() as? com.myagentos.app.presentation.activity.AgentProfileActivity)?.setWalletConnected(true)
    }
    
    /**
     * Disconnect wallet
     */
    private fun disconnectWallet() {
        // Clear stored wallet state
        WalletStorage.clearWalletState(requireContext())
        
        // Reset UI
        walletAddressText.visibility = View.GONE
        addWalletButton.visibility = View.VISIBLE
        walletConnectedButtons.visibility = View.GONE
        
        // Disable email section
        emailSection.alpha = 0.5f
        generateEmailButton.isEnabled = false
        
        // Update parent activity
        (requireActivity() as? com.myagentos.app.presentation.activity.AgentProfileActivity)?.setWalletConnected(false)
        
        // Clear wallet data
        walletPublicKey = null
        
        Toast.makeText(requireContext(), "Wallet disconnected", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Setup Mobile Wallet Adapter client
     */
    private fun setupWalletAdapter() {
        val solanaUri = Uri.parse("https://agentos.ai")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "AgentOS"
        
        walletAdapter = MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = solanaUri,
                iconUri = iconUri,
                identityName = identityName
            )
        )
    }
    
    /**
     * Connect to Solana wallet and retrieve public key
     */
    private fun connectToWallet() {
        lifecycleScope.launch {
            try {
                val activity = requireActivity() as? com.myagentos.app.presentation.activity.AgentProfileActivity
                val activityResultSender = activity?.getActivityResultSender()
                if (activityResultSender == null) {
                    Toast.makeText(requireContext(), "Activity not ready", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val result = walletAdapter.connect(activityResultSender)
                
                when (result) {
                    is TransactionResult.Success -> {
                        val authResult = result.authResult
                        val publicKey = authResult.accounts.firstOrNull()?.publicKey
                        
                        if (publicKey != null) {
                            walletPublicKey = publicKey.contentToString()
                            val publicKeyString = Base58Encoder.encodeToString(publicKey)
                            
                            // Save wallet state
                            WalletStorage.saveWalletState(requireContext(), publicKeyString, true)
                            
                            // Update UI to show wallet is connected
                            walletAddressText.text = publicKeyString
                            walletAddressText.visibility = View.VISIBLE
                            
                            // Set up copy functionality for wallet address
                            walletAddressText.setOnClickListener {
                                copyToClipboard("Wallet Address", publicKeyString)
                            }
                            
                            // Hide add wallet button, show connected buttons
                            addWalletButton.visibility = View.GONE
                            walletConnectedButtons.visibility = View.VISIBLE
                            
                            // Enable email section
                            emailSection.alpha = 1.0f
                            generateEmailButton.isEnabled = true
                            
                            // Update parent activity wallet status
                            (requireActivity() as? com.myagentos.app.presentation.activity.AgentProfileActivity)?.setWalletConnected(true)
                            
                            Toast.makeText(requireContext(), "Wallet connected successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "No wallet account found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is TransactionResult.NoWalletFound -> {
                        Toast.makeText(requireContext(), "No MWA compatible wallet app found on device", Toast.LENGTH_SHORT).show()
                    }
                    is TransactionResult.Failure -> {
                        Toast.makeText(requireContext(), "Error connecting to wallet: ${result.e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Generate email address for the agent
     */
    private fun generateEmail() {
        val agentNameLower = agentName.lowercase().replace(" ", ".")
        val generatedEmail = "$agentNameLower@agentos.ai"
        
        // Update UI to show email is generated
        emailAddressText.text = generatedEmail
        emailAddressText.visibility = View.VISIBLE
        
        // Set up copy functionality for email address
        emailAddressText.setOnClickListener {
            copyToClipboard("Email Address", generatedEmail)
        }
        
        // Hide generate email button, show view inbox button
        generateEmailButton.visibility = View.GONE
        viewInboxButton.visibility = View.VISIBLE
        
        Toast.makeText(requireContext(), "Email generated: $generatedEmail", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Copy text to clipboard
     */
    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
