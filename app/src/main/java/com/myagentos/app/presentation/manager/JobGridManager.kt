package com.myagentos.app.presentation.manager

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.myagentos.app.domain.model.Job
import com.myagentos.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * JobGridManager - Manages job grid overlay, job creation, and execution
 * 
 * Responsibilities:
 * - Job grid overlay display
 * - Job creation, editing, deletion
 * - Job execution with context extraction
 * - Job persistence (SharedPreferences)
 * 
 * Extracted from MainActivity to reduce complexity
 */
class JobGridManager(
    private val context: Context,
    private val jobGridOverlay: View
) {
    
    // State
    private val jobs = mutableListOf<Job>()
    private var isJobGridVisible = false
    
    // Callbacks
    private var onJobExecute: ((Job) -> Unit)? = null
    
    /**
     * Initialize job grid
     */
    fun setup() {
        // Set up click listener for overlay to close grid
        jobGridOverlay.setOnClickListener {
            hide()
        }
        
        // Set up click listener for "New Job" button (slot 1)
        jobGridOverlay.findViewById<View>(R.id.jobSlot1)?.setOnClickListener {
            showCreateJobDialog()
        }
        
        // Load saved jobs
        loadJobs()
        
        android.util.Log.d("JobGridManager", "Job grid initialized with ${jobs.size} jobs")
    }
    
    /**
     * Show job grid overlay
     */
    fun show() {
        if (isJobGridVisible) return
        
        android.util.Log.d("JobGridManager", "Showing job grid")
        
        isJobGridVisible = true
        jobGridOverlay.visibility = View.VISIBLE
        
        // Update job slots display
        updateJobSlots()
    }
    
    /**
     * Hide job grid overlay
     */
    fun hide() {
        if (!isJobGridVisible) return
        
        android.util.Log.d("JobGridManager", "Hiding job grid")
        
        isJobGridVisible = false
        jobGridOverlay.visibility = View.GONE
    }
    
    /**
     * Check if job grid is visible
     */
    fun isVisible(): Boolean = isJobGridVisible
    
    /**
     * Update job slots display
     */
    private fun updateJobSlots() {
        // Show all 6 job slots
        for (i in 0 until 6) {
            val slotId = when(i) {
                0 -> R.id.jobSlot1 // Always "Add New" button
                1 -> R.id.jobSlot2
                2 -> R.id.jobSlot3
                3 -> R.id.jobSlot4
                4 -> R.id.jobSlot5
                5 -> R.id.jobSlot6
                else -> continue
            }
            
            val slot = jobGridOverlay.findViewById<FrameLayout>(slotId) ?: continue
            slot.visibility = View.VISIBLE // Always show all 6 slots
            
            if (i == 0) {
                // First slot is always the "Add New" button - keep original content
                // Don't modify slot 1, it already has the + icon and "New Job" text
            } else if (i - 1 < jobs.size) {
                // Show job
                val job = jobs[i - 1]
                updateJobSlot(slot, job)
            } else {
                // Show empty slot with dashed border
                slot.removeAllViews()
                slot.setOnClickListener {
                    showCreateJobDialog()
                }
                slot.setOnLongClickListener(null)
            }
        }
    }
    
    /**
     * Update individual job slot
     */
    private fun updateJobSlot(slot: FrameLayout, job: Job) {
        slot.removeAllViews()
        
        // Create container for consistent positioning
        val container = FrameLayout(context)
        container.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        // Add icon in center (only if not empty)
        if (job.iconEmoji.isNotEmpty()) {
            val iconText = TextView(context)
            iconText.text = job.iconEmoji
            iconText.textSize = 24f
            iconText.gravity = android.view.Gravity.CENTER
            iconText.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
            container.addView(iconText)
        }
        
        // Add name text at bottom (only if job has a name)
        if (job.name.isNotEmpty()) {
            val nameText = TextView(context)
            nameText.text = job.name
            nameText.textSize = 12f
            nameText.setTextColor(android.graphics.Color.WHITE)
            nameText.gravity = android.view.Gravity.CENTER
            nameText.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = 12
            }
            container.addView(nameText)
        }
        
        slot.addView(container)
        
        // Set click listener to execute the job
        slot.setOnClickListener {
            executeJob(job)
            hide()
        }
        
        // Set long click listener to edit/delete the job
        slot.setOnLongClickListener {
            showJobOptionsDialog(job)
            true
        }
    }
    
    /**
     * Show create job dialog
     */
    private fun showCreateJobDialog() {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_create_job, null)
        dialog.setContentView(view)
        dialog.show()
        
        val jobNameInput = view.findViewById<EditText>(R.id.jobNameInput)
        val jobPromptInput = view.findViewById<EditText>(R.id.jobPromptInput)
        val jobIconInput = view.findViewById<EditText>(R.id.jobIconInput)
        
        view.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }
        
        view.findViewById<Button>(R.id.saveButton).setOnClickListener {
            val name = jobNameInput.text.toString().trim()
            val prompt = jobPromptInput.text.toString().trim()
            val icon = jobIconInput.text.toString().trim().ifEmpty { "🤖" }
            
            if (name.isNotEmpty() && prompt.isNotEmpty()) {
                val job = Job(
                    id = System.currentTimeMillis().toString(),
                    name = name,
                    prompt = prompt,
                    iconEmoji = icon
                )
                jobs.add(job)
                saveJobs()
                updateJobSlots()
                dialog.dismiss()
                Toast.makeText(context, "Job created: $name", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Show job options dialog (edit/delete)
     */
    private fun showJobOptionsDialog(job: Job) {
        val options = arrayOf("Edit Job", "Delete Job", "Cancel")
        
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Job: ${job.name}")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> editJob(job) // Edit
                1 -> deleteJob(job) // Delete
                2 -> dialog.dismiss() // Cancel
            }
        }
        builder.show()
    }
    
    /**
     * Edit existing job
     */
    private fun editJob(job: Job) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_create_job, null)
        dialog.setContentView(view)
        
        // Pre-fill with existing job data
        val jobNameInput = view.findViewById<EditText>(R.id.jobNameInput)
        val jobPromptInput = view.findViewById<EditText>(R.id.jobPromptInput)
        val jobIconInput = view.findViewById<EditText>(R.id.jobIconInput)
        
        jobNameInput.setText(job.name)
        jobPromptInput.setText(job.prompt)
        jobIconInput.setText(job.iconEmoji)
        
        // Change save button text
        val saveButton = view.findViewById<Button>(R.id.saveButton)
        saveButton.text = "Update Job"
        
        dialog.show()
        
        view.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            dialog.dismiss()
        }
        
        saveButton.setOnClickListener {
            val name = jobNameInput.text.toString().trim()
            val prompt = jobPromptInput.text.toString().trim()
            val icon = jobIconInput.text.toString().trim().ifEmpty { "🤖" }
            
            if (name.isNotEmpty() && prompt.isNotEmpty()) {
                // Update the job
                val updatedJob = job.copy(
                    name = name,
                    prompt = prompt,
                    iconEmoji = icon
                )
                
                // Replace in list
                val index = jobs.indexOfFirst { it.id == job.id }
                if (index >= 0) {
                    jobs[index] = updatedJob
                    saveJobs()
                    updateJobSlots()
                    dialog.dismiss()
                    Toast.makeText(context, "Job updated: $name", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Delete job
     */
    private fun deleteJob(job: Job) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Delete Job")
        builder.setMessage("Are you sure you want to delete '${job.name}'?")
        builder.setPositiveButton("Delete") { _, _ ->
            jobs.removeAll { it.id == job.id }
            saveJobs()
            updateJobSlots()
            Toast.makeText(context, "Job deleted: ${job.name}", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    /**
     * Execute job (delegates to callback)
     */
    private fun executeJob(job: Job) {
        android.util.Log.d("JobGridManager", "Executing job: ${job.name}")
        onJobExecute?.invoke(job)
    }
    
    /**
     * Load jobs from SharedPreferences
     */
    private fun loadJobs() {
        val prefs = context.getSharedPreferences("jobs", Context.MODE_PRIVATE)
        val jobsJson = prefs.getString("jobs_list", "[]")
        
        try {
            val jsonArray = JSONArray(jobsJson)
            jobs.clear()
            for (i in 0 until jsonArray.length()) {
                val jobJson = jsonArray.getJSONObject(i)
                val job = Job(
                    id = jobJson.getString("id"),
                    name = jobJson.getString("name"),
                    prompt = jobJson.getString("prompt"),
                    iconEmoji = jobJson.optString("iconEmoji", "🤖"),
                    createdAt = jobJson.optLong("createdAt", System.currentTimeMillis())
                )
                jobs.add(job)
            }
            android.util.Log.d("JobGridManager", "Loaded ${jobs.size} jobs")
            
            // Add default jobs if none exist or if we need to update them
            if (jobs.isEmpty() || shouldUpdateDefaultJobs(prefs)) {
                jobs.clear()
                addDefaultJobs()
            }
        } catch (e: Exception) {
            android.util.Log.e("JobGridManager", "Error loading jobs: ${e.message}")
            addDefaultJobs()
        }
    }
    
    /**
     * Check if default jobs should be updated
     */
    private fun shouldUpdateDefaultJobs(prefs: android.content.SharedPreferences): Boolean {
        val currentVersion = prefs.getInt("jobs_version", 0)
        val targetVersion = 3
        
        if (currentVersion < targetVersion) {
            prefs.edit().putInt("jobs_version", targetVersion).apply()
            return true
        }
        
        return false
    }
    
    /**
     * Add default jobs
     */
    private fun addDefaultJobs() {
        val defaultJobs = listOf(
            Job(
                id = "default_summarize",
                name = "Summarize",
                prompt = "Please provide a concise summary of the content on this screen in 3-4 bullet points. Focus on the key information and main points.",
                iconEmoji = "📝"
            ),
            Job(
                id = "default_chat",
                name = "Chat",
                prompt = "Based on the content on this screen, start a helpful conversation. Ask clarifying questions or provide insights about what you see.",
                iconEmoji = "💬"
            ),
            Job(
                id = "default_explain",
                name = "Explain",
                prompt = "Explain the content on this screen in simple terms. Break down any complex concepts and provide context where needed.",
                iconEmoji = "💡"
            ),
            Job(
                id = "default_context",
                name = "Add to context",
                prompt = "Analyze and remember the content on this screen for future reference. Summarize the key points that might be useful later.",
                iconEmoji = "🧠"
            ),
            Job(
                id = "default_empty",
                name = "",
                prompt = "Analyze the content on this screen and provide any relevant insights or observations.",
                iconEmoji = ""
            )
        )
        
        jobs.addAll(defaultJobs)
        saveJobs()
        android.util.Log.d("JobGridManager", "Added ${defaultJobs.size} default jobs")
    }
    
    /**
     * Save jobs to SharedPreferences
     */
    private fun saveJobs() {
        val prefs = context.getSharedPreferences("jobs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        
        for (job in jobs) {
            val jobJson = JSONObject()
            jobJson.put("id", job.id)
            jobJson.put("name", job.name)
            jobJson.put("prompt", job.prompt)
            jobJson.put("iconEmoji", job.iconEmoji)
            jobJson.put("createdAt", job.createdAt)
            jsonArray.put(jobJson)
        }
        
        prefs.edit().putString("jobs_list", jsonArray.toString()).apply()
        android.util.Log.d("JobGridManager", "Saved ${jobs.size} jobs")
    }
    
    /**
     * Set job execution callback
     */
    fun setOnJobExecute(callback: (Job) -> Unit) {
        onJobExecute = callback
    }
    
    /**
     * Get all jobs
     */
    fun getJobs(): List<Job> = jobs.toList()
}

