package com.myagentos.app.domain.model

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?,
    val isSystemApp: Boolean = false
)
