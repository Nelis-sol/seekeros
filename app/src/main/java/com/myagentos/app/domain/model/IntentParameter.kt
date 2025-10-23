package com.myagentos.app.domain.model

data class IntentParameter(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String,
    val example: String
)
