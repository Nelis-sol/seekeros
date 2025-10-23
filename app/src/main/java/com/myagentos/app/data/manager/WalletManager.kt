package com.myagentos.app.data.manager

import com.myagentos.app.R

import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import com.solana.mobilewalletadapter.clientlib.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64

/**
 * Manages Mobile Wallet Adapter integration for connecting to wallets,
 * signing transactions, and sending them to the Solana network.
 */
class WalletManager(private val activity: ComponentActivity) {
    
    companion object {
        private const val TAG = "WalletManager"
        private const val IDENTITY_NAME = "AgentOS"
        private const val IDENTITY_URI = "https://agentos.com"
        private const val ICON_URI = "favicon.ico"
    }
    
    // ActivityResultSender must be created during activity initialization, not at runtime
    private val sender: ActivityResultSender = ActivityResultSender(activity)
    
    // Mobile Wallet Adapter client
    private val walletAdapter: MobileWalletAdapter by lazy {
        MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = Uri.parse(IDENTITY_URI),
                iconUri = Uri.parse(ICON_URI),
                identityName = IDENTITY_NAME
            )
        )
    }
    
    /**
     * Connect to a wallet and get the user's public key
     * @return The user's wallet address (base58 encoded), or null on error
     */
    suspend fun connectWallet(): String? {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "Connecting to wallet...")
                
                val result = walletAdapter.connect(sender)
                
                when (result) {
                    is TransactionResult.Success -> {
                        val authResult = result.authResult
                        val publicKey = authResult.accounts.firstOrNull()?.publicKey
                        
                        if (publicKey != null) {
                            val base58Address = Base58.encodeToString(publicKey)
                            Log.d(TAG, "Successfully connected to wallet: $base58Address")
                            base58Address
                        } else {
                            Log.e(TAG, "No account found in auth result")
                            null
                        }
                    }
                    is TransactionResult.NoWalletFound -> {
                        Log.e(TAG, "No MWA compatible wallet app found on device")
                        null
                    }
                    is TransactionResult.Failure -> {
                        Log.e(TAG, "Error connecting to wallet: ${result.e.message}", result.e)
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception connecting to wallet: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Sign a transaction using the connected wallet (does not submit to blockchain)
     * @param transactionBase64 The serialized transaction in base64 format
     * @return The signed transaction in base64 format, or null on error
     */
    suspend fun signTransaction(transactionBase64: String): String? {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "Signing transaction...")
                
                // Decode base64 transaction to bytes
                val transactionBytes = Base64.decode(transactionBase64, Base64.DEFAULT)
                
                val result = walletAdapter.transact(sender) { authResult ->
                    Log.d(TAG, "Connected to wallet, requesting signature...")
                    
                    // Sign the transaction only (don't send)
                    signTransactions(arrayOf(transactionBytes))
                }
                
                when (result) {
                    is TransactionResult.Success -> {
                        val signedTxBytes = result.successPayload?.signedPayloads?.firstOrNull()
                        
                        if (signedTxBytes != null) {
                            val signedTxBase64 = Base64.encodeToString(signedTxBytes, Base64.NO_WRAP)
                            Log.d(TAG, "Transaction signed successfully")
                            signedTxBase64
                        } else {
                            Log.e(TAG, "No signed transaction in success payload")
                            null
                        }
                    }
                    is TransactionResult.NoWalletFound -> {
                        Log.e(TAG, "No MWA compatible wallet app found on device")
                        null
                    }
                    is TransactionResult.Failure -> {
                        Log.e(TAG, "Error signing transaction: ${result.e.message}", result.e)
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception signing transaction: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Sign and send a transaction using the connected wallet
     * @param transactionBase64 The serialized transaction in base64 format
     * @return The transaction signature (base58 encoded), or null on error
     */
    suspend fun signAndSendTransaction(transactionBase64: String): String? {
        return withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "Signing and sending transaction...")
                
                // Decode base64 transaction to bytes
                val transactionBytes = Base64.decode(transactionBase64, Base64.DEFAULT)
                
                val result = walletAdapter.transact(sender) { authResult ->
                    Log.d(TAG, "Connected to wallet, sending transaction...")
                    
                    // Sign and send the transaction
                    signAndSendTransactions(arrayOf(transactionBytes))
                }
                
                when (result) {
                    is TransactionResult.Success -> {
                        val signatureBytes = result.successPayload?.signatures?.firstOrNull()
                        
                        if (signatureBytes != null) {
                            val signatureBase58 = Base58.encodeToString(signatureBytes)
                            Log.d(TAG, "Transaction signed and sent successfully: $signatureBase58")
                            signatureBase58
                        } else {
                            Log.e(TAG, "No signature in success payload")
                            null
                        }
                    }
                    is TransactionResult.NoWalletFound -> {
                        Log.e(TAG, "No MWA compatible wallet app found on device")
                        null
                    }
                    is TransactionResult.Failure -> {
                        Log.e(TAG, "Error signing/sending transaction: ${result.e.message}", result.e)
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception signing/sending transaction: ${e.message}", e)
                null
            }
        }
    }
}

// Base58 encoding utility
object Base58 {
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

