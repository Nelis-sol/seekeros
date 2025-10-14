package com.myagentos.app

data class BrowserHistoryItem(
    val id: String,
    val title: String,
    val url: String,
    val dateVisited: Long = System.currentTimeMillis()
)
