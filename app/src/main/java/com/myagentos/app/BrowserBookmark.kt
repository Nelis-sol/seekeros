package com.myagentos.app

data class BrowserBookmark(
    val id: String,
    val title: String,
    val url: String,
    val dateAdded: Long = System.currentTimeMillis()
)
