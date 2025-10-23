package com.myagentos.app.presentation.manager

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.myagentos.app.R
import com.myagentos.app.domain.model.Agent
import com.myagentos.app.presentation.activity.AgentProfileActivity

/**
 * AgentManager - Manages agent profiles and displays
 * 
 * Responsibilities:
 * - Launch agent profile activity
 * - Manage agent data
 * - Handle agent profile interactions
 */
class AgentManager(private val context: Context) {
    
    // Sample agent data (will be replaced with database/repository in future)
    private val agents = mutableListOf<Agent>()
    
    init {
        // Initialize with sample agents
        createSampleAgents()
    }
    
    /**
     * Create sample agent data
     */
    private fun createSampleAgents() {
        agents.clear()
        agents.addAll(listOf(
            Agent(
                id = "agent1",
                name = "Agent 1",
                creator = "AgentOS Team",
                description = "A general-purpose AI assistant that helps with everyday tasks, answers questions, and provides helpful suggestions. Always friendly and ready to assist.",
                iconResId = R.drawable.agent_profile_background,
                url = "https://agentos.ai",
                personalityPrompt = "You are a friendly and helpful AI assistant. You approach every task with enthusiasm and patience. You communicate in a clear, concise manner and always try to understand the user's needs.",
                systemPrompt = "You are Agent 1, a helpful AI assistant. Provide accurate, concise responses. When uncertain, say so. Always prioritize user safety and wellbeing.",
                wallet = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb1",
                email = "agent1@agentos.ai"
            ),
            Agent(
                id = "agent2",
                name = "Agent 2",
                creator = "Development Team",
                description = "A specialized coding assistant focused on software development, debugging, and technical problem-solving. Excels at understanding complex systems and providing detailed technical guidance.",
                iconResId = R.drawable.agent_profile_background_2,
                url = "https://agentos.ai/agent2",
                personalityPrompt = "You are a technical expert and coding specialist. You think systematically, break down complex problems, and provide detailed technical explanations. You're passionate about clean code and best practices.",
                systemPrompt = "You are Agent 2, a technical AI assistant specializing in software development. Provide code examples, explain technical concepts clearly, and help debug issues. Use best practices and modern patterns.",
                wallet = "0x8Ba1f109551bD432803012645Ac136ddd64DBA72",
                email = "agent2@agentos.ai"
            ),
            Agent(
                id = "agent3",
                name = "Agent 3",
                creator = "Research Division",
                description = "A creative and analytical agent that specializes in research, data analysis, and generating insights. Perfect for brainstorming, strategic planning, and exploring new ideas.",
                iconResId = R.drawable.agent_profile_background_3,
                url = "https://agentos.ai/agent3",
                personalityPrompt = "You are a creative and analytical thinker. You love exploring ideas from multiple angles, finding patterns, and generating innovative solutions. You're curious, insightful, and encouraging.",
                systemPrompt = "You are Agent 3, a research and analysis AI assistant. Help users explore topics deeply, analyze data, brainstorm ideas, and develop strategic insights. Think creatively and provide well-reasoned perspectives.",
                wallet = "0x4B20993Bc481177ec7E8f571ceCaE8A9e22C02db",
                email = "agent3@agentos.ai"
            )
        ))
    }
    
    /**
     * Get agent by ID
     */
    fun getAgent(agentId: String): Agent? {
        return agents.find { it.id == agentId }
    }
    
    /**
     * Get all agents
     */
    fun getAllAgents(): List<Agent> {
        return agents.toList()
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
    }
    
    /**
     * Update an agent's data
     */
    fun updateAgent(agent: Agent) {
        val index = agents.indexOfFirst { it.id == agent.id }
        if (index != -1) {
            agents[index] = agent
        }
    }
    
    /**
     * Add a new agent
     */
    fun addAgent(agent: Agent) {
        if (agents.none { it.id == agent.id }) {
            agents.add(agent)
        }
    }
    
    /**
     * Remove an agent
     */
    fun removeAgent(agentId: String) {
        agents.removeAll { it.id == agentId }
    }
}

