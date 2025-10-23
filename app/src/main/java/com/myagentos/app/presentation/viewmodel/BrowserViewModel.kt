package com.myagentos.app.presentation.viewmodel

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myagentos.app.domain.model.BrowserBookmark
import com.myagentos.app.domain.model.BrowserHistoryItem
import com.myagentos.app.domain.model.BrowserTab
import com.myagentos.app.domain.repository.BrowserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Browser functionality
 * 
 * Manages browser state and coordinates between the UI layer and BrowserRepository.
 * Handles tabs, navigation, bookmarks, and history.
 * 
 * State Management:
 * - currentTab: Currently active tab
 * - allTabs: List of all open tabs
 * - bookmarks: List of saved bookmarks
 * - history: Browser history
 * - currentUrl: URL of current page
 * - isLoading: Whether page is loading
 * - error: Error message if something went wrong
 */
class BrowserViewModel(
    private val browserRepository: BrowserRepository
) : ViewModel() {
    
    // UI State
    private val _currentTab = MutableStateFlow<BrowserTab?>(null)
    val currentTab: StateFlow<BrowserTab?> = _currentTab.asStateFlow()
    
    private val _allTabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val allTabs: StateFlow<List<BrowserTab>> = _allTabs.asStateFlow()
    
    private val _bookmarks = MutableStateFlow<List<BrowserBookmark>>(emptyList())
    val bookmarks: StateFlow<List<BrowserBookmark>> = _bookmarks.asStateFlow()
    
    private val _history = MutableStateFlow<List<BrowserHistoryItem>>(emptyList())
    val history: StateFlow<List<BrowserHistoryItem>> = _history.asStateFlow()
    
    private val _currentUrl = MutableStateFlow<String>("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    init {
        // Load initial state
        loadTabs()
        loadBookmarks()
        loadHistory()
    }
    
    /**
     * Create a new tab
     */
    fun createNewTab(isIncognito: Boolean = false) {
        viewModelScope.launch {
            try {
                val newTab = browserRepository.createNewTab(isIncognito)
                _currentTab.value = newTab
                loadTabs()
            } catch (e: Exception) {
                _error.value = "Failed to create tab: ${e.message}"
            }
        }
    }
    
    /**
     * Switch to a different tab
     */
    fun switchToTab(tabId: String) {
        viewModelScope.launch {
            try {
                browserRepository.switchToTab(tabId)
                _currentTab.value = browserRepository.getCurrentTab()
                updateCurrentUrl()
            } catch (e: Exception) {
                _error.value = "Failed to switch tab: ${e.message}"
            }
        }
    }
    
    /**
     * Close a tab
     */
    fun closeTab(tabId: String) {
        viewModelScope.launch {
            try {
                browserRepository.closeTab(tabId)
                _currentTab.value = browserRepository.getCurrentTab()
                loadTabs()
                updateCurrentUrl()
            } catch (e: Exception) {
                _error.value = "Failed to close tab: ${e.message}"
            }
        }
    }
    
    /**
     * Navigate to URL
     */
    fun navigateToUrl(url: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val currentTab = browserRepository.getCurrentTab()
                if (currentTab?.webView != null) {
                    currentTab.webView.loadUrl(url)
                    _currentUrl.value = url
                    // Add to history
                    browserRepository.addToHistory(url, url)
                } else {
                    _error.value = "No active tab"
                }
            } catch (e: Exception) {
                _error.value = "Failed to navigate: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Go back in history
     */
    fun goBack() {
        viewModelScope.launch {
            try {
                val webView = browserRepository.getCurrentTab()?.webView
                if (webView?.canGoBack() == true) {
                    webView.goBack()
                    updateCurrentUrl()
                }
            } catch (e: Exception) {
                _error.value = "Failed to go back: ${e.message}"
            }
        }
    }
    
    /**
     * Go forward in history
     */
    fun goForward() {
        viewModelScope.launch {
            try {
                val webView = browserRepository.getCurrentTab()?.webView
                if (webView?.canGoForward() == true) {
                    webView.goForward()
                    updateCurrentUrl()
                }
            } catch (e: Exception) {
                _error.value = "Failed to go forward: ${e.message}"
            }
        }
    }
    
    /**
     * Reload current page
     */
    fun reload() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val webView = browserRepository.getCurrentTab()?.webView
                webView?.reload()
            } catch (e: Exception) {
                _error.value = "Failed to reload: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Add current page to bookmarks
     */
    fun addBookmark(title: String, url: String) {
        viewModelScope.launch {
            try {
                browserRepository.addBookmark(title, url)
                loadBookmarks()
            } catch (e: Exception) {
                _error.value = "Failed to add bookmark: ${e.message}"
            }
        }
    }
    
    /**
     * Remove a bookmark
     */
    fun removeBookmark(bookmarkId: String) {
        viewModelScope.launch {
            try {
                browserRepository.removeBookmark(bookmarkId)
                loadBookmarks()
            } catch (e: Exception) {
                _error.value = "Failed to remove bookmark: ${e.message}"
            }
        }
    }
    
    /**
     * Clear browser history
     */
    fun clearHistory() {
        viewModelScope.launch {
            try {
                browserRepository.clearHistory()
                loadHistory()
            } catch (e: Exception) {
                _error.value = "Failed to clear history: ${e.message}"
            }
        }
    }
    
    /**
     * Get WebView for current tab
     */
    fun getCurrentWebView(): WebView? {
        return browserRepository.getCurrentTab()?.webView
    }
    
    /**
     * Clear error
     */
    fun clearError() {
        _error.value = null
    }
    
    // Private helper methods
    
    private fun loadTabs() {
        viewModelScope.launch {
            try {
                _allTabs.value = browserRepository.getAllTabs()
            } catch (e: Exception) {
                _error.value = "Failed to load tabs: ${e.message}"
            }
        }
    }
    
    private fun loadBookmarks() {
        viewModelScope.launch {
            try {
                _bookmarks.value = browserRepository.getAllBookmarks()
            } catch (e: Exception) {
                _error.value = "Failed to load bookmarks: ${e.message}"
            }
        }
    }
    
    private fun loadHistory() {
        viewModelScope.launch {
            try {
                _history.value = browserRepository.getAllHistory()
            } catch (e: Exception) {
                _error.value = "Failed to load history: ${e.message}"
            }
        }
    }
    
    private fun updateCurrentUrl() {
        val webView = browserRepository.getCurrentTab()?.webView
        _currentUrl.value = webView?.url ?: ""
    }
}

/**
 * Factory for creating BrowserViewModel with dependencies
 */
class BrowserViewModelFactory(
    private val browserRepository: BrowserRepository
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BrowserViewModel::class.java)) {
            return BrowserViewModel(browserRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

