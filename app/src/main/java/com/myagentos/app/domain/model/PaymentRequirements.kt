package com.myagentos.app.domain.model

/**
 * Payment requirements from MCP server error response
 */
data class PaymentRequirements(
    val price: PaymentPrice,
    val recipient: String,
    val description: String,
    val currency: String,
    val network: String
)

/**
 * Payment price information
 */
data class PaymentPrice(
    val amount: String, // Amount in micro-units (e.g., "10000" = $0.01 USDC)
    val asset: PaymentAsset
)

/**
 * Payment asset information
 */
data class PaymentAsset(
    val address: String // Token mint address (e.g., USDC mint address)
)
