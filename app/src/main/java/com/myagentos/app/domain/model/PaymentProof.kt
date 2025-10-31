package com.myagentos.app.domain.model

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * x402 Payment Proof for Solana transactions
 */
@Serializable
data class PaymentProof(
    val x402Version: Int = 1,
    val scheme: String = "exact",
    val network: String = "solana",
    val transactionSignature: String,
    val amount: Double,
    val currency: String,
    val from: String,
    val to: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private val json = Json { 
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    
    fun toBase64(): String {
        val jsonString = json.encodeToString(this)
        return Base64.encodeToString(jsonString.toByteArray(), Base64.NO_WRAP)
    }
}
