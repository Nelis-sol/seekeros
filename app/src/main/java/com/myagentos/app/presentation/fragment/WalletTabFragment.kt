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
import com.myagentos.app.util.SolanaTransactionBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Wallet tab fragment for agent profile
 */
class WalletTabFragment : Fragment() {
    
    private lateinit var walletAddressOverview: TextView
    private lateinit var connectWalletButton: Button
    private lateinit var solBalance: TextView
    private lateinit var solValue: TextView
    private lateinit var usdcBalance: TextView
    private lateinit var usdcValue: TextView
    private lateinit var openWalletAppFromTokensButton: Button
    
    // Mobile Wallet Adapter
    private lateinit var walletAdapter: MobileWalletAdapter
    private var walletPublicKey: String? = null
    
    // HTTP client for RPC calls
    private val httpClient = OkHttpClient()
    private val rpcUrl = "https://api.devnet.solana.com"
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.tab_wallet, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        walletAddressOverview = view.findViewById(R.id.walletAddressOverview)
        connectWalletButton = view.findViewById(R.id.connectWalletButton)
        solBalance = view.findViewById(R.id.solBalance)
        solValue = view.findViewById(R.id.solValue)
        usdcBalance = view.findViewById(R.id.usdcBalance)
        usdcValue = view.findViewById(R.id.usdcValue)
        openWalletAppFromTokensButton = view.findViewById(R.id.openWalletAppFromTokensButton)
        
        // Initialize Mobile Wallet Adapter
        setupWalletAdapter()
        
        // Set up click listeners
        connectWalletButton.setOnClickListener {
            connectToWallet()
        }
        
        openWalletAppFromTokensButton.setOnClickListener {
            openWalletApp()
        }
        
        // Set up copy functionality for wallet address
        walletAddressOverview.setOnClickListener {
            if (walletPublicKey != null) {
                copyToClipboard("Wallet Address", walletPublicKey!!)
            }
        }
        
        // Check if wallet is already connected from parent activity
        checkWalletStatus()
    }
    
    /**
     * Check if wallet is already connected from parent activity or storage
     */
    private fun checkWalletStatus() {
        val activity = requireActivity() as? com.myagentos.app.presentation.activity.AgentProfileActivity
        val isConnected = activity?.isWalletConnected() == true || WalletStorage.isWalletConnected(requireContext())
        
        if (isConnected) {
            // Get wallet address from storage if not available from activity
            val savedAddress = WalletStorage.getWalletAddress(requireContext())
            if (savedAddress != null) {
                walletPublicKey = savedAddress
                walletAddressOverview.text = savedAddress
                walletAddressOverview.visibility = View.VISIBLE
            }
            
            // Wallet is already connected, update UI accordingly
            connectWalletButton.visibility = View.GONE
            openWalletAppFromTokensButton.isEnabled = true
            
            // Load balances
            loadBalances()
            
            // Automatically trigger SOL transfer if wallet is already connected
            android.util.Log.d("WalletTab", "Wallet already connected on fragment creation, triggering SOL transfer...")
            sendTestSOLTransfer()
        }
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
                
                // Combine wallet connection AND transaction into a single transact() call
                android.util.Log.d("WalletTab", "========================================")
                android.util.Log.d("WalletTab", "Starting combined connection + transaction flow")
                android.util.Log.d("WalletTab", "========================================")
                
                // Amount: 0.0001 SOL = 100,000 lamports
                val lamportAmount = 100_000L
                val recipientAddress = "11111111111111111111111111111111"
                
                android.util.Log.d("WalletTab", "[1/5] Fetching blockhash before wallet connection...")
                val blockhash = try {
                    getLatestBlockhash()
                } catch (e: Exception) {
                    android.util.Log.e("WalletTab", "[ERROR] Failed to fetch blockhash", e)
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Error fetching blockhash: ${e.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
                android.util.Log.d("WalletTab", "[1/5] Blockhash obtained: ${blockhash.take(20)}...")
                
                // Use transact() which handles both connection AND transaction in one flow
                android.util.Log.d("WalletTab", "[2/5] Calling walletAdapter.transact() to connect AND send transaction...")
                val result = walletAdapter.transact(activityResultSender) { authResult ->
                    android.util.Log.d("WalletTab", "[3/5] ===== INSIDE TRANSACT LAMBDA =====")
                    android.util.Log.d("WalletTab", "[3/5] AuthResult received - wallet is connected")
                    android.util.Log.d("WalletTab", "[3/5] Accounts count: ${authResult.accounts.size}")
                    
                    if (authResult.accounts.isEmpty()) {
                        android.util.Log.e("WalletTab", "[ERROR] No accounts in authResult!")
                        throw Exception("No accounts found in wallet auth result")
                    }
                    
                    val userAccountPublicKey = authResult.accounts.first().publicKey
                    android.util.Log.d("WalletTab", "[3/5] User account public key size: ${userAccountPublicKey.size} bytes")
                    
                    // Decode recipient address
                    android.util.Log.d("WalletTab", "[4/5] Decoding recipient address...")
                    val recipientPublicKey = SolanaTransactionBuilder.decodeBase58(recipientAddress)
                    
                    // Build transaction
                    android.util.Log.d("WalletTab", "[4/5] Building SOL transfer transaction...")
                    val transferTx = SolanaTransactionBuilder.buildSOLTransfer(
                        fromPublicKey = userAccountPublicKey,
                        toPublicKey = recipientPublicKey,
                        lamports = lamportAmount,
                        recentBlockhash = blockhash
                    )
                    android.util.Log.d("WalletTab", "[4/5] Transaction built: ${transferTx.size} bytes")
                    
                    // Sign and send
                    android.util.Log.d("WalletTab", "[5/5] Calling signAndSendTransactions...")
                    signAndSendTransactions(arrayOf(transferTx))
                }
                
                android.util.Log.d("WalletTab", "[RESULT] Combined flow completed")
                android.util.Log.d("WalletTab", "[RESULT] Result type: ${result.javaClass.simpleName}")
                
                // Handle the result - this could be either a connection result or transaction result
                when (result) {
                    is TransactionResult.Success -> {
                        // Check if this is a connection result (has authResult) or transaction result (has signatures)
                        val authResult = result.authResult
                        
                        if (authResult != null && authResult.accounts.isNotEmpty()) {
                            // This is a connection result - update UI
                            val publicKey = authResult.accounts.first().publicKey
                            walletPublicKey = Base58Encoder.encodeToString(publicKey)
                            
                            // Save wallet state
                            WalletStorage.saveWalletState(requireContext(), walletPublicKey!!, true)
                            
                            // Update UI
                            walletAddressOverview.text = walletPublicKey
                            walletAddressOverview.visibility = View.VISIBLE
                            connectWalletButton.visibility = View.GONE
                            openWalletAppFromTokensButton.isEnabled = true
                            loadBalances()
                            
                            android.util.Log.d("WalletTab", "[RESULT] Wallet connection successful, processing transaction result...")
                            // Now handle the transaction result
                            handleTransactionResult(result)
                        } else {
                            // This is just a transaction result
                            android.util.Log.d("WalletTab", "[RESULT] Transaction result only, handling...")
                            handleTransactionResult(result)
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
     * Open wallet app on the phone
     */
    private fun openWalletApp() {
        try {
            val walletApps = listOf(
                "com.solanamobile.wallet", // Solana Mobile (detected on device)
                "com.solana.mobile", // Solana Mobile (alternative)
                "com.phantom.app", // Phantom
                "com.coinbase.wallet", // Coinbase Wallet
                "io.metamask", // MetaMask
                "com.trustwallet.app" // Trust Wallet
            )
            
            var walletOpened = false
            for (packageName in walletApps) {
                try {
                    val intent = requireActivity().packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        startActivity(intent)
                        walletOpened = true
                        break
                    }
                } catch (e: Exception) {
                    // Continue to next wallet app
                }
            }
            
            if (!walletOpened) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://solana.com/wallets")
                startActivity(intent)
                Toast.makeText(requireContext(), "Opening wallet options in browser", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open wallet app", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Load wallet balances (mock data for now)
     */
    private fun loadBalances() {
        // Mock data - in real implementation, this would fetch from Solana RPC
        solBalance.text = "2.45 SOL"
        solValue.text = "$245.00"
        usdcBalance.text = "1,250.00 USDC"
        usdcValue.text = "$1,250.00"
        
        // Enable open wallet app button
        openWalletAppFromTokensButton.isEnabled = true
    }
    
    /**
     * Refresh wallet balances
     */
    private fun refreshBalances() {
        Toast.makeText(requireContext(), "Refreshing balances...", Toast.LENGTH_SHORT).show()
        // In real implementation, this would fetch fresh data from Solana RPC
        loadBalances()
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
    
    /**
     * Fetch the latest blockhash from Solana network
     */
    private suspend fun getLatestBlockhash(): String {
        return withContext(Dispatchers.IO) {
            android.util.Log.d("WalletTab", "Making RPC call to: $rpcUrl")
            val requestBody = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getLatestBlockhash")
                put("params", JSONObject().apply {
                    put("commitment", "finalized")
                })
            }.toString()
            
            val request = Request.Builder()
                .url(rpcUrl)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            
            android.util.Log.d("WalletTab", "Executing HTTP request...")
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            android.util.Log.d("WalletTab", "Response code: ${response.code}")
            android.util.Log.d("WalletTab", "Response body: ${responseBody?.take(200)}")
            
            if (responseBody == null) {
                throw Exception("No response from RPC")
            }
            
            val json = JSONObject(responseBody)
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                throw Exception("RPC error: ${error.getString("message")}")
            }
            val result = json.getJSONObject("result")
            val blockhashObj = result.getJSONObject("value")
            val blockhash = blockhashObj.getString("blockhash")
            android.util.Log.d("WalletTab", "Blockhash retrieved successfully")
            blockhash
        }
    }
    
    /**
     * Send SOL transfer immediately after wallet connection (reusing auth result)
     */
    private fun sendTestSOLTransferAfterConnection(
        activityResultSender: ActivityResultSender,
        authResult: Any,
        userPublicKey: ByteArray
    ) {
        android.util.Log.d("WalletTab", "========================================")
        android.util.Log.d("WalletTab", "sendTestSOLTransferAfterConnection() ENTRY")
        android.util.Log.d("WalletTab", "========================================")
        
        lifecycleScope.launch {
            try {
                android.util.Log.d("WalletTab", "[1/8] Starting SOL transfer with existing connection")
                Toast.makeText(requireContext(), "Preparing SOL transfer...", Toast.LENGTH_SHORT).show()
                
                // Amount: 0.0001 SOL = 100,000 lamports
                val lamportAmount = 100_000L
                android.util.Log.d("WalletTab", "[2/8] Transfer amount: $lamportAmount lamports (0.0001 SOL)")
                
                // Recipient address
                val recipientAddress = "11111111111111111111111111111111"
                android.util.Log.d("WalletTab", "[2/8] Recipient address: $recipientAddress")
                
                // Fetch latest blockhash
                android.util.Log.d("WalletTab", "[3/8] Fetching latest blockhash from RPC...")
                val blockhash = try {
                    getLatestBlockhash()
                } catch (e: Exception) {
                    android.util.Log.e("WalletTab", "[ERROR] Failed to fetch blockhash", e)
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Error fetching blockhash: ${e.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
                android.util.Log.d("WalletTab", "[3/8] Blockhash obtained: ${blockhash.take(20)}...")
                
                // Decode recipient address
                android.util.Log.d("WalletTab", "[4/8] Decoding recipient address...")
                val recipientPublicKey = try {
                    SolanaTransactionBuilder.decodeBase58(recipientAddress)
                } catch (e: Exception) {
                    android.util.Log.e("WalletTab", "[ERROR] Failed to decode recipient", e)
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Error decoding recipient: ${e.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                // Build transaction
                android.util.Log.d("WalletTab", "[5/8] Building SOL transfer transaction...")
                val transferTx = try {
                    SolanaTransactionBuilder.buildSOLTransfer(
                        fromPublicKey = userPublicKey,
                        toPublicKey = recipientPublicKey,
                        lamports = lamportAmount,
                        recentBlockhash = blockhash
                    )
                } catch (e: Exception) {
                    android.util.Log.e("WalletTab", "[ERROR] Failed to build transaction", e)
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Error building transaction: ${e.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
                android.util.Log.d("WalletTab", "[5/8] Transaction built: ${transferTx.size} bytes")
                
                // Now use transact() to sign and send (it will reuse the existing connection)
                android.util.Log.d("WalletTab", "[6/8] Calling walletAdapter.transact() to sign and send...")
                val result = walletAdapter.transact(activityResultSender) { authResult ->
                    android.util.Log.d("WalletTab", "[7/8] Inside transact lambda - calling signAndSendTransactions")
                    signAndSendTransactions(arrayOf(transferTx))
                }
                
                android.util.Log.d("WalletTab", "[8/8] Transaction result received: ${result.javaClass.simpleName}")
                
                // Handle result
                handleTransactionResult(result)
                
            } catch (e: Exception) {
                android.util.Log.e("WalletTab", "[ERROR] Exception in sendTestSOLTransferAfterConnection", e)
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Send a test SOL transfer (0.0001 SOL = 100,000 lamports)
     * Based on: https://docs.solanamobile.com/android-native/sending-sol
     */
    private fun sendTestSOLTransfer() {
        android.util.Log.d("WalletTab", "========================================")
        android.util.Log.d("WalletTab", "sendTestSOLTransfer() ENTRY POINT")
        android.util.Log.d("WalletTab", "========================================")
        
        lifecycleScope.launch {
            try {
                android.util.Log.d("WalletTab", "[1/10] Starting SOL transfer process in coroutine")
                Toast.makeText(requireContext(), "Preparing SOL transfer...", Toast.LENGTH_SHORT).show()
                
                android.util.Log.d("WalletTab", "[2/10] Getting activity reference")
                val activity = requireActivity() as? com.myagentos.app.presentation.activity.AgentProfileActivity
                android.util.Log.d("WalletTab", "[2/10] Activity class: ${activity?.javaClass?.simpleName}")
                
                if (activity == null) {
                    android.util.Log.e("WalletTab", "[ERROR] Activity is null!")
                    Toast.makeText(requireContext(), "Activity is null", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                android.util.Log.d("WalletTab", "[3/10] Getting ActivityResultSender")
                val activityResultSender = activity.getActivityResultSender()
                android.util.Log.d("WalletTab", "[3/10] ActivityResultSender: ${activityResultSender != null}")
                
                if (activityResultSender == null) {
                    android.util.Log.e("WalletTab", "[ERROR] ActivityResultSender is null!")
                    Toast.makeText(requireContext(), "Activity not ready", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                android.util.Log.d("WalletTab", "[3/10] ActivityResultSender obtained successfully")
                
                // Amount: 0.0001 SOL = 100,000 lamports
                val lamportAmount = 100_000L
                android.util.Log.d("WalletTab", "[4/10] Transfer amount set: $lamportAmount lamports (0.0001 SOL)")
                
                // Recipient address - you can change this to any valid Solana address
                // This is a test address on devnet
                val recipientAddress = "11111111111111111111111111111111"
                android.util.Log.d("WalletTab", "[4/10] Recipient address: $recipientAddress")
                
                // Fetch latest blockhash first
                android.util.Log.d("WalletTab", "[5/10] Fetching latest blockhash from RPC...")
                val blockhash = try {
                    getLatestBlockhash()
                } catch (e: Exception) {
                    android.util.Log.e("WalletTab", "[ERROR] Failed to fetch blockhash", e)
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Error fetching blockhash: ${e.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }
                android.util.Log.d("WalletTab", "[5/10] Blockhash obtained successfully: ${blockhash.take(20)}... (full: $blockhash)")
                
                android.util.Log.d("WalletTab", "[6/10] Starting walletAdapter.transact() call...")
                android.util.Log.d("WalletTab", "[6/10] WalletAdapter instance: ${walletAdapter != null}")
                
                val result = walletAdapter.transact(activityResultSender) { authResult ->
                    android.util.Log.d("WalletTab", "[7/10] ===== INSIDE TRANSACT LAMBDA =====")
                    android.util.Log.d("WalletTab", "[7/10] AuthResult received")
                    android.util.Log.d("WalletTab", "[7/10] Accounts count: ${authResult.accounts.size}")
                    
                    if (authResult.accounts.isEmpty()) {
                        android.util.Log.e("WalletTab", "[ERROR] No accounts in authResult!")
                        throw Exception("No accounts found in wallet auth result")
                    }
                    
                    // Retrieve the user wallet address from the MWA authResult
                    val userAccountPublicKey = authResult.accounts.first().publicKey
                    android.util.Log.d("WalletTab", "[7/10] User account public key size: ${userAccountPublicKey.size} bytes")
                    android.util.Log.d("WalletTab", "[7/10] User account public key (first 20 bytes): ${userAccountPublicKey.take(20).joinToString(",") { "%02x".format(it) }}")
                    
                    // Decode recipient address from base58
                    android.util.Log.d("WalletTab", "[8/10] Decoding recipient address from base58...")
                    val recipientPublicKey = try {
                        SolanaTransactionBuilder.decodeBase58(recipientAddress)
                    } catch (e: Exception) {
                        android.util.Log.e("WalletTab", "[ERROR] Failed to decode recipient address", e)
                        e.printStackTrace()
                        throw e
                    }
                    android.util.Log.d("WalletTab", "[8/10] Recipient public key decoded: size=${recipientPublicKey.size} bytes")
                    
                    // Build the SOL transfer transaction
                    android.util.Log.d("WalletTab", "[9/10] Building SOL transfer transaction...")
                    android.util.Log.d("WalletTab", "[9/10] Parameters: from=${userAccountPublicKey.size} bytes, to=${recipientPublicKey.size} bytes, lamports=$lamportAmount, blockhash=$blockhash")
                    
                    val transferTx = try {
                        SolanaTransactionBuilder.buildSOLTransfer(
                            fromPublicKey = userAccountPublicKey,
                            toPublicKey = recipientPublicKey,
                            lamports = lamportAmount,
                            recentBlockhash = blockhash
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("WalletTab", "[ERROR] Failed to build transaction", e)
                        e.printStackTrace()
                        throw e
                    }
                    android.util.Log.d("WalletTab", "[9/10] Transaction built successfully!")
                    android.util.Log.d("WalletTab", "[9/10] Transaction size: ${transferTx.size} bytes")
                    android.util.Log.d("WalletTab", "[9/10] Transaction bytes (first 50): ${transferTx.take(50).joinToString(",") { "%02x".format(it) }}")
                    
                    // Issue a 'signAndSendTransactions' request
                    android.util.Log.d("WalletTab", "[10/10] Calling signAndSendTransactions...")
                    android.util.Log.d("WalletTab", "[10/10] Transaction array size: 1")
                    val result = signAndSendTransactions(arrayOf(transferTx))
                    android.util.Log.d("WalletTab", "[10/10] signAndSendTransactions returned: ${result != null}")
                    result
                }
                
                android.util.Log.d("WalletTab", "[RESULT] Transaction flow completed")
                android.util.Log.d("WalletTab", "[RESULT] Result type: ${result.javaClass.simpleName}")
                android.util.Log.d("WalletTab", "[RESULT] Is Success: ${result is TransactionResult.Success}")
                android.util.Log.d("WalletTab", "[RESULT] Is NoWalletFound: ${result is TransactionResult.NoWalletFound}")
                android.util.Log.d("WalletTab", "[RESULT] Is Failure: ${result is TransactionResult.Failure}")
                
                // Handle the results - using reflection to access the payload since the API structure may vary
                android.util.Log.d("WalletTab", "[RESULT] Processing transaction result...")
                try {
                    when (result) {
                        is TransactionResult.Success -> {
                            android.util.Log.d("WalletTab", "[RESULT] SUCCESS - TransactionResult.Success received")
                            
                            // Try to access successPayload
                            try {
                                android.util.Log.d("WalletTab", "[RESULT] Attempting to access successPayload field...")
                                val payloadField = result.javaClass.getDeclaredField("successPayload")
                                payloadField.isAccessible = true
                                val payload = payloadField.get(result)
                                android.util.Log.d("WalletTab", "[RESULT] Payload obtained: ${payload != null}, type: ${payload?.javaClass?.simpleName}")
                                
                                if (payload != null) {
                                    // Try to get signatures from payload
                                    try {
                                        val signaturesField = payload.javaClass.getDeclaredField("signatures")
                                        signaturesField.isAccessible = true
                                        val signatures = signaturesField.get(payload) as? List<*>
                                        android.util.Log.d("WalletTab", "[RESULT] Signatures list: ${signatures != null}, size: ${signatures?.size ?: 0}")
                                        
                                        val signatureBytes = signatures?.firstOrNull() as? ByteArray
                                        if (signatureBytes != null) {
                                            android.util.Log.d("WalletTab", "[RESULT] Signature bytes obtained: ${signatureBytes.size} bytes")
                                            val signature = Base58Encoder.encodeToString(signatureBytes)
                                            android.util.Log.d("WalletTab", "[RESULT] SUCCESS! Transaction signature: $signature")
                                            Toast.makeText(
                                                requireContext(), 
                                                "Transaction successful!\nSignature: ${signature.take(20)}...", 
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            android.util.Log.w("WalletTab", "[RESULT] No signature bytes in signatures list")
                                            Toast.makeText(
                                                requireContext(), 
                                                "Transaction submitted successfully", 
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("WalletTab", "[RESULT] Error accessing signatures field", e)
                                        e.printStackTrace()
                                        Toast.makeText(
                                            requireContext(), 
                                            "Transaction submitted (signature unavailable)", 
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    android.util.Log.w("WalletTab", "[RESULT] Payload is null")
                                    Toast.makeText(
                                        requireContext(), 
                                        "Transaction submitted successfully", 
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("WalletTab", "[RESULT] Error accessing successPayload field", e)
                                e.printStackTrace()
                                Toast.makeText(
                                    requireContext(), 
                                    "Transaction submitted successfully", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        is TransactionResult.NoWalletFound -> {
                            android.util.Log.e("WalletTab", "[RESULT] FAILURE - NoWalletFound")
                            Toast.makeText(
                                requireContext(), 
                                "No MWA compatible wallet app found on device.", 
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is TransactionResult.Failure -> {
                            android.util.Log.e("WalletTab", "[RESULT] FAILURE - TransactionResult.Failure")
                            android.util.Log.e("WalletTab", "[RESULT] Error message: ${result.e.message}")
                            android.util.Log.e("WalletTab", "[RESULT] Error type: ${result.e.javaClass.simpleName}")
                            result.e.printStackTrace()
                            Toast.makeText(
                                requireContext(), 
                                "Error during signing and sending: ${result.e.message}", 
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WalletTab", "[RESULT] Exception while processing result", e)
                    e.printStackTrace()
                    // Fallback: just show success message
                    when (result) {
                        is TransactionResult.Success -> {
                            android.util.Log.d("WalletTab", "[RESULT] Fallback: Showing success message")
                            Toast.makeText(
                                requireContext(), 
                                "Transaction submitted successfully", 
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is TransactionResult.NoWalletFound -> {
                            Toast.makeText(
                                requireContext(), 
                                "No MWA compatible wallet app found on device.", 
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is TransactionResult.Failure -> {
                            Toast.makeText(
                                requireContext(), 
                                "Error: ${result.e.message}", 
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                
                android.util.Log.d("WalletTab", "========================================")
                android.util.Log.d("WalletTab", "sendTestSOLTransfer() EXIT")
                android.util.Log.d("WalletTab", "========================================")
                
                // Handle result
                handleTransactionResult(result)
                
            } catch (e: Exception) {
                android.util.Log.e("WalletTab", "========================================")
                android.util.Log.e("WalletTab", "EXCEPTION in sendTestSOLTransfer")
                android.util.Log.e("WalletTab", "Exception type: ${e.javaClass.simpleName}")
                android.util.Log.e("WalletTab", "Exception message: ${e.message}")
                android.util.Log.e("WalletTab", "========================================")
                e.printStackTrace()
                Toast.makeText(
                    requireContext(), 
                    "Error: ${e.message}", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Handle transaction result
     */
    private fun handleTransactionResult(result: TransactionResult<*>) {
        android.util.Log.d("WalletTab", "[RESULT] Processing transaction result...")
        try {
            when (result) {
                is TransactionResult.Success<*> -> {
                    android.util.Log.d("WalletTab", "[RESULT] SUCCESS - TransactionResult.Success received")
                    
                    // Try to access successPayload using reflection
                    try {
                        android.util.Log.d("WalletTab", "[RESULT] Attempting to access successPayload field...")
                        val payloadField = (result as Any).javaClass.getDeclaredField("successPayload")
                        payloadField.isAccessible = true
                        val payload = payloadField.get(result)
                        android.util.Log.d("WalletTab", "[RESULT] Payload obtained: ${payload != null}, type: ${payload?.javaClass?.simpleName}")
                        
                        if (payload != null) {
                            // Try to get signatures from payload
                            try {
                                val signaturesField = payload.javaClass.getDeclaredField("signatures")
                                signaturesField.isAccessible = true
                                val signatures = signaturesField.get(payload) as? List<*>
                                android.util.Log.d("WalletTab", "[RESULT] Signatures list: ${signatures != null}, size: ${signatures?.size ?: 0}")
                                
                                val signatureBytes = signatures?.firstOrNull() as? ByteArray
                                if (signatureBytes != null) {
                                    android.util.Log.d("WalletTab", "[RESULT] Signature bytes obtained: ${signatureBytes.size} bytes")
                                    val signature = Base58Encoder.encodeToString(signatureBytes)
                                    android.util.Log.d("WalletTab", "[RESULT] SUCCESS! Transaction signature: $signature")
                                    Toast.makeText(
                                        requireContext(), 
                                        "Transaction successful!\nSignature: ${signature.take(20)}...", 
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    android.util.Log.w("WalletTab", "[RESULT] No signature bytes in signatures list")
                                    Toast.makeText(
                                        requireContext(), 
                                        "Transaction submitted successfully", 
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("WalletTab", "[RESULT] Error accessing signatures field", e)
                                e.printStackTrace()
                                Toast.makeText(
                                    requireContext(), 
                                    "Transaction submitted (signature unavailable)", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            android.util.Log.w("WalletTab", "[RESULT] Payload is null")
                            Toast.makeText(
                                requireContext(), 
                                "Transaction submitted successfully", 
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("WalletTab", "[RESULT] Error accessing successPayload field", e)
                        e.printStackTrace()
                        Toast.makeText(
                            requireContext(), 
                            "Transaction submitted successfully", 
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                is TransactionResult.NoWalletFound -> {
                    android.util.Log.e("WalletTab", "[RESULT] FAILURE - NoWalletFound")
                    Toast.makeText(
                        requireContext(), 
                        "No MWA compatible wallet app found on device.", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is TransactionResult.Failure -> {
                    android.util.Log.e("WalletTab", "[RESULT] FAILURE - TransactionResult.Failure")
                    android.util.Log.e("WalletTab", "[RESULT] Error message: ${result.e.message}")
                    android.util.Log.e("WalletTab", "[RESULT] Error type: ${result.e.javaClass.simpleName}")
                    result.e.printStackTrace()
                    Toast.makeText(
                        requireContext(), 
                        "Error during signing and sending: ${result.e.message}", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WalletTab", "[RESULT] Exception while processing result", e)
            e.printStackTrace()
            Toast.makeText(
                requireContext(), 
                "Transaction submitted successfully", 
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
