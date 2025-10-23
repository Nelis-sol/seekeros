package com.myagentos.app.presentation.viewmodel

import android.webkit.WebView
import com.myagentos.app.BrowserBookmark
import com.myagentos.app.BrowserHistoryItem
import com.myagentos.app.BrowserTab
import com.myagentos.app.domain.repository.BrowserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Unit tests for BrowserViewModel
 * 
 * Tests browser functionality coordination.
 * BrowserRepository itself is already tested (16 unit tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class BrowserViewModelTest {

    @Mock
    private lateinit var mockBrowserRepository: BrowserRepository

    @Mock
    private lateinit var mockWebView: WebView

    private lateinit var viewModel: BrowserViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        // Setup default mocks
        whenever(mockBrowserRepository.getAllTabs()).thenReturn(emptyList())
        whenever(mockBrowserRepository.getAllBookmarks()).thenReturn(emptyList())
        whenever(mockBrowserRepository.getAllHistory()).thenReturn(emptyList())
        
        viewModel = BrowserViewModel(mockBrowserRepository)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads tabs, bookmarks, and history`() = runTest {
        // Given - mocks already set up in setUp()
        
        // Then
        verify(mockBrowserRepository, atLeastOnce()).getAllTabs()
        verify(mockBrowserRepository, atLeastOnce()).getAllBookmarks()
        verify(mockBrowserRepository, atLeastOnce()).getAllHistory()
    }

    @Test
    fun `createNewTab creates tab and updates state`() = runTest {
        // Given
        val newTab = BrowserTab(id = "tab1", title = "New Tab", isIncognito = false, webView = mockWebView)
        whenever(mockBrowserRepository.createNewTab(false)).thenReturn(newTab)
        whenever(mockBrowserRepository.getAllTabs()).thenReturn(listOf(newTab))

        // When
        viewModel.createNewTab()
        advanceUntilIdle()

        // Then
        assertEquals(newTab, viewModel.currentTab.value)
        verify(mockBrowserRepository).createNewTab(false)
    }

    @Test
    fun `createNewTab with incognito flag creates incognito tab`() = runTest {
        // Given
        val incognitoTab = BrowserTab(id = "tab2", title = "Incognito", isIncognito = true, webView = mockWebView)
        whenever(mockBrowserRepository.createNewTab(true)).thenReturn(incognitoTab)

        // When
        viewModel.createNewTab(isIncognito = true)
        advanceUntilIdle()

        // Then
        verify(mockBrowserRepository).createNewTab(true)
    }

    @Test
    fun `switchToTab switches to specified tab`() = runTest {
        // Given
        val tabId = "tab3"
        val tab = BrowserTab(id = tabId, title = "Tab 3", isIncognito = false, webView = mockWebView)
        whenever(mockBrowserRepository.getCurrentTab()).thenReturn(tab)

        // When
        viewModel.switchToTab(tabId)
        advanceUntilIdle()

        // Then
        verify(mockBrowserRepository).switchToTab(tabId)
        assertEquals(tab, viewModel.currentTab.value)
    }

    @Test
    fun `closeTab closes specified tab`() = runTest {
        // Given
        val tabId = "tab4"
        whenever(mockBrowserRepository.getAllTabs()).thenReturn(emptyList())

        // When
        viewModel.closeTab(tabId)
        advanceUntilIdle()

        // Then
        verify(mockBrowserRepository).closeTab(tabId)
    }

    @Test
    fun `navigateToUrl loads URL in current tab`() = runTest {
        // Given
        val url = "https://example.com"
        val tab = BrowserTab(id = "tab1", title = "Tab", isIncognito = false, webView = mockWebView)
        whenever(mockBrowserRepository.getCurrentTab()).thenReturn(tab)

        // When
        viewModel.navigateToUrl(url)
        advanceUntilIdle()

        // Then
        verify(mockWebView).loadUrl(url)
        verify(mockBrowserRepository).addToHistory(url, url)
        assertEquals(url, viewModel.currentUrl.value)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `navigateToUrl shows error when no active tab`() = runTest {
        // Given
        val url = "https://example.com"
        whenever(mockBrowserRepository.getCurrentTab()).thenReturn(null)

        // When
        viewModel.navigateToUrl(url)
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.error.value?.contains("No active tab") == true)
    }

    @Test
    fun `goBack navigates back when possible`() = runTest {
        // Given
        val tab = BrowserTab(id = "tab1", title = "Tab", isIncognito = false, webView = mockWebView)
        whenever(mockBrowserRepository.getCurrentTab()).thenReturn(tab)
        whenever(mockWebView.canGoBack()).thenReturn(true)

        // When
        viewModel.goBack()
        advanceUntilIdle()

        // Then
        verify(mockWebView).goBack()
    }

    @Test
    fun `goBack does nothing when cannot go back`() = runTest {
        // Given
        val tab = BrowserTab(id = "tab1", title = "Tab", isIncognito = false, webView = mockWebView)
        whenever(mockBrowserRepository.getCurrentTab()).thenReturn(tab)
        whenever(mockWebView.canGoBack()).thenReturn(false)

        // When
        viewModel.goBack()
        advanceUntilIdle()

        // Then
        verify(mockWebView, never()).goBack()
    }

    @Test
    fun `goForward navigates forward when possible`() = runTest {
        // Given
        val tab = BrowserTab(id = "tab1", title = "Tab", isIncognito = false, webView = mockWebView)
        whenever(mockBrowserRepository.getCurrentTab()).thenReturn(tab)
        whenever(mockWebView.canGoForward()).thenReturn(true)

        // When
        viewModel.goForward()
        advanceUntilIdle()

        // Then
        verify(mockWebView).goForward()
    }

    @Test
    fun `reload reloads current page`() = runTest {
        // Given
        val tab = BrowserTab(id = "tab1", title = "Tab", isIncognito = false, webView = mockWebView)
        whenever(mockBrowserRepository.getCurrentTab()).thenReturn(tab)

        // When
        viewModel.reload()
        advanceUntilIdle()

        // Then
        verify(mockWebView).reload()
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `addBookmark adds bookmark and refreshes list`() = runTest {
        // Given
        val title = "Example"
        val url = "https://example.com"
        val bookmark = BrowserBookmark(id = "bm1", title = title, url = url)
        whenever(mockBrowserRepository.getAllBookmarks()).thenReturn(listOf(bookmark))

        // When
        viewModel.addBookmark(title, url)
        advanceUntilIdle()

        // Then
        verify(mockBrowserRepository).addBookmark(title, url)
        assertEquals(1, viewModel.bookmarks.value.size)
    }

    @Test
    fun `removeBookmark removes bookmark and refreshes list`() = runTest {
        // Given
        val bookmarkId = "bm2"
        whenever(mockBrowserRepository.getAllBookmarks()).thenReturn(emptyList())

        // When
        viewModel.removeBookmark(bookmarkId)
        advanceUntilIdle()

        // Then
        verify(mockBrowserRepository).removeBookmark(bookmarkId)
        assertTrue(viewModel.bookmarks.value.isEmpty())
    }

    @Test
    fun `clearHistory clears history and refreshes list`() = runTest {
        // Given
        whenever(mockBrowserRepository.getAllHistory()).thenReturn(emptyList())

        // When
        viewModel.clearHistory()
        advanceUntilIdle()

        // Then
        verify(mockBrowserRepository).clearHistory()
        assertTrue(viewModel.history.value.isEmpty())
    }

    @Test
    fun `getCurrentWebView returns current WebView`() {
        // Given
        val tab = BrowserTab(id = "tab1", title = "Tab", isIncognito = false, webView = mockWebView)
        whenever(mockBrowserRepository.getCurrentTab()).thenReturn(tab)

        // When
        val result = viewModel.getCurrentWebView()

        // Then
        assertEquals(mockWebView, result)
    }

    @Test
    fun `clearError clears error state`() {
        // Given - set an error first
        whenever(mockBrowserRepository.getCurrentTab()).thenReturn(null)
        runTest {
            viewModel.navigateToUrl("https://test.com")
            advanceUntilIdle()
            assertNotNull(viewModel.error.value)
        }

        // When
        viewModel.clearError()

        // Then
        assertNull(viewModel.error.value)
    }

    @Test
    fun `createNewTab handles errors gracefully`() = runTest {
        // Given
        whenever(mockBrowserRepository.createNewTab(any()))
            .thenThrow(RuntimeException("Tab creation failed"))

        // When
        viewModel.createNewTab()
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.error.value?.contains("Tab creation failed") == true)
    }
}

