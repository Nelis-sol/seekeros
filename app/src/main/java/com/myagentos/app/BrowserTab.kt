package com.myagentos.app

import android.webkit.WebView

data class BrowserTab(
    val id: String,
    val webView: WebView,
    val title: String = "",
    val url: String = "",
    val isIncognito: Boolean = false,
    val isActive: Boolean = false
)
