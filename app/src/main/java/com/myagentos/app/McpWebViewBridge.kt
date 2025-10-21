package com.myagentos.app

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.util.Locale

/**
 * MCP WebView Bridge - JavaScript bridge for MCP web components
 * 
 * Implements the window.openai JavaScript API for data injection
 * and bidirectional communication between native Android and web components.
 * 
 * Based on OpenAI's Apps SDK reference:
 * https://developers.openai.com/apps-sdk/reference
 * 
 * JavaScript API exposed:
 * - window.openai.global - Structured data from tool results
 * - window.openai._meta - Metadata from tool results
 * - window.openai.locale - Client locale
 * - window.openai.userAgent - Client user agent
 * - window.AgentOS.invokeAction() - Callback for actions
 */
class McpWebViewBridge(
    private val webView: WebView,
    private val onAction: (actionName: String, arguments: Map<String, Any>) -> Unit
) {
    
    companion object {
        private const val TAG = "McpWebViewBridge"
    }
    
    /**
     * Inject structured data into web component
     * 
     * Sets window.openai.global with the structured content
     * and window.openai._meta with metadata
     * 
     * @param structuredContent JSON data from tool result
     * @param metadata Metadata from tool result (_meta field)
     */
    fun injectData(structuredContent: JSONObject, metadata: Map<String, Any>?) {
        try {
            Log.d(TAG, "Injecting data into web component")
            Log.d(TAG, "Structured content: $structuredContent")
            Log.d(TAG, "Metadata: $metadata")
            
            // Build window.openai object with data
            val metadataJson = metadata?.let { JSONObject(it).toString() } ?: "{}"
            
            val script = """
                (function() {
                    // Initialize window.openai if not exists
                    window.openai = window.openai || {};
                    
                    // Inject structured content as global data
                    window.openai.global = $structuredContent;
                    
                    // Inject metadata
                    window.openai._meta = $metadataJson;
                    
                    // Log for debugging
                    console.log('AgentOS: Data injected into window.openai');
                    console.log('AgentOS: global =', window.openai.global);
                    console.log('AgentOS: _meta =', window.openai._meta);
                    
                    // Dispatch event to notify component that data is ready
                    window.dispatchEvent(new Event('agentos-data-ready'));
                })();
            """.trimIndent()
            
            webView.post {
                webView.evaluateJavascript(script) { result ->
                    Log.d(TAG, "Data injection result: $result")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting data: ${e.message}", e)
        }
    }
    
    /**
     * Set client metadata (locale, user agent, etc.)
     * 
     * Sets window.openai.locale and window.openai.userAgent
     * for client-to-server metadata communication
     * 
     * @param locale Client locale (e.g., "en-US")
     * @param userAgent Client user agent string
     */
    fun setClientMetadata(locale: String, userAgent: String) {
        try {
            Log.d(TAG, "Setting client metadata: locale=$locale")
            
            val script = """
                (function() {
                    window.openai = window.openai || {};
                    window.openai.locale = "$locale";
                    window.openai.userAgent = "$userAgent";
                    console.log('AgentOS: Client metadata set');
                })();
            """.trimIndent()
            
            webView.post {
                webView.evaluateJavascript(script, null)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting client metadata: ${e.message}", e)
        }
    }
    
    /**
     * Initialize the bridge when WebView is first loaded
     * 
     * Sets up:
     * - window.openai base object
     * - window.openai.displayMode
     * - Helper functions for React components
     */
    fun initialize() {
        try {
            Log.d(TAG, "Initializing WebView bridge")
            
            val locale = Locale.getDefault().toLanguageTag()
            val userAgent = "AgentOS/1.0.0 (Android)"
            
            val script = """
                (function() {
                    // Initialize window.openai
                    window.openai = window.openai || {};
                    
                    // Set initial metadata
                    window.openai.locale = "$locale";
                    window.openai.userAgent = "$userAgent";
                    
                    // Set display mode (fullscreen for Android)
                    window.openai.displayMode = "fullscreen";
                    
                    // Helper function for React components to access global data
                    window.useOpenaiGlobal = function(key) {
                        if (window.openai && window.openai.global) {
                            return window.openai.global[key];
                        }
                        return undefined;
                    };
                    
                    // Log initialization
                    console.log('AgentOS: Bridge initialized');
                    console.log('AgentOS: locale =', window.openai.locale);
                    console.log('AgentOS: displayMode =', window.openai.displayMode);
                })();
            """.trimIndent()
            
            webView.post {
                webView.evaluateJavascript(script) { result ->
                    Log.d(TAG, "Bridge initialization result: $result")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing bridge: ${e.message}", e)
        }
    }
    
    /**
     * JavaScript interface for callbacks from web component
     * 
     * Exposed as window.AgentOS.invokeAction(actionName, argsJson)
     * 
     * @param actionName Name of the action to invoke
     * @param argsJson JSON string of arguments
     */
    @JavascriptInterface
    fun invokeAction(actionName: String, argsJson: String) {
        try {
            Log.d(TAG, "Action invoked from web component: $actionName")
            Log.d(TAG, "Arguments JSON: $argsJson")
            
            // Parse arguments
            val argsObject = JSONObject(argsJson)
            val args = mutableMapOf<String, Any>()
            
            argsObject.keys().forEach { key ->
                args[key] = argsObject.get(key)
            }
            
            // Invoke callback on main thread
            webView.post {
                onAction(actionName, args)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking action: ${e.message}", e)
        }
    }
    
    /**
     * JavaScript interface for logging from web component
     * 
     * Exposed as window.AgentOS.log(level, message)
     * 
     * @param level Log level ("debug", "info", "warn", "error")
     * @param message Log message
     */
    @JavascriptInterface
    fun log(level: String, message: String) {
        when (level.lowercase()) {
            "debug" -> Log.d("WebComponent", message)
            "info" -> Log.i("WebComponent", message)
            "warn" -> Log.w("WebComponent", message)
            "error" -> Log.e("WebComponent", message)
            else -> Log.v("WebComponent", message)
        }
    }
    
    /**
     * Clear injected data (useful when navigating away)
     */
    fun clearData() {
        try {
            Log.d(TAG, "Clearing injected data")
            
            val script = """
                (function() {
                    if (window.openai) {
                        delete window.openai.global;
                        delete window.openai._meta;
                        console.log('AgentOS: Data cleared');
                    }
                })();
            """.trimIndent()
            
            webView.post {
                webView.evaluateJavascript(script, null)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing data: ${e.message}", e)
        }
    }
}

/**
 * Helper extension function to set up WebView for MCP components
 */
fun WebView.setupForMcp(bridge: McpWebViewBridge) {
    // Enable JavaScript
    settings.javaScriptEnabled = true
    
    // Enable DOM storage (required for React components)
    settings.domStorageEnabled = true
    
    // Enable database storage
    settings.databaseEnabled = true
    
    // Allow file access from file URLs (for loading external resources)
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    
    // Enable zoom controls (optional)
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    
    // Set user agent - force mobile user agent string
    settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 AgentOS/1.0.0"
    
    // Force mobile viewport
    settings.useWideViewPort = false
    settings.loadWithOverviewMode = false
    
    // Add JavaScript interface
    addJavascriptInterface(bridge, "AgentOS")
    
    // Enable WebView debugging in debug builds
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
        WebView.setWebContentsDebuggingEnabled(true)
    }
    
    // Add console message handler for debugging
    webChromeClient = object : android.webkit.WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
            Log.d("WebViewConsole", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
            return true
        }
    }
    
    Log.d("McpWebViewBridge", "WebView configured for MCP components")
}

