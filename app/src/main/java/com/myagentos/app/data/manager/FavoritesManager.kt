package com.myagentos.app.data.manager

import android.content.Context
import android.content.SharedPreferences

/**
 * FavoritesManager - Manages favorite MCP apps persistence
 * 
 * Responsibilities:
 * - Save/load favorite MCP app IDs
 * - Add/remove favorites
 * - Check if app is favorited
 * - Notify listeners of changes
 */
class FavoritesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val listeners = mutableListOf<FavoritesListener>()
    
    companion object {
        private const val PREFS_NAME = "agentos_favorites"
        private const val KEY_FAVORITE_MCP_APPS = "favorite_mcp_apps"
    }
    
    /**
     * Listener interface for favorite changes
     */
    interface FavoritesListener {
        fun onFavoritesChanged(favoriteAppIds: Set<String>)
    }
    
    /**
     * Add a listener for favorite changes
     */
    fun addListener(listener: FavoritesListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    /**
     * Remove a listener
     */
    fun removeListener(listener: FavoritesListener) {
        listeners.remove(listener)
    }
    
    /**
     * Get all favorite MCP app IDs
     * NOTE: Returns a defensive copy to avoid SharedPreferences mutation issues
     */
    fun getFavoriteMcpApps(): Set<String> {
        val storedSet = prefs.getStringSet(KEY_FAVORITE_MCP_APPS, null)
        // Create defensive copy to avoid SharedPreferences internal set mutation
        return storedSet?.toSet() ?: emptySet()
    }
    
    /**
     * Check if an MCP app is favorited
     */
    fun isFavorite(appId: String): Boolean {
        return getFavoriteMcpApps().contains(appId)
    }
    
    /**
     * Add an MCP app to favorites
     */
    fun addFavorite(appId: String) {
        val currentFavorites = getFavoriteMcpApps().toMutableSet()
        if (currentFavorites.add(appId)) {
            saveFavorites(currentFavorites)
            android.util.Log.d("FavoritesManager", "Added to favorites: $appId (total: ${currentFavorites.size})")
            notifyListeners(currentFavorites)
        } else {
            android.util.Log.d("FavoritesManager", "Already favorited: $appId")
        }
    }
    
    /**
     * Remove an MCP app from favorites
     */
    fun removeFavorite(appId: String) {
        val currentFavorites = getFavoriteMcpApps().toMutableSet()
        if (currentFavorites.remove(appId)) {
            saveFavorites(currentFavorites)
            android.util.Log.d("FavoritesManager", "Removed from favorites: $appId (remaining: ${currentFavorites.size})")
            notifyListeners(currentFavorites)
        } else {
            android.util.Log.d("FavoritesManager", "Was not favorited: $appId")
        }
    }
    
    /**
     * Toggle favorite status for an MCP app
     * @return true if now favorited, false if unfavorited
     */
    fun toggleFavorite(appId: String): Boolean {
        return if (isFavorite(appId)) {
            removeFavorite(appId)
            false
        } else {
            addFavorite(appId)
            true
        }
    }
    
    /**
     * Clear all favorites
     */
    fun clearAllFavorites() {
        prefs.edit().remove(KEY_FAVORITE_MCP_APPS).apply()
        android.util.Log.d("FavoritesManager", "Cleared all favorites")
        notifyListeners(emptySet())
    }
    
    /**
     * Save favorites to SharedPreferences
     * NOTE: Creates a new HashSet to avoid SharedPreferences mutation issues
     */
    private fun saveFavorites(favorites: Set<String>) {
        // CRITICAL: Always create a new HashSet instance for SharedPreferences
        // This avoids the infamous SharedPreferences StringSet mutation bug
        val newSet = HashSet(favorites)
        
        prefs.edit()
            .remove(KEY_FAVORITE_MCP_APPS)  // Clear first
            .putStringSet(KEY_FAVORITE_MCP_APPS, newSet)
            .commit()  // Use commit() for immediate persistence
        
        android.util.Log.d("FavoritesManager", "Saved favorites: ${newSet.joinToString(", ")}")
    }
    
    /**
     * Notify all listeners of favorite changes
     */
    private fun notifyListeners(favorites: Set<String>) {
        listeners.forEach { it.onFavoritesChanged(favorites) }
    }
}

