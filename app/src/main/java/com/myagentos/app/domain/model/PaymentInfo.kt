package com.myagentos.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Payment information for x402 protocol
 */
@Serializable
data class PaymentInfo(
    val required: Boolean = false,
    val price: Double = 0.0,
    val currency: String = "USDC",
    val description: String = "",
    val recipient: String? = null,
    val expiresAt: String? = null,
    val maxSlippage: Double? = null,
    val pricing: String? = null // "static", "dynamic", "user-based"
)

