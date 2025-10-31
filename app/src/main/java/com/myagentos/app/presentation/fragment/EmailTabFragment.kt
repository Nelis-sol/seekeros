package com.myagentos.app.presentation.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.myagentos.app.R

/**
 * Email tab fragment for agent profile
 */
class EmailTabFragment : Fragment() {
    
    private lateinit var emailAddressInbox: TextView
    private lateinit var generateEmailInboxButton: Button
    private lateinit var refreshEmailsButton: Button
    private lateinit var noEmailsText: TextView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.tab_email, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        emailAddressInbox = view.findViewById(R.id.emailAddressInbox)
        generateEmailInboxButton = view.findViewById(R.id.generateEmailInboxButton)
        refreshEmailsButton = view.findViewById(R.id.refreshEmailsButton)
        noEmailsText = view.findViewById(R.id.noEmailsText)
        
        // Set up click listeners
        generateEmailInboxButton.setOnClickListener {
            generateEmail()
        }
        
        refreshEmailsButton.setOnClickListener {
            refreshEmails()
        }
        
        // Set up copy functionality for email address
        emailAddressInbox.setOnClickListener {
            if (emailAddressInbox.text.toString() != "Not generated") {
                copyToClipboard("Email Address", emailAddressInbox.text.toString())
            }
        }
        
        // Check if email is already generated from parent activity
        checkEmailStatus()
    }
    
    /**
     * Check if email is already generated from parent activity
     */
    private fun checkEmailStatus() {
        val activity = requireActivity() as? com.myagentos.app.presentation.activity.AgentProfileActivity
        activity?.let {
            // Check if wallet is connected (email generation requires wallet)
            if (it.isWalletConnected()) {
                generateEmailInboxButton.isEnabled = true
            }
        }
    }
    
    /**
     * Generate email address for the agent
     */
    private fun generateEmail() {
        val activity = requireActivity() as? com.myagentos.app.presentation.activity.AgentProfileActivity
        val agentName = activity?.intent?.getStringExtra("agent_name") ?: "agent"
        val agentNameLower = agentName.lowercase().replace(" ", ".")
        val generatedEmail = "$agentNameLower@agentos.ai"
        
        // Update UI to show email is generated
        emailAddressInbox.text = generatedEmail
        emailAddressInbox.visibility = View.VISIBLE
        
        // Hide generate email button, show refresh button
        generateEmailInboxButton.visibility = View.GONE
        refreshEmailsButton.isEnabled = true
        
        // Hide no emails text
        noEmailsText.visibility = View.GONE
        
        Toast.makeText(requireContext(), "Email generated: $generatedEmail", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Refresh email inbox
     */
    private fun refreshEmails() {
        Toast.makeText(requireContext(), "Refreshing inbox...", Toast.LENGTH_SHORT).show()
        // In real implementation, this would fetch emails from the email service
        // For now, we'll just show a success message
        Toast.makeText(requireContext(), "Inbox refreshed", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Copy text to clipboard
     */
    private fun copyToClipboard(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
