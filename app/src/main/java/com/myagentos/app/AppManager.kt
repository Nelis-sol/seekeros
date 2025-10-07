package com.myagentos.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

class AppManager(private val context: Context) {
    
    fun getInstalledApps(): List<AppInfo> {
        val packageManager = context.packageManager
        val installedApps = mutableListOf<AppInfo>()
        
        try {
            // Get all installed packages
            val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            
            for (packageInfo in packages) {
                val appInfo = packageInfo.applicationInfo ?: continue
                
                // Skip our own app
                if (appInfo.packageName == context.packageName) {
                    continue
                }
                
                // Skip apps that are not launchable (no main activity)
                if (packageManager.getLaunchIntentForPackage(appInfo.packageName) == null) {
                    continue
                }
                
                try {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    installedApps.add(
                        AppInfo(
                            packageName = appInfo.packageName,
                            appName = appName,
                            icon = icon,
                            isSystemApp = isSystemApp
                        )
                    )
                } catch (e: Exception) {
                    // Skip apps that can't be loaded (corrupted or missing info)
                    continue
                }
            }
            
            // Sort by app name
            installedApps.sortBy { it.appName }
            
        } catch (e: Exception) {
            android.util.Log.e("AppManager", "Error fetching installed apps", e)
        }
        
        return installedApps
    }
    
    fun launchApp(packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("AppManager", "Error launching app: $packageName", e)
            false
        }
    }
    
    fun getAppIntentCapabilities(packageName: String): List<IntentCapability> {
        val capabilities = mutableListOf<IntentCapability>()
        val packageManager = context.packageManager
        
        try {
            // Common intents to check with their required parameters
            val commonIntents = listOf(
                Intent(Intent.ACTION_SEND).setType("text/plain") to IntentCapability(
                    action = Intent.ACTION_SEND,
                    displayName = "Send Message",
                    icon = R.drawable.ic_send_message,
                    description = "Send text message",
                    parameters = listOf(
                        IntentParameter("recipient", "text", true, "Phone number or username", "+1234567890"),
                        IntentParameter("message", "text", false, "Message text", "Hello!")
                    )
                ),
                Intent(Intent.ACTION_DIAL) to IntentCapability(
                    action = Intent.ACTION_DIAL,
                    displayName = "Call",
                    icon = R.drawable.ic_phone_call,
                    description = "Make phone call",
                    parameters = listOf(
                        IntentParameter("phone_number", "text", true, "Phone number to call", "+1234567890")
                    )
                ),
                Intent(Intent.ACTION_SEND).setType("image/*") to IntentCapability(
                    action = Intent.ACTION_SEND,
                    displayName = "Share Photo",
                    icon = R.drawable.ic_share,
                    description = "Share image",
                    parameters = listOf(
                        IntentParameter("recipient", "text", false, "Recipient (optional)", "John")
                    )
                ),
                Intent(Intent.ACTION_VIEW) to IntentCapability(
                    action = Intent.ACTION_VIEW,
                    displayName = "View",
                    icon = R.drawable.ic_view,
                    description = "View content",
                    parameters = listOf(
                        IntentParameter("url", "text", true, "URL or content to view", "https://www.google.com")
                    )
                ),
                Intent(Intent.ACTION_SEND).setType("*/*") to IntentCapability(
                    action = Intent.ACTION_SEND,
                    displayName = "Share File",
                    icon = R.drawable.ic_share,
                    description = "Share any file",
                    parameters = listOf(
                        IntentParameter("recipient", "text", false, "Recipient (optional)", "John")
                    )
                ),
                Intent(Intent.ACTION_EDIT) to IntentCapability(
                    action = Intent.ACTION_EDIT,
                    displayName = "Edit",
                    icon = R.drawable.ic_view,
                    description = "Edit content",
                    parameters = listOf(
                        IntentParameter("content_uri", "text", true, "Content URI to edit", "content://media/external/images/media/1")
                    )
                ),
                Intent(Intent.ACTION_PICK) to IntentCapability(
                    action = Intent.ACTION_PICK,
                    displayName = "Pick",
                    icon = R.drawable.ic_view,
                    description = "Pick from content",
                    parameters = listOf(
                        IntentParameter("type", "text", false, "Content type (optional)", "image/*")
                    )
                )
            )
            
            for ((intent, capability) in commonIntents) {
                val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                val canHandle = activities.any { it.activityInfo.packageName == packageName }
                
                if (canHandle) {
                    capabilities.add(capability)
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("AppManager", "Error getting intent capabilities for: $packageName", e)
        }
        
        return capabilities
    }
    
    fun launchAppWithIntent(packageName: String, intentCapability: IntentCapability): Boolean {
        return try {
            val intent = when (intentCapability.action) {
                Intent.ACTION_SEND -> {
                    if (intentCapability.displayName.contains("Photo")) {
                        Intent(Intent.ACTION_SEND).setType("image/*")
                    } else if (intentCapability.displayName.contains("File")) {
                        Intent(Intent.ACTION_SEND).setType("*/*")
                    } else {
                        // Send Message - include sample text
                        Intent(Intent.ACTION_SEND).setType("text/plain").apply {
                            putExtra(Intent.EXTRA_TEXT, "Hello! This is a message from AgentOS launcher.")
                            putExtra(Intent.EXTRA_SUBJECT, "Message from AgentOS")
                        }
                    }
                }
                Intent.ACTION_DIAL -> {
                    // For dial intent, we need a phone number
                    Intent(Intent.ACTION_DIAL).apply {
                        data = android.net.Uri.parse("tel:+1234567890") // Sample phone number
                    }
                }
                Intent.ACTION_VIEW -> {
                    // For view intent, we need a URL or content
                    Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://www.google.com") // Sample URL
                    }
                }
                Intent.ACTION_EDIT -> {
                    Intent(Intent.ACTION_EDIT).apply {
                        data = android.net.Uri.parse("content://media/external/images/media/1") // Sample content URI
                    }
                }
                Intent.ACTION_PICK -> {
                    Intent(Intent.ACTION_PICK).apply {
                        type = "image/*" // Pick from images
                    }
                }
                else -> Intent(intentCapability.action)
            }
            
            intent.setPackage(packageName)
            
            // Add flags to ensure we return to our app after the intent completes
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            android.util.Log.e("AppManager", "Error launching app with intent: $packageName", e)
            false
        }
    }
    
    
    fun launchAppWithParameters(packageName: String, capability: IntentCapability, parameters: Map<String, String>): Boolean {
        return try {
            val intent = when (capability.action) {
                Intent.ACTION_SEND -> {
                    if (capability.displayName.contains("Photo")) {
                        Intent(Intent.ACTION_SEND).setType("image/*")
                    } else if (capability.displayName.contains("File")) {
                        Intent(Intent.ACTION_SEND).setType("*/*")
                    } else {
                        // Send Message
                        Intent(Intent.ACTION_SEND).setType("text/plain").apply {
                            parameters["message"]?.let { putExtra(Intent.EXTRA_TEXT, it) }
                            parameters["recipient"]?.let { putExtra(Intent.EXTRA_SUBJECT, "Message to $it") }
                        }
                    }
                }
                Intent.ACTION_DIAL -> {
                    Intent(Intent.ACTION_DIAL).apply {
                        parameters["phone_number"]?.let { 
                            data = android.net.Uri.parse("tel:$it") 
                        }
                    }
                }
                Intent.ACTION_VIEW -> {
                    Intent(Intent.ACTION_VIEW).apply {
                        parameters["url"]?.let { 
                            data = android.net.Uri.parse(it) 
                        }
                    }
                }
                Intent.ACTION_EDIT -> {
                    Intent(Intent.ACTION_EDIT).apply {
                        parameters["content_uri"]?.let { 
                            data = android.net.Uri.parse(it) 
                        }
                    }
                }
                Intent.ACTION_PICK -> {
                    Intent(Intent.ACTION_PICK).apply {
                        parameters["type"]?.let { type = it }
                    }
                }
                else -> Intent(capability.action)
            }
            
            intent.setPackage(packageName)
            
            // Add flags to ensure we return to our app after the intent completes
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            android.util.Log.e("AppManager", "Error launching app with parameters: $packageName", e)
            false
        }
    }
    
    // Enhanced method with callback support
    fun launchAppWithCallback(packageName: String, capability: IntentCapability, parameters: Map<String, String>, activity: android.app.Activity, requestCode: Int = 1001): Boolean {
        return try {
            val intent = when (capability.action) {
                Intent.ACTION_SEND -> {
                    if (capability.displayName.contains("Photo")) {
                        Intent(Intent.ACTION_SEND).setType("image/*")
                    } else if (capability.displayName.contains("File")) {
                        Intent(Intent.ACTION_SEND).setType("*/*")
                    } else {
                        // Send Message
                        Intent(Intent.ACTION_SEND).setType("text/plain").apply {
                            parameters["message"]?.let { putExtra(Intent.EXTRA_TEXT, it) }
                            parameters["recipient"]?.let { putExtra(Intent.EXTRA_SUBJECT, "Message to $it") }
                        }
                    }
                }
                Intent.ACTION_DIAL -> {
                    Intent(Intent.ACTION_DIAL).apply {
                        parameters["phone_number"]?.let { 
                            data = android.net.Uri.parse("tel:$it") 
                        }
                    }
                }
                Intent.ACTION_VIEW -> {
                    Intent(Intent.ACTION_VIEW).apply {
                        parameters["url"]?.let { 
                            data = android.net.Uri.parse(it) 
                        }
                    }
                }
                Intent.ACTION_EDIT -> {
                    Intent(Intent.ACTION_EDIT).apply {
                        parameters["content_uri"]?.let { 
                            data = android.net.Uri.parse(it) 
                        }
                    }
                }
                Intent.ACTION_PICK -> {
                    Intent(Intent.ACTION_PICK).apply {
                        parameters["type"]?.let { type = it }
                    }
                }
                else -> Intent(capability.action)
            }
            
            intent.setPackage(packageName)
            
            // For intents that can return data, use startActivityForResult
            if (capability.action == Intent.ACTION_PICK) {
                activity.startActivityForResult(intent, requestCode)
            } else {
                // Add flags to ensure we return to our app after the intent completes
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                activity.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("AppManager", "Error launching app with callback: $packageName", e)
            false
        }
    }
}
