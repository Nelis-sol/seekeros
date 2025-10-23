package com.myagentos.app.presentation.activity

import com.myagentos.app.R
import com.myagentos.app.presentation.widget.McpWebViewBridge
import com.myagentos.app.data.service.McpService
import com.myagentos.app.presentation.widget.setupForMcp
import com.myagentos.app.data.model.McpContent
import com.myagentos.app.data.model.McpAnnotations
import com.myagentos.app.data.model.ToolsCapability
import com.myagentos.app.data.model.ResourcesCapability
import com.myagentos.app.data.model.PromptsCapability
import com.myagentos.app.data.model.McpToolResult

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * MCP Tool Invocation Activity
 * 
 * Full-screen activity for invoking MCP tools and rendering their results.
 * 
 * - Calls tool via McpService
 * - Fetches HTML resource if tool specifies outputTemplate
 * - Renders HTML in WebView with data injection
 * - Handles errors and loading states
 */
class McpToolInvocationActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "McpToolInvocation"
        
        // Intent extras
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_TOOL_NAME = "tool_name"
        const val EXTRA_TOOL_TITLE = "tool_title"
        const val EXTRA_TOOL_ARGUMENTS = "tool_arguments"
    }
    
    private lateinit var webView: WebView
    private lateinit var bridge: McpWebViewBridge
    private lateinit var mcpService: McpService
    
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorLayout: LinearLayout
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var toolTitleView: TextView
    
    private var serverUrl: String? = null
    private var toolName: String? = null
    private var toolTitle: String? = null
    private var toolArguments: Map<String, Any> = emptyMap()
    private var outputTemplate: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mcp_tool_invocation)
        
        // Use singleton instance to share SSE connection with MainActivity
        mcpService = McpService.getInstance()
        
        // Check if this is coming from expand button (has HTML_CONTENT)
        val preRenderedHtml = intent.getStringExtra("HTML_CONTENT")
        val baseUrl = intent.getStringExtra("BASE_URL")
        val displayMode = intent.getStringExtra("DISPLAY_MODE") ?: "fullscreen"
        
        if (preRenderedHtml != null) {
            // Expand button flow - we already have the content but need to fetch fullscreen version
            val appId = intent.getStringExtra("APP_ID")
            toolName = intent.getStringExtra("TOOL_NAME")
            toolTitle = toolName // Will be updated if we re-fetch
            
            Log.d(TAG, "Expand button flow - appId: $appId, toolName: $toolName, displayMode: $displayMode")
            
            // Initialize views
            setupViews()
            
            if (displayMode == "fullscreen" && appId != null) {
                val toolNameLocal = toolName
                if (toolNameLocal != null) {
                    // Re-fetch the resource with fullscreen display mode
                    refetchResourceForFullscreen(appId, toolNameLocal)
                    return
                }
            }
            
            // Fallback: show inline HTML
            run {
                // Just show the inline HTML in fullscreen
                loadingIndicator.visibility = View.GONE
                webView.visibility = View.VISIBLE
                webView.loadDataWithBaseURL(baseUrl, preRenderedHtml, "text/html", "UTF-8", null)
            }
            return
        }
        
        // Original flow - invocation from tool card
        serverUrl = intent.getStringExtra(EXTRA_SERVER_URL)
        toolName = intent.getStringExtra(EXTRA_TOOL_NAME)
        toolTitle = intent.getStringExtra(EXTRA_TOOL_TITLE)
        outputTemplate = intent.getStringExtra("OUTPUT_TEMPLATE")
        
        Log.d(TAG, "Tool invocation flow - Output template from intent: $outputTemplate")
        
        // Set the session ID from MainActivity
        val sessionId = intent.getStringExtra("SESSION_ID")
        if (sessionId != null) {
            mcpService.setSessionId(sessionId)
            Log.d(TAG, "Using session ID from MainActivity: $sessionId")
        } else {
            Log.w(TAG, "No session ID provided from MainActivity")
        }
        
        // Parse tool arguments (JSON string)
        val argsJson = intent.getStringExtra(EXTRA_TOOL_ARGUMENTS)
        if (argsJson != null) {
            try {
                val argsObject = JSONObject(argsJson)
                val args = mutableMapOf<String, Any>()
                argsObject.keys().forEach { key ->
                    args[key] = argsObject.get(key)
                }
                toolArguments = args
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing tool arguments: ${e.message}", e)
            }
        }
        
        // Initialize views
        setupViews()
        
        // Validate inputs
        if (serverUrl == null || toolName == null) {
            showError("Invalid tool configuration")
            return
        }
        
        // Invoke tool
        invokeTool()
    }
    
    private fun setupViews() {
        // Find views
        webView = findViewById(R.id.toolWebView)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        errorLayout = findViewById(R.id.errorLayout)
        errorMessage = findViewById(R.id.errorMessage)
        retryButton = findViewById(R.id.retryButton)
        backButton = findViewById(R.id.backButton)
        toolTitleView = findViewById(R.id.toolTitle)
        
        // Set tool title
        toolTitleView.text = toolTitle ?: toolName ?: "Tool"
        
        // Set black background for WebView
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        
        // Set up WebView bridge
        bridge = McpWebViewBridge(webView) { actionName, arguments ->
            handleWebComponentAction(actionName, arguments)
        }
        
        // Configure WebView
        webView.setupForMcp(bridge)
        
        // Set up WebViewClient to handle page loading
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "WebView page started loading: $url")
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "WebView page finished loading: $url")
                Log.d(TAG, "WebView visibility: ${webView.visibility}")
                Log.d(TAG, "WebView size: ${webView.width}x${webView.height}")
                // Initialize bridge after page loads
                bridge.initialize()
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    Log.e(TAG, "WebView error: ${error?.description} (code: ${error?.errorCode})")
                    Log.e(TAG, "Failed URL: ${request?.url}")
                }
            }
            
            override fun onReceivedHttpError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e(TAG, "WebView HTTP error: ${errorResponse?.statusCode}")
                Log.e(TAG, "Failed URL: ${request?.url}")
            }
        }
        
        // Set up back button
        backButton.setOnClickListener {
            finish()
        }
        
        // Set up retry button
        retryButton.setOnClickListener {
            invokeTool()
        }
    }
    
    private fun invokeTool() {
        Log.d(TAG, "Invoking tool: $toolName on $serverUrl")
        
        // Show loading
        showLoading()
        
        // Launch coroutine to invoke tool
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Call tool on MCP server
                val result = withContext(Dispatchers.IO) {
                    mcpService.callTool(
                        serverUrl = serverUrl!!,
                        toolName = toolName!!,
                        arguments = toolArguments
                    )
                }
                
                if (result == null) {
                    showError("Failed to invoke tool")
                    return@launch
                }
                
                Log.d(TAG, "========== TOOL RESULT ==========")
                Log.d(TAG, "Tool: $toolName")
                Log.d(TAG, "isError: ${result.isError}")
                Log.d(TAG, "Content count: ${result.content.size}")
                result.content.forEachIndexed { index, content ->
                    Log.d(TAG, "Content[$index]: ${content.javaClass.simpleName}")
                    when (content) {
                        is McpContent.Text -> Log.d(TAG, "  Text: ${content.text}")
                        else -> Log.d(TAG, "  Other type")
                    }
                }
                Log.d(TAG, "StructuredContent: ${result.structuredContent}")
                Log.d(TAG, "_meta: ${result._meta}")
                Log.d(TAG, "================================")
                
                if (result.isError) {
                    val errorText = result.content.firstOrNull()?.let {
                        when (it) {
                            is McpContent.Text -> it.text
                            else -> "Tool returned an error"
                        }
                    } ?: "Tool returned an error"
                    showError(errorText)
                    return@launch
                }
                
                // Use output template from tool definition (passed via intent)
                if (outputTemplate != null) {
                    Log.d(TAG, "Rendering HTML component with template: $outputTemplate")
                    // Fetch and render HTML resource
                    renderHtmlComponent(outputTemplate!!, result)
                } else {
                    Log.d(TAG, "No output template, showing text content")
                    // Show text content
                    renderTextContent(result)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking tool: ${e.message}", e)
                showError("Error: ${e.message}")
            }
        }
    }
    
    private suspend fun renderHtmlComponent(resourceUri: String, result: McpToolResult) {
        try {
            Log.d(TAG, "Fetching HTML resource: $resourceUri")
            
            // Fetch HTML resource from server
            val resource = withContext(Dispatchers.IO) {
                mcpService.readResource(
                    serverUrl = serverUrl!!,
                    uri = resourceUri
                )
            }
            
            if (resource == null || resource.text == null) {
                showError("Failed to load HTML resource")
                return
            }
            
            Log.d(TAG, "Loading HTML into WebView")
            Log.d(TAG, "HTML length: ${resource.text.length} chars")
            Log.d(TAG, "HTML snippet: ${resource.text.take(200)}")
            
            // Prepare structured content as JSON string
            val structuredContentJson = if (result.structuredContent != null) {
                result.structuredContent.toString()
            } else {
                "{}"
            }
            
            Log.d(TAG, "========== HTML RENDERING ==========")
            Log.d(TAG, "Resource URI: $resourceUri")
            Log.d(TAG, "Structured content: $structuredContentJson")
            Log.d(TAG, "HTML snippet (first 300 chars): ${resource.text.take(300)}")
            Log.d(TAG, "====================================")
            
            val resourceText = resource.text ?: ""
            
            // Skybridge polyfill - Complete implementation based on OpenAI's Apps SDK
            // Reference: https://developers.openai.com/apps-sdk/build/custom-ux/
            val skybridgePolyfill = """
<script>
// ========== SKYBRIDGE POLYFILL ==========
// OpenAI's Skybridge system - full initialization with event dispatching
// NOTE: NOT using strict mode to allow easier global scope access
(function() {
    
    const SET_GLOBALS_EVENT_TYPE = 'openai:set_globals';
    
    // Create the complete openai API object
    const openaiAPI = {
        locale: '${java.util.Locale.getDefault().toLanguageTag()}',
        displayMode: 'inline',  // Changed from 'fullscreen' to 'inline' for mobile layout
        theme: 'dark',
        maxHeight: window.innerHeight,
        safeArea: { insets: { top: 0, bottom: 0, left: 0, right: 0 } },
        userAgent: { 
            device: { 
                type: 'mobile',
                os: 'android',
                screenWidth: window.screen.width,
                screenHeight: window.screen.height
            },
            capabilities: { 
                hover: false, 
                touch: true,
                mobile: true,
                desktop: false
            }
        },
        
        // Tool data (from MCP server)
        toolInput: {},
        toolOutput: $structuredContentJson,
        toolResponseMetadata: null,
        widgetState: null,
        
        // API methods
        callTool: function(name, args) {
            console.log('[Skybridge] callTool:', name, args);
            return Promise.resolve({ content: [], structuredContent: {} });
        },
        sendFollowUpMessage: function(args) {
            console.log('[Skybridge] sendFollowUpMessage:', args);
            return Promise.resolve();
        },
        openExternal: function(payload) {
            console.log('[Skybridge] openExternal:', payload.href);
            if (window.AgentOS && window.AgentOS.openExternal) {
                window.AgentOS.openExternal(payload.href);
            } else {
                window.open(payload.href, '_blank');
            }
        },
        requestDisplayMode: function(args) {
            console.log('[Skybridge] requestDisplayMode requested:', args.mode, '-> rejecting, locked to inline for mobile');
            // displayMode is locked to 'inline' via Object.defineProperty
            // Always return 'inline' regardless of what was requested
            return Promise.resolve({ mode: 'inline' });
        },
        setWidgetState: function(state) {
            console.log('[Skybridge] setWidgetState:', state);
            this.widgetState = state;
            return Promise.resolve();
        },
        sendEvent: function(event) {
            console.log('[Skybridge] sendEvent:', event);
            if (window.AgentOS && window.AgentOS.sendEvent) {
                window.AgentOS.sendEvent(JSON.stringify(event));
            }
        }
    };
    
    // IMPORTANT: Extend the existing window.openai object (created by early stub)
    // rather than replacing it. This preserves references captured by ES6 modules.
    if (window.openai) {
        console.log('[Skybridge] Extending existing window.openai stub with full API');
        // Extend properties of the existing object
        Object.assign(window.openai, openaiAPI);
    } else {
        console.log('[Skybridge] Creating new window.openai (no stub found)');
        window.openai = openaiAPI;
    }
    
    // CRITICAL: Lock displayMode to 'inline' to prevent layout switching
    Object.defineProperty(window.openai, 'displayMode', {
        value: 'inline',
        writable: false,
        configurable: false,
        enumerable: true
    });
    console.log('[Skybridge] Locked displayMode to:', window.openai.displayMode);
    
    // CRITICAL: The minified JavaScript renames "openai" to "webplus" 
    // We need to provide BOTH for compatibility
    window.webplus = window.openai;
    
    // Also assign to other global scopes
    globalThis.openai = window.openai;
    globalThis.webplus = window.webplus;
    if (typeof self !== 'undefined') {
        self.openai = window.openai;
        self.webplus = window.webplus;
    }
    
    console.log('[Skybridge] ✓ window.openai initialized');
    console.log('[Skybridge]   typeof window.openai:', typeof window.openai);
    console.log('[Skybridge]   window.openai keys:', Object.keys(window.openai));
    console.log('[Skybridge]   displayMode:', window.openai.displayMode);
    console.log('[Skybridge]   toolOutput:', window.openai.toolOutput);
    
    // Add error handler for any access attempts
    window.addEventListener('error', function(e) {
        console.error('[Skybridge] Global error:', e.message, 'at', e.filename, 'line', e.lineno);
    });
    
    // CRITICAL: Set up the subscription mechanism that React's useSyncExternalStore expects
    // This mimics OpenAI's Skybridge implementation
    
    // Store for event listeners
    const listeners = new Set();
    
    // Create a proxy subscribe function that React components will use
    window.__openai_subscribe = function(listener) {
        console.log('[Skybridge] Component subscribed to openai changes');
        listeners.add(listener);
        return function() {
            console.log('[Skybridge] Component unsubscribed');
            listeners.delete(listener);
        };
    };
    
    // Create a getter that React components will use
    window.__openai_getSnapshot = function() {
        console.log('[Skybridge] getSnapshot called, returning:', window.openai);
        return window.openai;
    };
    
    // Dispatch the set_globals event (both openai and webplus versions for minified code compatibility)
    const setGlobalsEvent = new CustomEvent(SET_GLOBALS_EVENT_TYPE, {
        detail: { globals: openaiAPI }
    });
    const setGlobalsEventWebplus = new CustomEvent('webplus:set_globals', {
        detail: { globals: openaiAPI }
    });
    
    // Dispatch IMMEDIATELY (synchronously) before any ES6 modules can execute
    console.log('[Skybridge] Dispatching', SET_GLOBALS_EVENT_TYPE, 'and webplus:set_globals events NOW');
    window.dispatchEvent(setGlobalsEvent);
    window.dispatchEvent(setGlobalsEventWebplus);
    console.log('[Skybridge] ✓ Events dispatched synchronously');
    
    // Notify all listeners
    listeners.forEach(function(listener) {
        console.log('[Skybridge] Notifying listener of initial state');
        try {
            listener();
        } catch (e) {
            console.error('[Skybridge] Error notifying listener:', e);
        }
    });
    
    // Also dispatch after delays to catch late subscribers
    setTimeout(function() {
        window.dispatchEvent(setGlobalsEvent);
        window.dispatchEvent(setGlobalsEventWebplus);
        listeners.forEach(function(listener) { listener(); });
        console.log('[Skybridge] ✓ Events re-dispatched at 10ms');
    }, 10);
    setTimeout(function() {
        window.dispatchEvent(setGlobalsEvent);
        window.dispatchEvent(setGlobalsEventWebplus);
        listeners.forEach(function(listener) { listener(); });
        console.log('[Skybridge] ✓ Events re-dispatched at 50ms');
    }, 50);
    setTimeout(function() {
        window.dispatchEvent(setGlobalsEvent);
        window.dispatchEvent(setGlobalsEventWebplus);
        listeners.forEach(function(listener) { listener(); });
        console.log('[Skybridge] ✓ Events re-dispatched at 200ms');
    }, 200);
})();
</script>
"""
            
            Log.d(TAG, "Injecting Skybridge polyfill into HEAD (before module scripts)")
            
            // Create a MINIMAL early stub - this runs FIRST, before anything else
            // This prevents undefined errors during ES6 module parsing
            val earlyStub = """<script>
// Minimal early stub to prevent undefined errors in ES6 module scope
window.openai = window.openai || {
    displayMode: 'inline',
    theme: 'dark',
    locale: '${java.util.Locale.getDefault().toLanguageTag()}',
    maxHeight: 99999,
    safeArea: { insets: { top: 0, bottom: 0, left: 0, right: 0 } },
    userAgent: { 
        device: { type: 'mobile', os: 'android', screenWidth: window.screen.width, screenHeight: window.screen.height },
        capabilities: { hover: false, touch: true, mobile: true, desktop: false }
    },
    toolInput: {},
    toolOutput: {},
    toolResponseMetadata: null,
    widgetState: null,
    callTool: function() { return Promise.resolve({}) },
    sendFollowUpMessage: function() { return Promise.resolve() },
    openExternal: function() {},
    requestDisplayMode: function() { return Promise.resolve({ mode: 'inline' }) },
    setWidgetState: function() { return Promise.resolve() },
    sendEvent: function() {}
};
// Also create window.webplus for minified code compatibility
window.webplus = window.openai;
console.log('[Early Stub] window.openai and window.webplus stubs created');
</script>"""
            
            // Wrap in complete document with early stub + full polyfill in HEAD
            val fullHtml = """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
$earlyStub
<style>
body {
    margin: 0;
    padding: 0;
    background-color: #000000;
    color: #FFFFFF;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    max-width: 428px; /* Force mobile width */
}
/* Force mobile layout via media query override */
@media (min-width: 429px) {
    body {
        max-width: 428px !important;
    }
}
</style>
$skybridgePolyfill
</head>
<body>
$resourceText
</body>
</html>"""
            
            Log.d(TAG, "Full HTML length: ${fullHtml.length} chars")
            Log.d(TAG, "Full HTML (first 500 chars):\n${fullHtml.take(500)}")
            
            // Load HTML in WebView with text/html mime type
            Log.d(TAG, ">>> Loading HTML into WebView...")
            webView.loadDataWithBaseURL(
                "https://persistent.oaistatic.com/",  // Use the actual CDN domain for script loading
                fullHtml,
                "text/html",
                "UTF-8",
                null
            )
            Log.d(TAG, ">>> HTML load initiated, calling showWebView()...")
            
            // Show WebView (data is already injected in the HTML)
            showWebView()
            Log.d(TAG, ">>> showWebView() completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering HTML component: ${e.message}", e)
            showError("Error rendering component: ${e.message}")
        }
    }
    
    private fun renderTextContent(result: McpToolResult) {
        Log.d(TAG, ">>> renderTextContent() called")
        
        // For text-only content, create simple HTML to display it
        val textContent = result.content.joinToString("\n\n") { content ->
            when (content) {
                is McpContent.Text -> content.text
                is McpContent.Image -> "[Image: ${content.mimeType}]"
                is McpContent.Audio -> "[Audio: ${content.mimeType}]"
                is McpContent.ResourceLink -> "[Resource: ${content.uri}]"
                is McpContent.EmbeddedResource -> "[Resource: ${content.resource.uri}]"
            }
        }
        
        Log.d(TAG, "Text content to display: $textContent")
        
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        padding: 20px;
                        background-color: #000000;
                        color: #FFFFFF;
                        margin: 0;
                    }
                    pre {
                        white-space: pre-wrap;
                        word-wrap: break-word;
                        background-color: #1C1C1E;
                        padding: 16px;
                        border-radius: 8px;
                        overflow-x: auto;
                    }
                </style>
            </head>
            <body>
                <pre>$textContent</pre>
            </body>
            </html>
        """.trimIndent()
        
        Log.d(TAG, "Text HTML length: ${html.length} chars")
        Log.d(TAG, ">>> Loading text HTML into WebView...")
        
        webView.loadDataWithBaseURL(
            "https://app.local/",
            html,
            "text/html",
            "UTF-8",
            null
        )
        
        Log.d(TAG, ">>> Text HTML load initiated, calling showWebView()...")
        showWebView()
        Log.d(TAG, ">>> renderTextContent() completed")
    }
    
    private fun handleWebComponentAction(actionName: String, arguments: Map<String, Any>) {
        Log.d(TAG, "Web component action: $actionName with args: $arguments")
        Toast.makeText(this, "Action: $actionName", Toast.LENGTH_SHORT).show()
        
        // Handle actions from web component
        // This could trigger additional tool calls, navigation, etc.
        when (actionName) {
            "close" -> finish()
            "refresh" -> invokeTool()
            else -> {
                // Custom action handling
                Log.d(TAG, "Unhandled action: $actionName")
            }
        }
    }
    
    private fun showLoading() {
        Log.d(TAG, ">>> showLoading()")
        loadingIndicator.visibility = View.VISIBLE
        webView.visibility = View.GONE
        errorLayout.visibility = View.GONE
        Log.d(TAG, "    Loading indicator visible, WebView hidden")
    }
    
    private fun showWebView() {
        Log.d(TAG, ">>> showWebView()")
        Log.d(TAG, "    WebView current state - visibility: ${webView.visibility}, size: ${webView.width}x${webView.height}")
        loadingIndicator.visibility = View.GONE
        webView.visibility = View.VISIBLE
        errorLayout.visibility = View.GONE
        Log.d(TAG, "    WebView now visible, loading and error hidden")
        
        // Force a layout pass
        webView.post {
            Log.d(TAG, "    WebView after layout - visibility: ${webView.visibility}, size: ${webView.width}x${webView.height}")
            Log.d(TAG, "    WebView URL: ${webView.url}")
        }
    }
    
    private fun refetchResourceForFullscreen(appId: String, toolNameParam: String) {
        Log.d(TAG, "Re-fetching resource for fullscreen: appId=$appId, toolName=$toolNameParam")
        
        // Show loading
        showLoading()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Get required data from intent
                val serverUrlFromIntent = intent.getStringExtra("SERVER_URL")
                val outputTemplateFromIntent = intent.getStringExtra("OUTPUT_TEMPLATE")
                val toolArgumentsJson = intent.getStringExtra("TOOL_ARGUMENTS")
                
                if (serverUrlFromIntent == null || outputTemplateFromIntent == null) {
                    Log.w(TAG, "Missing serverUrl or outputTemplate, falling back to inline HTML")
                    // Just show the inline HTML that was passed
                    val preRenderedHtml = intent.getStringExtra("HTML_CONTENT")
                    val baseUrl = intent.getStringExtra("BASE_URL")
                    if (preRenderedHtml != null) {
                        loadingIndicator.visibility = View.GONE
                        webView.visibility = View.VISIBLE
                        webView.loadDataWithBaseURL(baseUrl, preRenderedHtml, "text/html", "UTF-8", null)
                    } else {
                        showError("Missing required data for fullscreen view")
                    }
                    return@launch
                }
                
                // Parse tool arguments
                val arguments = if (toolArgumentsJson != null) {
                    try {
                        val argsObject = JSONObject(toolArgumentsJson)
                        val args = mutableMapOf<String, Any>()
                        argsObject.keys().forEach { key ->
                            args[key] = argsObject.get(key)
                        }
                        args
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing tool arguments", e)
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }
                
                Log.d(TAG, "Re-invoking tool with serverUrl=$serverUrlFromIntent, outputTemplate=$outputTemplateFromIntent")
                
                // Call the tool again
                val result = withContext(Dispatchers.IO) {
                    mcpService.callTool(
                        serverUrl = serverUrlFromIntent,
                        toolName = toolNameParam,
                        arguments = arguments
                    )
                }
                
                if (result == null) {
                    showError("Failed to invoke tool for fullscreen view")
                    return@launch
                }
                
                // Fetch the resource with inline display mode (mobile fullscreen is still inline on mobile)
                val resourceUri = outputTemplateFromIntent
                Log.d(TAG, "Fetching mobile fullscreen resource (using inline mode): $resourceUri")
                
                val resource = withContext(Dispatchers.IO) {
                    // Use "inline" mode for mobile, not "fullscreen" which implies desktop
                    mcpService.readResource(serverUrlFromIntent, resourceUri, displayMode = "inline")
                }
                
                if (resource != null && resource.text != null) {
                    Log.d(TAG, "Got fullscreen resource, rendering...")
                    
                    // Build HTML with polyfill - use "inline" for mobile fullscreen
                    val completeHtml = buildHtmlWithPolyfill(resource.text!!, result, displayMode = "inline")
                    
                    // Render in WebView
                    loadingIndicator.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                    webView.loadDataWithBaseURL(
                        "https://persistent.oaistatic.com/",
                        completeHtml,
                        "text/html",
                        "UTF-8",
                        null
                    )
                } else {
                    showError("Failed to load fullscreen resource")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error re-fetching resource for fullscreen", e)
                showError("Error loading fullscreen view: ${e.message}")
            }
        }
    }
    
    private fun buildHtmlWithPolyfill(resourceText: String, toolResult: McpToolResult, displayMode: String = "inline"): String {
        // Build Skybridge polyfill with configurable display mode
        val toolInput = JSONObject()
        val toolOutput = JSONObject()
        
        // Extract tool output from result
        try {
            val firstContent = toolResult.content.firstOrNull()
            if (firstContent is McpContent.Text) {
                toolOutput.put("text", firstContent.text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting tool output", e)
        }
        
        // Add viewport meta tag to force mobile rendering
        val viewportMeta = """<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">"""
        
        val skybridgePolyfill = """<script>
(function() {
    console.log('[Skybridge] Initializing OpenAI global API with displayMode: $displayMode (MOBILE)...');
    
    // Force mobile user agent detection
    Object.defineProperty(navigator, 'userAgent', {
        get: function() { return 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 AgentOS/1.0'; }
    });
    
    var openaiAPI = {
        // Layout globals
        displayMode: '$displayMode',
        theme: 'dark',
        locale: '${java.util.Locale.getDefault().toLanguageTag()}',
        maxHeight: 99999,
        safeArea: { insets: { top: 0, bottom: 0, left: 0, right: 0 } },
        userAgent: { 
            device: { type: 'mobile', os: 'android', screenWidth: window.screen.width, screenHeight: window.screen.height },
            capabilities: { hover: false, touch: true, mobile: true, desktop: false }
        },
        
        // State
        toolInput: $toolInput,
        toolOutput: $toolOutput,
        toolResponseMetadata: null,
        widgetState: null,
        
        // API Methods - Exact OpenAI spec
        callTool: function(name, args) { 
            console.log('[Skybridge] callTool called:', name, args);
            if (window.AndroidBridge && window.AndroidBridge.callTool) {
                window.AndroidBridge.callTool(name, JSON.stringify(args || {}));
            }
            return Promise.resolve({ 
                content: [], 
                isError: false 
            });
        },
        
        sendFollowUpMessage: function(payload) { 
            console.log('[Skybridge] sendFollowUpMessage called:', payload);
            if (window.AndroidBridge && window.AndroidBridge.sendFollowUpMessage) {
                window.AndroidBridge.sendFollowUpMessage(payload.prompt || '');
            }
            return Promise.resolve();
        },
        
        openExternal: function(payload) { 
            console.log('[Skybridge] openExternal called:', payload);
            if (window.AndroidBridge && window.AndroidBridge.openExternal) {
                window.AndroidBridge.openExternal(payload.href || '');
            }
        },
        
        requestDisplayMode: function(payload) { 
            console.log('[Skybridge] requestDisplayMode called:', payload);
            var requestedMode = payload.mode || '$displayMode';
            if (window.AndroidBridge && window.AndroidBridge.requestDisplayMode) {
                window.AndroidBridge.requestDisplayMode(requestedMode);
            }
            // On mobile, PiP is coerced to fullscreen
            var grantedMode = requestedMode === 'pip' ? 'fullscreen' : requestedMode;
            return Promise.resolve({ mode: grantedMode });
        },
        
        setWidgetState: function(state) { 
            console.log('[Skybridge] setWidgetState called:', state);
            if (window.AndroidBridge && window.AndroidBridge.setWidgetState) {
                window.AndroidBridge.setWidgetState(JSON.stringify(state || {}));
            }
            openaiAPI.widgetState = state;
            // Dispatch globals update event
            var event = new CustomEvent('openai:set_globals', { 
                detail: { globals: { widgetState: state } } 
            });
            window.dispatchEvent(event);
            return Promise.resolve();
        },
        
        sendEvent: function(eventName, data) { 
            console.log('[Skybridge] sendEvent called:', eventName, data);
            // Custom events can be dispatched here
        }
    };
    
    window.openai = openaiAPI;
    window.webplus = openaiAPI;
    Object.defineProperty(window, 'openai', { configurable: false, writable: false, value: openaiAPI });
    
    // Dispatch initial set_globals event with correct structure
    var setGlobalsEvent = new CustomEvent('openai:set_globals', { 
        detail: { globals: openaiAPI } 
    });
    var setGlobalsEventWebplus = new CustomEvent('webplus:set_globals', { 
        detail: { globals: openaiAPI } 
    });
    window.dispatchEvent(setGlobalsEvent);
    window.dispatchEvent(setGlobalsEventWebplus);
    console.log('[Skybridge] ✓ window.openai and window.webplus initialized');
    
    // Redispatch after a brief delay for late-loading scripts
    setTimeout(function() {
        window.dispatchEvent(setGlobalsEvent);
        window.dispatchEvent(setGlobalsEventWebplus);
    }, 10);
})();
</script>"""
        
        // Return complete HTML with viewport meta and polyfill injected
        var result = resourceText
        
        // Inject viewport meta tag
        result = if (result.contains("<head>", ignoreCase = true)) {
            result.replace("<head>", "<head>$viewportMeta", ignoreCase = true)
        } else if (result.contains("<!DOCTYPE", ignoreCase = true)) {
            result.replaceFirst("<!DOCTYPE html>", "<!DOCTYPE html><head>$viewportMeta</head>", ignoreCase = true)
        } else {
            "$viewportMeta$result"
        }
        
        // Inject polyfill before </head> or at start
        result = if (result.contains("</head>", ignoreCase = true)) {
            result.replace("</head>", "$skybridgePolyfill</head>", ignoreCase = true)
        } else {
            skybridgePolyfill + result
        }
        
        return result
    }
    
    private fun showError(message: String) {
        Log.e(TAG, ">>> showError: $message")
        loadingIndicator.visibility = View.GONE
        webView.visibility = View.GONE
        errorLayout.visibility = View.VISIBLE
        errorMessage.text = message
        Log.e(TAG, "    Error layout visible, WebView and loading hidden")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up WebView
        webView.destroy()
    }
}

