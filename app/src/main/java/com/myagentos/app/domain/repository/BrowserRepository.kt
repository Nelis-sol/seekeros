package com.myagentos.app.domain.repository

import com.myagentos.app.domain.model.BrowserTab
import com.myagentos.app.domain.model.BrowserBookmark
import com.myagentos.app.domain.model.BrowserHistoryItem

/**
 * Repository interface for browser operations
 * 
 * Provides abstraction over browser management, enabling:
 * - Testability (easy to mock)
 * - Flexibility (can swap implementations)
 * - Clean separation of concerns
 */
interface BrowserRepository {
    
    /**
     * Create a new browser tab
     * @param isIncognito Whether this is an incognito tab
     * @return The created BrowserTab
     */
    fun createNewTab(isIncognito: Boolean = false): BrowserTab
    
    /**
     * Get the currently active tab
     * @return Current BrowserTab or null if no active tab
     */
    fun getCurrentTab(): BrowserTab?
    
    /**
     * Switch to a different tab
     * @param tabId The ID of the tab to switch to
     */
    fun switchToTab(tabId: String)
    
    /**
     * Close a tab
     * @param tabId The ID of the tab to close
     */
    fun closeTab(tabId: String)
    
    /**
     * Get all open tabs
     * @return List of all BrowserTabs
     */
    fun getAllTabs(): List<BrowserTab>
    
    /**
     * Add a bookmark
     * @param title The bookmark title
     * @param url The bookmark URL
     */
    fun addBookmark(title: String, url: String)
    
    /**
     * Remove a bookmark
     * @param bookmarkId The ID of the bookmark to remove
     */
    fun removeBookmark(bookmarkId: String)
    
    /**
     * Get all bookmarks
     * @return List of all BrowserBookmarks
     */
    fun getAllBookmarks(): List<BrowserBookmark>
    
    /**
     * Add an item to browser history
     * @param title The page title
     * @param url The page URL
     */
    fun addToHistory(title: String, url: String)
    
    /**
     * Clear all browser history
     */
    fun clearHistory()
    
    /**
     * Get all browser history
     * @return List of all BrowserHistoryItems, most recent first
     */
    fun getAllHistory(): List<BrowserHistoryItem>
}

