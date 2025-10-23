package com.myagentos.app.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.myagentos.app.domain.model.ChatMessage
import com.myagentos.app.data.model.ModelType
import com.myagentos.app.R
import com.myagentos.app.presentation.adapter.SimpleChatAdapter
import com.myagentos.app.data.repository.AIRepositoryImpl
import com.myagentos.app.data.repository.ConversationRepositoryImpl
import com.myagentos.app.domain.usecase.DeleteConversationUseCase
import com.myagentos.app.domain.usecase.LoadConversationUseCase
import com.myagentos.app.domain.usecase.SendMessageUseCase
import com.myagentos.app.presentation.viewmodel.ChatViewModel
import com.myagentos.app.presentation.viewmodel.ChatViewModelFactory
import com.myagentos.app.data.manager.ConversationManager
import com.myagentos.app.data.service.ExternalAIService
import kotlinx.coroutines.launch

/**
 * Fragment for Chat functionality
 * 
 * Uses ChatViewModel with manual dependency injection.
 * Observes ViewModel state and updates UI reactively.
 */
class ChatFragment : Fragment() {
    
    // ViewModel with manual DI
    private val viewModel: ChatViewModel by viewModels {
        // Manual DI - create dependencies
        val context = requireContext()
        val conversationManager = ConversationManager(context)
        val conversationRepository = ConversationRepositoryImpl(conversationManager)
        val aiService = ExternalAIService()
        val aiRepository = AIRepositoryImpl(aiService)
        
        val sendMessageUseCase = SendMessageUseCase(conversationRepository, aiRepository)
        val loadConversationUseCase = LoadConversationUseCase(conversationRepository)
        val deleteConversationUseCase = DeleteConversationUseCase(conversationRepository)
        
        ChatViewModelFactory(
            conversationRepository,
            sendMessageUseCase,
            loadConversationUseCase,
            deleteConversationUseCase
        )
    }
    
    // UI Components
    private lateinit var modelSpinner: Spinner
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: FloatingActionButton
    private lateinit var loadingProgress: ProgressBar
    
    // Adapter
    private lateinit var chatAdapter: SimpleChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI(view)
        setupModelSpinner()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }
    
    private fun setupUI(view: View) {
        modelSpinner = view.findViewById(R.id.modelSpinner)
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView)
        messageInput = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.sendButton)
        loadingProgress = view.findViewById(R.id.loadingProgress)
    }
    
    private fun setupModelSpinner() {
        val models = ModelType.values().map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, models)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = adapter
        
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedModel = ModelType.values()[position]
                viewModel.selectModel(selectedModel)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }
    
    private fun setupRecyclerView() {
        chatAdapter = SimpleChatAdapter(messages, isDarkMode = false, showCards = false)
        chatRecyclerView.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
        }
    }
    
    private fun setupListeners() {
        sendButton.setOnClickListener {
            val message = messageInput.text.toString()
            if (message.isNotBlank()) {
                viewModel.sendMessage(message)
                messageInput.text.clear()
            }
        }
    }
    
    private fun observeViewModel() {
        // Observe messages
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collect { newMessages ->
                messages.clear()
                messages.addAll(newMessages)
                chatAdapter.notifyDataSetChanged()
                
                // Scroll to bottom
                if (messages.isNotEmpty()) {
                    chatRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }
        
        // Observe loading state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                loadingProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
                sendButton.isEnabled = !isLoading
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
        
        // Observe selected model
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedModel.collect { model ->
                model?.let {
                    val position = ModelType.values().indexOf(it)
                    if (modelSpinner.selectedItemPosition != position) {
                        modelSpinner.setSelection(position)
                    }
                }
            }
        }
    }
    
    private fun showError(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG).show()
        }
    }
    
    /**
     * Public method to load a conversation
     */
    fun loadConversation(conversationId: Long) {
        viewModel.loadConversation(conversationId)
    }
    
    /**
     * Public method to start a new conversation
     */
    fun startNewConversation() {
        viewModel.startNewConversation()
    }
    
    companion object {
        fun newInstance() = ChatFragment()
    }
}

