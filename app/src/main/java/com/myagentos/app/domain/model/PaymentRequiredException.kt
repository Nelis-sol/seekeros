package com.myagentos.app.domain.model

/**
 * Exception thrown when MCP app responds with 402 Payment Required
 */
class PaymentRequiredException(
    val paymentInfo: PaymentInfo,
    message: String = "Payment required for this tool"
) : Exception(message)

/**
 * Exception thrown when user declines payment
 */
class PaymentDeclinedException(
    message: String = "Payment declined by user"
) : Exception(message)

