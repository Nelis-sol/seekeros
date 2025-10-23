package com.myagentos.app.domain.model

data class BrowserBookmark(
    val id: String,
    val title: String,
    val url: String,
    val dateAdded: Long = System.currentTimeMillis()
)
