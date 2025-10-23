package com.myagentos.app.util

import com.myagentos.app.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * IntentHelpers - Intent-related utility functions
 * 
 * Responsibilities:
 * - Launch apps
 * - Handle common intents (browser, dialer, etc.)
 * - Intent validation
 * 
 * Extracted from MainActivity to reduce complexity (Phase 4)
 */
object IntentHelpers {
    
    /**
     * Launch an app by package name
     */
    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
                true
            } else {
                Toast.makeText(context, "Could not launch app", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error launching app: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    /**
     * Open URL in browser
     */
    fun openUrl(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open URL", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    /**
     * Open dialer with phone number
     */
    fun openDialer(context: Context, phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$phoneNumber")
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open dialer", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    /**
     * Share text
     */
    fun shareText(context: Context, text: String, title: String = "Share"): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(intent, title))
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    /**
     * Check if app is installed
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Open Play Store for an app
     */
    fun openPlayStore(context: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("market://details?id=$packageName")
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // Fallback to web browser
            openUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
        }
    }
}

