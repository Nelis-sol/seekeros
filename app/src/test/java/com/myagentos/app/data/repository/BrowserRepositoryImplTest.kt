package com.myagentos.app.data.repository

import com.myagentos.app.BrowserManager
import com.myagentos.app.BrowserTab
import com.myagentos.app.BrowserBookmark
import com.myagentos.app.BrowserHistoryItem
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*
import org.mockito.kotlin.any

/**
 * Unit tests for BrowserRepositoryImpl
 */
@RunWith(MockitoJUnitRunner::class)
class BrowserRepositoryImplTest {

    @Mock
    private lateinit var mockBrowserManager: BrowserManager

    @Mock
    private lateinit var mockWebView: android.webkit.WebView

    private lateinit var repository: BrowserRepositoryImpl

    @Before
    fun setUp() {
        repository = BrowserRepositoryImpl(mockBrowserManager)
    }

    @Test
    fun `createNewTab creates regular tab`() {
        // Given
        val expectedTab = BrowserTab("tab1", mockWebView, isIncognito = false, isActive = true)
        `when`(mockBrowserManager.createNewTab(false)).thenReturn(expectedTab)

        // When
        val result = repository.createNewTab(false)

        // Then
        assertEquals(expectedTab, result)
        assertFalse(result.isIncognito)
        verify(mockBrowserManager).createNewTab(false)
    }

    @Test
    fun `createNewTab creates incognito tab`() {
        // Given
        val expectedTab = BrowserTab("tab2", mockWebView, isIncognito = true, isActive = true)
        `when`(mockBrowserManager.createNewTab(true)).thenReturn(expectedTab)

        // When
        val result = repository.createNewTab(true)

        // Then
        assertEquals(expectedTab, result)
        assertTrue(result.isIncognito)
        verify(mockBrowserManager).createNewTab(true)
    }

    @Test
    fun `getCurrentTab returns active tab`() {
        // Given
        val tab = BrowserTab("active", mockWebView, isIncognito = false, isActive = true)
        `when`(mockBrowserManager.getCurrentTab()).thenReturn(tab)

        // When
        val result = repository.getCurrentTab()

        // Then
        assertEquals(tab, result)
    }

    @Test
    fun `getCurrentTab returns null when no active tab`() {
        // Given
        `when`(mockBrowserManager.getCurrentTab()).thenReturn(null)

        // When
        val result = repository.getCurrentTab()

        // Then
        assertNull(result)
    }

    @Test
    fun `switchToTab delegates to manager`() {
        // Given
        val tabId = "tab123"

        // When
        repository.switchToTab(tabId)

        // Then
        verify(mockBrowserManager).switchToTab(tabId)
    }

    @Test
    fun `closeTab delegates to manager`() {
        // Given
        val tabId = "tab456"

        // When
        repository.closeTab(tabId)

        // Then
        verify(mockBrowserManager).closeTab(tabId)
    }

    @Test
    fun `getAllTabs returns list of tabs`() {
        // Given
        val tabs = listOf(
            BrowserTab("tab1", mockWebView, isIncognito = false, isActive = true),
            BrowserTab("tab2", mockWebView, isIncognito = false, isActive = false)
        )
        `when`(mockBrowserManager.getAllTabs()).thenReturn(tabs)

        // When
        val result = repository.getAllTabs()

        // Then
        assertEquals(2, result.size)
        assertEquals(tabs, result)
    }

    @Test
    fun `getAllTabs returns empty list when no tabs`() {
        // Given
        `when`(mockBrowserManager.getAllTabs()).thenReturn(emptyList())

        // When
        val result = repository.getAllTabs()

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `addBookmark delegates to manager`() {
        // Given
        val title = "Google"
        val url = "https://google.com"

        // When
        repository.addBookmark(title, url)

        // Then
        verify(mockBrowserManager).addBookmark(title, url)
    }

    @Test
    fun `removeBookmark delegates to manager`() {
        // Given
        val bookmarkId = "bookmark123"

        // When
        repository.removeBookmark(bookmarkId)

        // Then
        verify(mockBrowserManager).removeBookmark(bookmarkId)
    }

    @Test
    fun `getAllBookmarks returns list of bookmarks`() {
        // Given
        val bookmarks = listOf(
            BrowserBookmark("1", "Google", "https://google.com"),
            BrowserBookmark("2", "GitHub", "https://github.com")
        )
        `when`(mockBrowserManager.getAllBookmarks()).thenReturn(bookmarks)

        // When
        val result = repository.getAllBookmarks()

        // Then
        assertEquals(2, result.size)
        assertEquals(bookmarks, result)
    }

    @Test
    fun `addToHistory delegates to manager`() {
        // Given
        val title = "Example Page"
        val url = "https://example.com"

        // When
        repository.addToHistory(title, url)

        // Then
        verify(mockBrowserManager).addToHistory(title, url)
    }

    @Test
    fun `clearHistory delegates to manager`() {
        // When
        repository.clearHistory()

        // Then
        verify(mockBrowserManager).clearHistory()
    }

    @Test
    fun `getAllHistory returns list of history items`() {
        // Given
        val history = listOf(
            BrowserHistoryItem("1", "Page 1", "https://page1.com"),
            BrowserHistoryItem("2", "Page 2", "https://page2.com")
        )
        `when`(mockBrowserManager.getAllHistory()).thenReturn(history)

        // When
        val result = repository.getAllHistory()

        // Then
        assertEquals(2, result.size)
        assertEquals(history, result)
    }

    @Test
    fun `getAllHistory returns empty list when no history`() {
        // Given
        `when`(mockBrowserManager.getAllHistory()).thenReturn(emptyList())

        // When
        val result = repository.getAllHistory()

        // Then
        assertTrue(result.isEmpty())
    }
}

