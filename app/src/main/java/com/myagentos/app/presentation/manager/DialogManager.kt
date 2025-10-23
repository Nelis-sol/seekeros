package com.myagentos.app.presentation.manager
import com.myagentos.app.data.model.ModelType
import com.myagentos.app.data.model.McpApp
import com.myagentos.app.domain.model.IntentCapability
import com.myagentos.app.domain.model.AppInfo

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import com.myagentos.app.*
import com.myagentos.app.R

/**
 * DialogManager - Manages all dialog creation and display
 * 
 * Responsibilities:
 * - Model selection dialog
 * - Permission request dialogs
 * - Parameter input dialogs  
 * - MCP app connection dialogs
 * - Consistent dialog styling
 * 
 * Extracted from MainActivity to reduce complexity (Phase 4 - Step 3)
 */
class DialogManager(private val context: Context) {
    
    /**
     * Show model selection dialog
     */
    fun showModelSelectionDialog(onModelSelected: (ModelType) -> Unit) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_model_selection, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.show()

        val externalModelCard = view.findViewById<CardView>(R.id.externalModelCard)

        externalModelCard.setOnClickListener {
            dialog.dismiss()
            onModelSelected(ModelType.EXTERNAL_GROK)
        }
    }
    
    /**
     * Show MCP app connection dialog
     */
    fun showMcpAppConnectionDialog(
        mcpApp: McpApp,
        onConnect: () -> Unit,
        onShowDetails: () -> Unit
    ) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(mcpApp.name)
        builder.setMessage("${mcpApp.description}\n\nWould you like to connect to this app?")
        builder.setPositiveButton("Connect") { _, _ ->
            onConnect()
        }
        builder.setNeutralButton("Details") { _, _ ->
            onShowDetails()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    /**
     * Show parameter input dialog for intent capabilities
     */
    fun showParameterInputDialog(
        app: AppInfo,
        capability: IntentCapability,
        onExecute: (Map<String, String>) -> Unit
    ) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_parameter_input, null)
        dialog.setContentView(view)
        dialog.show()

        val dialogTitle = view.findViewById<TextView>(R.id.dialogTitle)
        val dialogDescription = view.findViewById<TextView>(R.id.dialogDescription)
        val parametersContainer = view.findViewById<LinearLayout>(R.id.parametersContainer)
        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        val executeButton = view.findViewById<Button>(R.id.executeButton)

        // Set dialog title and description
        dialogTitle.text = capability.displayName
        dialogDescription.text = capability.description

        // Create input fields for each parameter
        val parameterInputs = mutableMapOf<String, EditText>()
        
        for (parameter in capability.parameters) {
            val parameterLayout = LinearLayout(context)
            parameterLayout.orientation = LinearLayout.VERTICAL
            parameterLayout.setPadding(0, 0, 0, 16)

            // Parameter label
            val label = TextView(context)
            label.text = "${parameter.name}${if (parameter.required) " *" else ""}"
            label.setTextColor(android.graphics.Color.WHITE)
            label.textSize = 14f
            label.setPadding(0, 0, 0, 4)

            // Parameter input
            val input = EditText(context)
            input.hint = parameter.example
            input.setTextColor(android.graphics.Color.WHITE)
            input.setHintTextColor(android.graphics.Color.parseColor("#8E8E93"))
            input.background = context.getDrawable(R.drawable.input_background)
            input.setPadding(12, 12, 12, 12)

            // Set default values for common parameters
            when (parameter.name) {
                "message" -> input.setText("Hello! This is a message from AgentOS launcher.")
                "phone_number" -> input.setText("+1234567890")
                "url" -> input.setText("https://www.google.com")
            }

            parameterInputs[parameter.name] = input

            parameterLayout.addView(label)
            parameterLayout.addView(input)
            parametersContainer.addView(parameterLayout)
        }

        // Set up cancel button
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        // Set up execute button
        executeButton.setOnClickListener {
            val parameters = mutableMapOf<String, String>()
            var hasErrors = false

            // Collect parameter values and validate required ones
            for (parameter in capability.parameters) {
                val input = parameterInputs[parameter.name]!!
                val value = input.text.toString().trim()
                
                if (parameter.required && value.isEmpty()) {
                    input.error = "This field is required"
                    hasErrors = true
                } else {
                    parameters[parameter.name] = value
                }
            }

            if (!hasErrors) {
                dialog.dismiss()
                onExecute(parameters)
            }
        }
    }
    
    /**
     * Show usage stats permission request dialog
     */
    fun showUsageStatsPermissionDialog(
        onOpenSettings: () -> Unit,
        onCheckPermission: () -> Unit
    ) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_usage_permission, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.show()
        
        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.dialogMessage)
        val grantButton = view.findViewById<Button>(R.id.grantButton)
        val skipButton = view.findViewById<Button>(R.id.skipButton)
        
        title.text = "Enable App Usage Access"
        message.text = "To show your recently used apps, AgentOS needs permission to access app usage statistics.\n\n1. Tap 'Open Settings' below\n2. Find 'AgentOS' in the list\n3. Toggle the switch to enable\n4. Return to AgentOS"
        
        grantButton.text = "Open Settings"
        grantButton.setOnClickListener {
            dialog.dismiss()
            onOpenSettings()
            
            // Show follow-up dialog
            showPermissionCheckDialog(onCheckPermission)
        }
        
        skipButton.setOnClickListener {
            dialog.dismiss()
        }
    }
    
    /**
     * Show permission check dialog
     */
    fun showPermissionCheckDialog(onCheckPermission: () -> Unit) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_permission_check, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.show()
        
        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.dialogMessage)
        val checkButton = view.findViewById<Button>(R.id.checkButton)
        val skipButton = view.findViewById<Button>(R.id.skipButton)
        
        title.text = "Permission Granted?"
        message.text = "After enabling the permission in Settings, tap 'Check Again' to load your recent apps."
        
        checkButton.setOnClickListener {
            dialog.dismiss()
            onCheckPermission()
        }
        
        skipButton.setOnClickListener {
            dialog.dismiss()
        }
    }
    
    /**
     * Show parameter input dialog for MCP tool invocation
     */
    fun showParameterInputDialog(
        parameterName: String,
        tool: com.myagentos.app.data.model.McpTool,
        onValueProvided: (String) -> Unit
    ) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("${tool.title ?: tool.name}")
            .setMessage("Please provide: $parameterName")
            .setCancelable(false)
            .create()
        
        val inputLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        
        val input = EditText(context).apply {
            hint = parameterName
            setSingleLine(true)
        }
        inputLayout.addView(input)
        
        dialog.setView(inputLayout)
        
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Submit") { _, _ ->
            val value = input.text.toString()
            if (value.isNotEmpty()) {
                onValueProvided(value)
            }
        }
        
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        
        dialog.show()
    }
}

