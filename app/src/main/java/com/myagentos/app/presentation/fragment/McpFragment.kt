package com.myagentos.app.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.myagentos.app.data.service.McpService
import com.myagentos.app.data.model.ConnectionStatus
import com.myagentos.app.R
import com.myagentos.app.data.repository.McpRepositoryImpl
import com.myagentos.app.presentation.viewmodel.McpViewModel
import com.myagentos.app.presentation.viewmodel.McpViewModelFactory
import kotlinx.coroutines.launch

/**
 * Fragment for MCP (Model Context Protocol) functionality
 * 
 * Uses McpViewModel with manual dependency injection.
 * Displays MCP apps, connection status, and available tools.
 */
class McpFragment : Fragment() {
    
    // ViewModel with manual DI
    private val viewModel: McpViewModel by viewModels {
        val mcpService = McpService.getInstance()
        val mcpRepository = McpRepositoryImpl(mcpService)
        
        McpViewModelFactory(mcpRepository)
    }
    
    // UI Components
    private lateinit var connectionStatusText: TextView
    private lateinit var mcpAppsRecyclerView: RecyclerView
    private lateinit var toolsLabel: TextView
    private lateinit var toolsRecyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_mcp, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI(view)
        setupRecyclerViews()
        observeViewModel()
    }
    
    private fun setupUI(view: View) {
        connectionStatusText = view.findViewById(R.id.connectionStatusText)
        mcpAppsRecyclerView = view.findViewById(R.id.mcpAppsRecyclerView)
        toolsLabel = view.findViewById(R.id.toolsLabel)
        toolsRecyclerView = view.findViewById(R.id.toolsRecyclerView)
        loadingProgress = view.findViewById(R.id.loadingProgress)
    }
    
    private fun setupRecyclerViews() {
        mcpAppsRecyclerView.layoutManager = LinearLayoutManager(context)
        toolsRecyclerView.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.HORIZONTAL,
            false
        )
    }
    
    private fun observeViewModel() {
        // Observe connection status
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectionStatus.collect { status ->
                updateConnectionStatus(status)
            }
        }
        
        // Observe connected apps
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectedApps.collect { apps ->
                connectionStatusText.text = when {
                    apps.isEmpty() -> "No MCP apps connected"
                    apps.size == 1 -> "1 MCP app connected"
                    else -> "${apps.size} MCP apps connected"
                }
            }
        }
        
        // Observe available tools
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.availableTools.collect { tools ->
                toolsLabel.text = "Available Tools (${tools.size})"
                // TODO: Update tools adapter when implemented
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
                    showError(it)
                    viewModel.clearError()
                }
            }
        }
    }
    
    private fun updateConnectionStatus(status: ConnectionStatus) {
        val statusText = when (status) {
            ConnectionStatus.CONNECTED -> "✓ Connected"
            ConnectionStatus.CONNECTING -> "Connecting..."
            ConnectionStatus.DISCONNECTED -> "Disconnected"
            ConnectionStatus.ERROR -> "Connection Error"
        }
        connectionStatusText.text = statusText
    }
    
    private fun showError(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG).show()
        }
    }
    
    companion object {
        fun newInstance() = McpFragment()
    }
}

