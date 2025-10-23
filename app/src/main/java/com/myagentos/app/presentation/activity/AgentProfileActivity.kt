package com.myagentos.app.presentation.activity

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.myagentos.app.R
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.launch

/**
 * Full-screen activity for displaying agent profile details
 */
class AgentProfileActivity : AppCompatActivity() {
    
    private lateinit var backButton: Button
    private lateinit var agentIconLarge: ImageView
    private lateinit var agentName: TextView
    private lateinit var agentCreator: TextView
    private lateinit var agentDescription: TextView
    private lateinit var agentPersonalityPrompt: TextView
    private lateinit var agentSystemPrompt: TextView
    private lateinit var addWalletButton: Button
    private lateinit var generateEmailButton: Button
    
    // Mobile Wallet Adapter
    private lateinit var walletAdapter: MobileWalletAdapter
    private lateinit var activityResultSender: ActivityResultSender
    private var walletPublicKey: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_profile)
        
        // Initialize views
        backButton = findViewById(R.id.backButton)
        agentIconLarge = findViewById(R.id.agentIconLarge)
        agentName = findViewById(R.id.agentName)
        agentCreator = findViewById(R.id.agentCreator)
        agentDescription = findViewById(R.id.agentDescription)
        agentPersonalityPrompt = findViewById(R.id.agentPersonalityPrompt)
        agentSystemPrompt = findViewById(R.id.agentSystemPrompt)
        addWalletButton = findViewById(R.id.addWalletButton)
        generateEmailButton = findViewById(R.id.generateEmailButton)
        
        // Get agent data from intent
        val agentId = intent.getStringExtra("agent_id") ?: ""
        val name = intent.getStringExtra("agent_name") ?: ""
        val creator = intent.getStringExtra("agent_creator") ?: ""
        val description = intent.getStringExtra("agent_description") ?: ""
        val personalityPrompt = intent.getStringExtra("agent_personality_prompt") ?: ""
        val systemPrompt = intent.getStringExtra("agent_system_prompt") ?: ""
        val iconResId = intent.getIntExtra("agent_icon_res_id", 0)
        
        // Populate views
        agentName.text = name
        agentCreator.text = "by $creator"
        agentDescription.text = description
        agentPersonalityPrompt.text = personalityPrompt
        agentSystemPrompt.text = systemPrompt
        
        // Set icon if available
        if (iconResId != 0) {
            agentIconLarge.setBackgroundResource(iconResId)
        }
        
        // Initialize Mobile Wallet Adapter and ActivityResultSender
        // IMPORTANT: ActivityResultSender must be created in onCreate() before activity is STARTED
        activityResultSender = ActivityResultSender(this)
        setupWalletAdapter()
        
        // Back button
        backButton.setOnClickListener {
            finish()
        }
        
        // Add Wallet button - Connect to Solana wallet
        addWalletButton.setOnClickListener {
            connectToWallet()
        }
        
        // Generate Email button
        generateEmailButton.setOnClickListener {
            Toast.makeText(this, "Generate email feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Setup Mobile Wallet Adapter client
     */
    private fun setupWalletAdapter() {
        // Define dApp's identity metadata
        val solanaUri = Uri.parse("https://agentos.ai")
        val iconUri = Uri.parse("favicon.ico")
        val identityName = "AgentOS"
        
        // Construct the MobileWalletAdapter client
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
                // Connect to wallet using the pre-initialized ActivityResultSender
                val result = walletAdapter.connect(activityResultSender)
                
                when (result) {
                    is TransactionResult.Success -> {
                        // On success, an AuthorizationResult type is returned
                        val authResult = result.authResult
                        val publicKey = authResult.accounts.firstOrNull()?.publicKey
                        
                        if (publicKey != null) {
                            walletPublicKey = publicKey.contentToString()
                            
                            // Convert ByteArray to Base58 string for display
                            val publicKeyString = Base58Encoder.encodeToString(publicKey)
                            
                            // Update button appearance and text to show wallet is connected
                            addWalletButton.text = "${publicKeyString.take(4)}...${publicKeyString.takeLast(4)}"
                            addWalletButton.setBackgroundResource(R.drawable.pill_button_background)
                            addWalletButton.setTextColor(resources.getColor(android.R.color.white, null))
                            
                            Toast.makeText(
                                this@AgentProfileActivity,
                                "Connected! Public Key: $publicKeyString",
                                Toast.LENGTH_LONG
                            ).show()
                            
                            android.util.Log.d("AgentProfile", "Wallet connected: $publicKeyString")
                        } else {
                            Toast.makeText(
                                this@AgentProfileActivity,
                                "No wallet account found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    is TransactionResult.NoWalletFound -> {
                        Toast.makeText(
                            this@AgentProfileActivity,
                            "No MWA compatible wallet app found on device",
                            Toast.LENGTH_SHORT
                        ).show()
                        android.util.Log.e("AgentProfile", "No wallet found")
                    }
                    is TransactionResult.Failure -> {
                        Toast.makeText(
                            this@AgentProfileActivity,
                            "Error connecting to wallet: ${result.e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        android.util.Log.e("AgentProfile", "Wallet connection failed", result.e)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@AgentProfileActivity,
                    "Unexpected error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                android.util.Log.e("AgentProfile", "Unexpected wallet connection error", e)
            }
        }
    }
}

// Base58 encoding utility
private object Base58Encoder {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    
    fun encodeToString(input: ByteArray): String {
        if (input.isEmpty()) return ""
        
        // Count leading zeros
        var zeros = 0
        while (zeros < input.size && input[zeros].toInt() == 0) {
            zeros++
        }
        
        // Convert to base58
        val encoded = CharArray(input.size * 2)
        var outputStart = encoded.size
        var inputStart = zeros
        
        while (inputStart < input.size) {
            encoded[--outputStart] = ALPHABET[divmod(input, inputStart, 256, 58)]
            if (input[inputStart].toInt() == 0) {
                inputStart++
            }
        }
        
        // Skip leading zeros in the output
        while (outputStart < encoded.size && encoded[outputStart] == ALPHABET[0]) {
            outputStart++
        }
        
        // Add leading '1' for each leading zero byte
        while (--zeros >= 0) {
            encoded[--outputStart] = ALPHABET[0]
        }
        
        return String(encoded, outputStart, encoded.size - outputStart)
    }
    
    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Int {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder
    }
}
