package com.myagentos.app.presentation.activity

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.myagentos.app.R
import com.myagentos.app.data.local.database.AgentOSDatabase
import com.myagentos.app.data.local.entity.UserAgent
import kotlinx.coroutines.launch
import java.util.UUID

class AgentCreationActivity : AppCompatActivity() {
    
    private lateinit var backButton: Button
    private lateinit var saveButton: Button
    private lateinit var agentNameInput: EditText
    private lateinit var agentCreatorInput: EditText
    private lateinit var agentDescriptionInput: EditText
    private lateinit var personalityPromptInput: EditText
    private lateinit var systemPromptInput: EditText
    
    private val database by lazy { AgentOSDatabase.getDatabase(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_creation)
        
        // Initialize views
        backButton = findViewById(R.id.backButton)
        saveButton = findViewById(R.id.saveButton)
        agentNameInput = findViewById(R.id.agentNameInput)
        agentCreatorInput = findViewById(R.id.agentCreatorInput)
        agentDescriptionInput = findViewById(R.id.agentDescriptionInput)
        personalityPromptInput = findViewById(R.id.personalityPromptInput)
        systemPromptInput = findViewById(R.id.systemPromptInput)
        
        // Set up button listeners
        backButton.setOnClickListener {
            finish()
        }
        
        saveButton.setOnClickListener {
            saveAgent()
        }
    }
    
    private fun saveAgent() {
        // Get input values
        val name = agentNameInput.text.toString().trim()
        val creator = agentCreatorInput.text.toString().trim()
        val description = agentDescriptionInput.text.toString().trim()
        val personalityPrompt = personalityPromptInput.text.toString().trim()
        val systemPrompt = systemPromptInput.text.toString().trim()
        
        // Validate required fields
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter an agent name", Toast.LENGTH_SHORT).show()
            agentNameInput.requestFocus()
            return
        }
        
        if (creator.isEmpty()) {
            Toast.makeText(this, "Please enter creator name", Toast.LENGTH_SHORT).show()
            agentCreatorInput.requestFocus()
            return
        }
        
        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show()
            agentDescriptionInput.requestFocus()
            return
        }
        
        if (personalityPrompt.isEmpty()) {
            Toast.makeText(this, "Please enter a personality prompt", Toast.LENGTH_SHORT).show()
            personalityPromptInput.requestFocus()
            return
        }
        
        if (systemPrompt.isEmpty()) {
            Toast.makeText(this, "Please enter a system prompt", Toast.LENGTH_SHORT).show()
            systemPromptInput.requestFocus()
            return
        }
        
        // Create new agent
        val newAgent = UserAgent(
            id = UUID.randomUUID().toString(),
            name = name,
            creator = creator,
            description = description,
            personalityPrompt = personalityPrompt,
            systemPrompt = systemPrompt,
            iconResId = R.drawable.agent_profile_background,
            createdAt = System.currentTimeMillis(),
            isDefault = false
        )
        
        // Save to database
        lifecycleScope.launch {
            try {
                database.userAgentDao().insertAgent(newAgent)
                Toast.makeText(this@AgentCreationActivity, "Agent created successfully!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AgentCreationActivity, "Error creating agent: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

