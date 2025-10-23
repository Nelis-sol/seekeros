package com.myagentos.app.presentation.widget

import com.myagentos.app.R

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.util.Locale

/**
 * MCP WebView Bridge - JavaScript bridge for MCP web components
 * 
 * Implements the FULL window.openai JavaScript API for OpenAI Apps SDK compatibility
 * and bidirectional communication between native Android and web components.
 * 
 * Based on OpenAI's Apps SDK reference:
 * https://developers.openai.com/apps-sdk/reference
 * https://developers.openai.com/apps-sdk/build/custom-ux
 * 
 * JavaScript API exposed (Full Apps SDK API):
 * - window.openai.toolInput - Arguments passed to the tool
 * - window.openai.toolOutput - Structured result from tool
 * - window.openai.toolResponseMetadata - Response metadata
 * - window.openai.callTool(name, args) - Call MCP tools
 * - window.openai.sendFollowUpMessage({prompt}) - Trigger chat
 * - window.openai.requestDisplayMode({mode}) - Change layout
 * - window.openai.setWidgetState(state) - Persist state
 * - window.openai.theme - light/dark theme
 * - window.openai.locale - Client locale
 * - window.openai.displayMode - Current display mode
 * 
 * Also exposes window.webplus as an alias for compatibility
 */
class McpWebViewBridge(
    private val webView: WebView,
    private val onAction: (actionName: String, arguments: Map<String, Any>) -> Unit
) {
    
    companion object {
        private const val TAG = "McpWebViewBridge"
    }
    
    /**
     * Inject FULL Apps SDK data into web component
     * 
     * Sets window.openai with ALL required fields:
     * - toolInput: Arguments passed to the tool
     * - toolOutput: Structured result
     * - toolResponseMetadata: Response metadata
     * - widgetState: Persisted state
     * 
     * @param toolInput Arguments that were passed to the tool
     * @param toolOutput Structured result from tool execution
     * @param toolResponseMetadata Metadata from tool response (_meta field)
     * @param widgetState Optional persisted widget state
     */
    fun injectData(
        toolInput: JSONObject,
        toolOutput: JSONObject?,
        toolResponseMetadata: Map<String, Any>?,
        widgetState: JSONObject? = null
    ) {
        try {
            Log.e(TAG, "=" .repeat(80))
            Log.e(TAG, ">>> INJECTING FULL APPS SDK DATA")
            Log.e(TAG, "toolInput: $toolInput")
            Log.e(TAG, "toolOutput: $toolOutput")
            Log.e(TAG, "toolResponseMetadata: $toolResponseMetadata")
            Log.e(TAG, "widgetState: $widgetState")
            
            val metadataJson = toolResponseMetadata?.let { JSONObject(it).toString() } ?: "null"
            val toolOutputStr = toolOutput?.toString() ?: "null"
            val widgetStateStr = widgetState?.toString() ?: "null"
            
            val script = """
                (function() {
                    console.log('==================================================');
                    console.log('AgentOS: INJECTING FULL APPS SDK DATA');
                    
                    // Initialize window.openai if not exists
                    window.openai = window.openai || {};
                    
                    // ==== APPS SDK STANDARD FIELDS ====
                    
                    // Tool input (arguments passed to tool)
                    window.openai.toolInput = $toolInput;
                    console.log('AgentOS: toolInput =', window.openai.toolInput);
                    
                    // Tool output (structured result)
                    window.openai.toolOutput = $toolOutputStr;
                    console.log('AgentOS: toolOutput =', window.openai.toolOutput);
                    
                    // Tool response metadata (_meta from server)
                    window.openai.toolResponseMetadata = $metadataJson;
                    console.log('AgentOS: toolResponseMetadata =', window.openai.toolResponseMetadata);
                    
                    // Widget state (persisted)
                    window.openai.widgetState = $widgetStateStr;
                    console.log('AgentOS: widgetState =', window.openai.widgetState);
                    
                    // ==== LEGACY COMPATIBILITY ====
                    // Some MCP apps might use these old field names
                    window.openai.global = $toolOutputStr;  // Legacy
                    window.openai._meta = $metadataJson;    // Legacy
                    
                    // ==== WINDOW.WEBPLUS ALIAS ====
                    // Create window.webplus as alias for compatibility
                    window.webplus = window.openai;
                    console.log('AgentOS: window.webplus = window.openai (alias created)');
                    
                    // Dispatch event to notify component that data is ready
                    window.dispatchEvent(new Event('agentos-data-ready'));
                    window.dispatchEvent(new Event('openai:set_globals'));
                    
                    console.log('AgentOS: Data injection complete!');
                    console.log('==================================================');
                })();
            """.trimIndent()
            
            webView.post {
                webView.evaluateJavascript(script) { result ->
                    Log.e(TAG, "Data injection result: $result")
                    Log.e(TAG, "=".repeat(80))
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting data: ${e.message}", e)
            e.printStackTrace()
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
     * Initialize the FULL Apps SDK bridge when WebView is first loaded
     * 
     * Sets up:
     * - window.openai base object
     * - All required API functions
     * - window.webplus alias
     * - Extensive logging
     */
    fun initialize() {
        try {
            Log.e(TAG, "Initializing FULL Apps SDK WebView bridge")
            
            val locale = Locale.getDefault().toLanguageTag()
            val userAgent = "AgentOS/1.0.0 (Android)"
            
            val script = """
                (function() {
                    console.log('==================================================');
                    console.log('AgentOS: INITIALIZING FULL APPS SDK BRIDGE');
                    
                    // Initialize window.openai
                    window.openai = window.openai || {};
                    
                    // ==== METADATA ====
                    window.openai.locale = "$locale";
                    window.openai.theme = "dark";  // Android defaults to dark
                    window.openai.displayMode = "inline";
                    window.openai.maxHeight = 600;
                    window.openai.userAgent = {
                        device: { type: "mobile" },
                        capabilities: { hover: false, touch: true }
                    };
                    window.openai.safeArea = {
                        insets: { top: 0, bottom: 0, left: 0, right: 0 }
                    };
                    
                    // ==== API FUNCTIONS ====
                    
                    // callTool - Call MCP tools
                    window.openai.callTool = function(name, args) {
                        console.log('>>> window.openai.callTool CALLED!');
                        console.log('  - tool name:', name);
                        console.log('  - args:', args);
                        
                        try {
                            const argsJson = JSON.stringify(args || {});
                            window.McpBridge.callTool(name, argsJson);
                            return Promise.resolve({ success: true });
                        } catch (e) {
                            console.error('Error calling tool:', e);
                            return Promise.reject(e);
                        }
                    };
                    
                    // sendFollowUpMessage - Trigger chat
                    window.openai.sendFollowUpMessage = function(args) {
                        console.log('>>> window.openai.sendFollowUpMessage CALLED!');
                        console.log('  - prompt:', args.prompt);
                        
                        try {
                            window.McpBridge.sendFollowUpMessage(args.prompt);
                            return Promise.resolve();
                        } catch (e) {
                            console.error('Error sending follow-up:', e);
                            return Promise.reject(e);
                        }
                    };
                    
                    // requestDisplayMode - Change layout
                    window.openai.requestDisplayMode = function(args) {
                        console.log('>>> window.openai.requestDisplayMode CALLED!');
                        console.log('  - mode:', args.mode);
                        
                        try {
                            window.McpBridge.requestDisplayMode(args.mode);
                            return Promise.resolve({ mode: args.mode });
                        } catch (e) {
                            console.error('Error requesting display mode:', e);
                            return Promise.reject(e);
                        }
                    };
                    
                    // setWidgetState - Persist state
                    window.openai.setWidgetState = function(state) {
                        console.log('>>> window.openai.setWidgetState CALLED!');
                        console.log('  - state:', state);
                        
                        try {
                            const stateJson = JSON.stringify(state || {});
                            window.McpBridge.setWidgetState(stateJson);
                            return Promise.resolve();
                        } catch (e) {
                            console.error('Error setting widget state:', e);
                            return Promise.reject(e);
                        }
                    };
                    
                    // openExternal - Open external links
                    window.openai.openExternal = function(payload) {
                        console.log('>>> window.openai.openExternal CALLED!');
                        console.log('  - href:', payload.href);
                        
                        try {
                            window.McpBridge.openExternal(payload.href);
                        } catch (e) {
                            console.error('Error opening external:', e);
                        }
                    };
                    
                    // ==== WINDOW.WEBPLUS ALIAS ====
                    // Create window.webplus as complete alias
                    window.webplus = window.openai;
                    console.log('AgentOS: window.webplus = window.openai (alias created)');
                    
                    // ==== LEGACY COMPATIBILITY ====
                    // Old AgentOS API
                    window.AgentOS = window.AgentOS || {};
                    window.AgentOS.invokeAction = function(actionName, argsJson) {
                        console.log('>>> LEGACY window.AgentOS.invokeAction called');
                        console.log('  - Use window.openai.callTool instead!');
                        return window.openai.callTool(actionName, JSON.parse(argsJson));
                    };
                    
                    console.log('AgentOS: Bridge initialization complete!');
                    console.log('  - window.openai.callTool:', typeof window.openai.callTool);
                    console.log('  - window.openai.sendFollowUpMessage:', typeof window.openai.sendFollowUpMessage);
                    console.log('  - window.openai.requestDisplayMode:', typeof window.openai.requestDisplayMode);
                    console.log('  - window.openai.setWidgetState:', typeof window.openai.setWidgetState);
                    console.log('  - window.webplus:', typeof window.webplus);
                    console.log('==================================================');
                })();
            """.trimIndent()
            
            webView.post {
                webView.evaluateJavascript(script) { result ->
                    Log.e(TAG, "Bridge initialization result: $result")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing bridge: ${e.message}", e)
            e.printStackTrace()
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
     * JavaScript interface for calling MCP tools
     * 
     * Exposed as window.McpBridge.callTool(name, argsJson)
     * 
     * @param toolName Name of the tool to call
     * @param argsJson JSON string of arguments
     */
    @JavascriptInterface
    fun callTool(toolName: String, argsJson: String) {
        try {
            Log.e(TAG, ">>> callTool CALLED FROM WIDGET!")
            Log.e(TAG, "  - toolName: $toolName")
            Log.e(TAG, "  - argsJson: $argsJson")
            
            val argsObject = JSONObject(argsJson)
            val args = mutableMapOf<String, Any>()
            
            argsObject.keys().forEach { key ->
                args[key] = argsObject.get(key)
            }
            
            webView.post {
                onAction("callTool:$toolName", args)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calling tool: ${e.message}", e)
        }
    }
    
    /**
     * JavaScript interface for sending follow-up messages
     * 
     * Exposed as window.McpBridge.sendFollowUpMessage(prompt)
     * 
     * @param prompt The message to send
     */
    @JavascriptInterface
    fun sendFollowUpMessage(prompt: String) {
        try {
            Log.e(TAG, ">>> sendFollowUpMessage CALLED FROM WIDGET!")
            Log.e(TAG, "  - prompt: $prompt")
            
            webView.post {
                onAction("sendFollowUpMessage", mapOf("prompt" to prompt))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending follow-up message: ${e.message}", e)
        }
    }
    
    /**
     * JavaScript interface for requesting display mode
     * 
     * Exposed as window.McpBridge.requestDisplayMode(mode)
     * 
     * @param mode The requested display mode ("inline", "fullscreen", "pip")
     */
    @JavascriptInterface
    fun requestDisplayMode(mode: String) {
        try {
            Log.e(TAG, ">>> requestDisplayMode CALLED FROM WIDGET!")
            Log.e(TAG, "  - mode: $mode")
            
            webView.post {
                onAction("requestDisplayMode", mapOf("mode" to mode))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting display mode: ${e.message}", e)
        }
    }
    
    /**
     * JavaScript interface for setting widget state
     * 
     * Exposed as window.McpBridge.setWidgetState(stateJson)
     * 
     * @param stateJson JSON string of state to persist
     */
    @JavascriptInterface
    fun setWidgetState(stateJson: String) {
        try {
            Log.e(TAG, ">>> setWidgetState CALLED FROM WIDGET!")
            Log.e(TAG, "  - stateJson: $stateJson")
            
            val stateObject = JSONObject(stateJson)
            val state = mutableMapOf<String, Any>()
            
            stateObject.keys().forEach { key ->
                state[key] = stateObject.get(key)
            }
            
            webView.post {
                onAction("setWidgetState", state)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting widget state: ${e.message}", e)
        }
    }
    
    /**
     * JavaScript interface for opening external links
     * 
     * Exposed as window.McpBridge.openExternal(href)
     * 
     * @param href The URL to open
     */
    @JavascriptInterface
    fun openExternal(href: String) {
        try {
            Log.e(TAG, ">>> openExternal CALLED FROM WIDGET!")
            Log.e(TAG, "  - href: $href")
            
            webView.post {
                onAction("openExternal", mapOf("href" to href))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening external link: ${e.message}", e)
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
    
    // Add JavaScript interfaces
    addJavascriptInterface(bridge, "AgentOS")  // Legacy compatibility
    addJavascriptInterface(bridge, "McpBridge")  // New Apps SDK API
    
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

