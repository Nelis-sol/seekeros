package com.myagentos.app.presentation.manager

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.myagentos.app.R
import com.myagentos.app.data.local.database.AgentOSDatabase
import com.myagentos.app.data.local.entity.UserAgent
import com.myagentos.app.domain.model.Agent
import com.myagentos.app.presentation.activity.AgentProfileActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * AgentManager - Manages agent profiles and displays
 * 
 * Responsibilities:
 * - Launch agent profile activity
 * - Manage agent data (default + user-created)
 * - Handle agent profile interactions
 */
class AgentManager(private val context: Context) {
    
    private val database by lazy { AgentOSDatabase.getDatabase(context) }
    private val defaultAgent1 = createDefaultAgent()
    
    init {
        // Default Agent 1 is always available
    }
    
    /**
     * Create default Agent 1 (always available)
     */
    private fun createDefaultAgent(): Agent {
        return Agent(
            id = "agent1",
            name = "Agent 1",
            creator = "AgentOS Team",
            description = "A general-purpose AI assistant that helps with everyday tasks, answers questions, and provides helpful suggestions. Always friendly and ready to assist.",
            iconResId = R.drawable.agent_profile_background,
            url = null,
            personalityPrompt = "You are a friendly and helpful AI assistant. You approach every task with enthusiasm and patience. You communicate in a clear, concise manner and always try to understand the user's needs.",
            systemPrompt = "You are Agent 1, a helpful AI assistant. Provide accurate, concise responses. When uncertain, say so. Always prioritize user safety and wellbeing.",
            wallet = null,
            email = null
        )
    }
    
    /**
     * Convert UserAgent entity to domain Agent model
     */
    private fun UserAgent.toAgent(): Agent {
        return Agent(
            id = this.id,
            name = this.name,
            creator = this.creator,
            description = this.description,
            iconResId = if (this.iconResId != 0) this.iconResId else R.drawable.agent_profile_background,
            url = null,
            personalityPrompt = this.personalityPrompt,
            systemPrompt = this.systemPrompt,
            wallet = null,
            email = null
        )
    }
    
    /**
     * Get all agents as Flow (default + user-created)
     */
    fun getAllAgentsFlow(): Flow<List<Agent>> {
        return database.userAgentDao().getAllAgents().map { userAgents ->
            listOf(defaultAgent1) + userAgents.map { it.toAgent() }
        }
    }
    
    /**
     * Get agent by ID (checks default first, then database)
     */
    suspend fun getAgent(agentId: String): Agent? {
        if (agentId == "agent1") {
            return defaultAgent1
        }
        return database.userAgentDao().getAgentById(agentId)?.toAgent()
    }
    
    /**
     * Show agent profile in full-screen activity
     */
    fun showAgentProfile(agent: Agent) {
        val intent = Intent(context, AgentProfileActivity::class.java).apply {
            putExtra("agent_id", agent.id)
            putExtra("agent_name", agent.name)
            putExtra("agent_creator", agent.creator)
            putExtra("agent_description", agent.description)
            putExtra("agent_website", agent.url)
            putExtra("agent_personality_prompt", agent.personalityPrompt)
            putExtra("agent_system_prompt", agent.systemPrompt)
            agent.iconResId?.let { putExtra("agent_icon_res_id", it) }
        }
        
        // Start activity for result if context is an Activity
        if (context is Activity) {
            context.startActivityForResult(intent, REQUEST_CODE_AGENT_PROFILE)
        } else {
            context.startActivity(intent)
        }
    }
    
    companion object {
        const val REQUEST_CODE_AGENT_PROFILE = 1002
        const val REQUEST_CODE_CREATE_AGENT = 1003
    }
}

