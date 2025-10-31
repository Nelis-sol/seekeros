package com.myagentos.app.data.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple storage for wallet connection state
 */
object WalletStorage {
    private const val PREFS_NAME = "wallet_storage"
    private const val KEY_WALLET_ADDRESS = "wallet_address"
    private const val KEY_IS_CONNECTED = "is_connected"
    
    /**
     * Save wallet connection state
     */
    fun saveWalletState(context: Context, address: String, isConnected: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_WALLET_ADDRESS, address)
            .putBoolean(KEY_IS_CONNECTED, isConnected)
            .apply()
    }
    
    /**
     * Get saved wallet address
     */
    fun getWalletAddress(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WALLET_ADDRESS, null)
    }
    
    /**
     * Check if wallet is connected
     */
    fun isWalletConnected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_CONNECTED, false)
    }
    
    /**
     * Clear wallet state (disconnect)
     */
    fun clearWalletState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_WALLET_ADDRESS)
            .putBoolean(KEY_IS_CONNECTED, false)
            .apply()
    }
}
