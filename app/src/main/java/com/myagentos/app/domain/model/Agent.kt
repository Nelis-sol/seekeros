package com.myagentos.app.domain.model

import java.io.Serializable

/**
 * Represents an AI Agent with profile information
 */
data class Agent(
    val id: String,
    val name: String,
    val creator: String,
    val description: String,
    val iconResId: Int? = null, // Resource ID for icon drawable
    val iconUrl: String? = null, // URL for remote icon
    val url: String? = null, // Agent's website
    val personalityPrompt: String = "",
    val systemPrompt: String = "",
    val wallet: String? = null, // Wallet address
    val email: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

