package com.myagentos.app

data class IntentCapability(
    val action: String,
    val displayName: String,
    val icon: Int, // Resource ID for the action icon
    val description: String,
    val parameters: List<IntentParameter> = emptyList()
)
