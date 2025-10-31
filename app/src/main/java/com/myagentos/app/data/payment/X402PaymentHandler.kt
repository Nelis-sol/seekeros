package com.myagentos.app.data.payment

import android.content.Context
import com.myagentos.app.domain.model.PaymentInfo
import com.myagentos.app.domain.model.PaymentProof
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

/**
 * x402 payment handler that integrates with Solana Mobile Wallet Adapter
 */
class X402PaymentHandler(
    private val context: Context,
    private val activityResultSender: ActivityResultSender
) {
    
    private val solanaPaymentManager = SolanaPaymentManager(context, activityResultSender)
    
    /**
     * Check if a payment proof should be sent with the request
     * For static pricing, this is always determined upfront
     */
    fun shouldIncludePayment(paymentInfo: PaymentInfo?): Boolean {
        return paymentInfo?.required == true
    }
    
    /**
     * Create a real payment using Solana Mobile Wallet Adapter
     */
    suspend fun createPayment(paymentInfo: PaymentInfo, fromAddress: String? = null): PaymentProof {
        return solanaPaymentManager.createPayment(paymentInfo, fromAddress)
    }
    
    /**
     * Create a mock payment proof for testing (fallback)
     * Use createPayment() for real payments
     */
    fun createMockPaymentProof(paymentInfo: PaymentInfo, fromAddress: String): PaymentProof {
        return PaymentProof(
            x402Version = 1,
            scheme = "exact",
            network = "solana",
            transactionSignature = "mock_tx_${System.currentTimeMillis()}",
            amount = paymentInfo.price,
            currency = paymentInfo.currency,
            from = fromAddress,
            to = paymentInfo.recipient ?: "unknown_recipient"
        )
    }
}

