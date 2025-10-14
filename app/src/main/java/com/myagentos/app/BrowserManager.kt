package com.myagentos.app

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.UUID

class BrowserManager(private val context: Context) {
    
    private val tabs = mutableListOf<BrowserTab>()
    private val bookmarks = mutableListOf<BrowserBookmark>()
    private val history = mutableListOf<BrowserHistoryItem>()
    private var currentTabId: String? = null
    
    fun createNewTab(isIncognito: Boolean = false): BrowserTab {
        val webView = createWebView()
        val tabId = UUID.randomUUID().toString()
        
        val tab = BrowserTab(
            id = tabId,
            webView = webView,
            isIncognito = isIncognito,
            isActive = true
        )
        
        // Deactivate all other tabs
        tabs.forEach { it.copy(isActive = false) }
        
        tabs.add(tab)
        currentTabId = tabId
        
        return tab
    }
    
    fun getCurrentTab(): BrowserTab? {
        return tabs.find { it.id == currentTabId }
    }
    
    fun switchToTab(tabId: String) {
        tabs.forEach { tab ->
            if (tab.id == tabId) {
                currentTabId = tabId
            }
        }
    }
    
    fun closeTab(tabId: String) {
        val tab = tabs.find { it.id == tabId }
        tab?.webView?.destroy()
        tabs.removeAll { it.id == tabId }
        
        // If we closed the current tab, switch to another one
        if (currentTabId == tabId) {
            currentTabId = tabs.firstOrNull()?.id
        }
    }
    
    fun getAllTabs(): List<BrowserTab> = tabs.toList()
    
    fun addBookmark(title: String, url: String) {
        val bookmark = BrowserBookmark(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url
        )
        bookmarks.add(bookmark)
    }
    
    fun removeBookmark(bookmarkId: String) {
        bookmarks.removeAll { it.id == bookmarkId }
    }
    
    fun getAllBookmarks(): List<BrowserBookmark> = bookmarks.toList()
    
    fun addToHistory(title: String, url: String) {
        val historyItem = BrowserHistoryItem(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url
        )
        history.add(0, historyItem) // Add to beginning
        
        // Keep only last 1000 history items
        if (history.size > 1000) {
            history.removeAt(history.size - 1)
        }
    }
    
    fun clearHistory() {
        history.clear()
    }
    
    fun getAllHistory(): List<BrowserHistoryItem> = history.toList()
    
    private fun createWebView(): WebView {
        val webView = WebView(context)
        
        // Configure WebView settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        
        // Set WebView client
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { 
                    val title = view?.title ?: ""
                    addToHistory(title, it)
                }
            }
        }
        
        // Set WebChrome client
        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                // Update tab title
                getCurrentTab()?.let { tab ->
                    val updatedTab = tab.copy(title = title ?: "")
                    val index = tabs.indexOfFirst { it.id == tab.id }
                    if (index >= 0) {
                        tabs[index] = updatedTab
                    }
                }
            }
        }
        
        // Set download listener
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            // Handle downloads
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            context.startActivity(intent)
        }
        
        return webView
    }
}
