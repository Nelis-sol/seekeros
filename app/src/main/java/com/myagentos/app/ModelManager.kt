package com.myagentos.app

import android.content.Context
import android.content.SharedPreferences

class ModelManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_MODEL_TYPE = "model_type"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }
    
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }
    
    fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
    
    fun getSelectedModel(): ModelType? {
        val modelString = prefs.getString(KEY_MODEL_TYPE, null)
        return try {
            modelString?.let { ModelType.valueOf(it) }
        } catch (e: IllegalArgumentException) {
            // Handle migration from old LOCAL_LLAMA value
            if (modelString == "LOCAL_LLAMA") {
                // Migrate to Grok as default
                setSelectedModel(ModelType.EXTERNAL_GROK)
                ModelType.EXTERNAL_GROK
            } else {
                null
            }
        }
    }
    
    fun setSelectedModel(modelType: ModelType) {
        prefs.edit().putString(KEY_MODEL_TYPE, modelType.name).apply()
    }
}
