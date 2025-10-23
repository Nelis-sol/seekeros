package com.myagentos.app.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.myagentos.app.data.manager.BrowserManager
import com.myagentos.app.R
import com.myagentos.app.data.repository.BrowserRepositoryImpl
import com.myagentos.app.presentation.viewmodel.BrowserViewModel
import com.myagentos.app.presentation.viewmodel.BrowserViewModelFactory
import kotlinx.coroutines.launch

/**
 * Fragment for Browser functionality
 * 
 * Uses BrowserViewModel with manual dependency injection.
 * Simplified version - can be enhanced with full browser features.
 */
class BrowserFragment : Fragment() {
    
    // ViewModel with manual DI
    private val viewModel: BrowserViewModel by viewModels {
        val context = requireContext()
        val browserManager = BrowserManager(context)
        val browserRepository = BrowserRepositoryImpl(browserManager)
        
        BrowserViewModelFactory(browserRepository)
    }
    
    // UI Components
    private lateinit var urlInput: EditText
    private lateinit var goButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var reloadButton: ImageButton
    private lateinit var bookmarkButton: ImageButton
    private lateinit var tabsButton: ImageButton
    private lateinit var webViewContainer: FrameLayout
    private lateinit var loadingProgress: ProgressBar
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_browser, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI(view)
        setupListeners()
        observeViewModel()
        
        // Create initial tab
        viewModel.createNewTab()
    }
    
    private fun setupUI(view: View) {
        urlInput = view.findViewById(R.id.urlInput)
        goButton = view.findViewById(R.id.goButton)
        backButton = view.findViewById(R.id.backButton)
        forwardButton = view.findViewById(R.id.forwardButton)
        reloadButton = view.findViewById(R.id.reloadButton)
        bookmarkButton = view.findViewById(R.id.bookmarkButton)
        tabsButton = view.findViewById(R.id.tabsButton)
        webViewContainer = view.findViewById(R.id.webViewContainer)
        loadingProgress = view.findViewById(R.id.loadingProgress)
    }
    
    private fun setupListeners() {
        goButton.setOnClickListener {
            val url = urlInput.text.toString()
            if (url.isNotBlank()) {
                viewModel.navigateToUrl(if (url.startsWith("http")) url else "https://$url")
            }
        }
        
        urlInput.setOnEditorActionListener { _, _, _ ->
            goButton.performClick()
            true
        }
        
        backButton.setOnClickListener { viewModel.goBack() }
        forwardButton.setOnClickListener { viewModel.goForward() }
        reloadButton.setOnClickListener { viewModel.reload() }
        
        bookmarkButton.setOnClickListener {
            val url = viewModel.currentUrl.value
            if (url.isNotBlank()) {
                viewModel.addBookmark(url, url)
                showMessage("Bookmark added")
            }
        }
        
        tabsButton.setOnClickListener {
            showMessage("Tabs: ${viewModel.allTabs.value.size}")
        }
    }
    
    private fun observeViewModel() {
        // Observe current URL
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentUrl.collect { url ->
                if (urlInput.text.toString() != url) {
                    urlInput.setText(url)
                }
            }
        }
        
        // Observe loading state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                loadingProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
        
        // Observe errors
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    showMessage(it)
                    viewModel.clearError()
                }
            }
        }
        
        // Observe current tab and add WebView
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentTab.collect { tab ->
                webViewContainer.removeAllViews()
                tab?.webView?.let { webView ->
                    webViewContainer.addView(webView)
                }
            }
        }
    }
    
    private fun showMessage(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }
    
    companion object {
        fun newInstance() = BrowserFragment()
    }
}

