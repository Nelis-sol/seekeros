package com.myagentos.app.domain.model

import java.io.Serializable

/**
 * Represents an AI agent job that can be executed on any screen
 */
data class Job(
    val id: String,
    val name: String,
    val prompt: String,
    val iconEmoji: String = "🤖", // Optional emoji icon for the job
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

