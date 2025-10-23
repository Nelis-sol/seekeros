package com.myagentos.app.data.repository

import com.myagentos.app.data.manager.BrowserManager
import com.myagentos.app.domain.model.BrowserTab
import com.myagentos.app.domain.model.BrowserBookmark
import com.myagentos.app.domain.model.BrowserHistoryItem
import com.myagentos.app.domain.repository.BrowserRepository

/**
 * Implementation of BrowserRepository
 * 
 * Wraps the existing BrowserManager to provide a clean repository interface.
 */
class BrowserRepositoryImpl(
    private val browserManager: BrowserManager
) : BrowserRepository {
    
    override fun createNewTab(isIncognito: Boolean): BrowserTab {
        return browserManager.createNewTab(isIncognito)
    }
    
    override fun getCurrentTab(): BrowserTab? {
        return browserManager.getCurrentTab()
    }
    
    override fun switchToTab(tabId: String) {
        browserManager.switchToTab(tabId)
    }
    
    override fun closeTab(tabId: String) {
        browserManager.closeTab(tabId)
    }
    
    override fun getAllTabs(): List<BrowserTab> {
        return browserManager.getAllTabs()
    }
    
    override fun addBookmark(title: String, url: String) {
        browserManager.addBookmark(title, url)
    }
    
    override fun removeBookmark(bookmarkId: String) {
        browserManager.removeBookmark(bookmarkId)
    }
    
    override fun getAllBookmarks(): List<BrowserBookmark> {
        return browserManager.getAllBookmarks()
    }
    
    override fun addToHistory(title: String, url: String) {
        browserManager.addToHistory(title, url)
    }
    
    override fun clearHistory() {
        browserManager.clearHistory()
    }
    
    override fun getAllHistory(): List<BrowserHistoryItem> {
        return browserManager.getAllHistory()
    }
}

