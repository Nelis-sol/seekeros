package com.myagentos.app.util

import com.myagentos.app.R

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * UIHelpers - Common UI utility functions
 * 
 * Responsibilities:
 * - Keyboard management
 * - Color utilities
 * - View visibility helpers
 * 
 * Extracted from MainActivity to reduce complexity (Phase 4)
 */
object UIHelpers {
    
    /**
     * Hide keyboard and clear focus from EditText
     */
    fun hideKeyboard(activity: Activity, editText: EditText) {
        editText.clearFocus()
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editText.windowToken, 0)
    }
    
    /**
     * Show keyboard and focus EditText
     */
    fun showKeyboard(activity: Activity, editText: EditText, forced: Boolean = false) {
        editText.requestFocus()
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (forced) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED)
        } else {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    /**
     * Toggle view visibility (VISIBLE <-> GONE)
     */
    fun toggleVisibility(view: View) {
        view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }
    
    /**
     * Set view visible
     */
    fun show(view: View) {
        view.visibility = View.VISIBLE
    }
    
    /**
     * Set view gone
     */
    fun hide(view: View) {
        view.visibility = View.GONE
    }
    
    /**
     * Set view invisible
     */
    fun invisible(view: View) {
        view.visibility = View.INVISIBLE
    }
    
    /**
     * Check if view is visible
     */
    fun isVisible(view: View): Boolean {
        return view.visibility == View.VISIBLE
    }
    
    /**
     * Post action on view with delay
     */
    fun postDelayed(view: View, delayMillis: Long, action: () -> Unit) {
        view.postDelayed(action, delayMillis)
    }
    
    /**
     * Parse color from hex string
     */
    fun parseColor(hexColor: String): Int {
        return try {
            android.graphics.Color.parseColor(hexColor)
        } catch (e: Exception) {
            android.graphics.Color.BLACK
        }
    }
    
    /**
     * Get density-independent pixels (dp) as pixels
     */
    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * Get pixels as density-independent pixels (dp)
     */
    fun pxToDp(context: Context, px: Int): Int {
        return (px / context.resources.displayMetrics.density).toInt()
    }
}

