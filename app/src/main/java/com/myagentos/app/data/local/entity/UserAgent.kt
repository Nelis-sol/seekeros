package com.myagentos.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_agents")
data class UserAgent(
    @PrimaryKey val id: String,
    val name: String,
    val creator: String,
    val description: String,
    val personalityPrompt: String,
    val systemPrompt: String,
    val iconResId: Int = 0, // Resource ID for drawable
    val createdAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false
)

