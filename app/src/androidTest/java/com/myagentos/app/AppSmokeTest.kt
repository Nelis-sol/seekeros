package com.myagentos.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Smoke tests to verify the app can be built and installed
 * 
 * These tests run quickly and verify basic app integrity without
 * requiring complex UI interactions.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class AppSmokeTest {

    @Test
    fun appContext_isCorrect() {
        // Verify app package name
        val context: Context = ApplicationProvider.getApplicationContext()
        assertEquals("com.myagentos.app", context.packageName)
    }

    @Test
    fun conversationManager_canBeInstantiated() {
        // Verify ConversationManager can be created
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = ConversationManager(context)
        assertNotNull(manager)
    }

    @Test
    fun conversationManager_startsWithZeroMessages() {
        // Verify fresh conversation has no messages
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = ConversationManager(context)
        manager.startNewConversation()
        assertEquals(0, manager.getCurrentMessagesCount())
    }

    @Test
    fun conversationManager_canAddMessage() {
        // Verify messages can be added
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = ConversationManager(context)
        manager.startNewConversation()
        
        manager.addMessage(ChatMessage("Test", isUser = true, timestamp = System.currentTimeMillis()))
        assertEquals(1, manager.getCurrentMessagesCount())
    }

    @Test
    fun browserManager_canBeInstantiated() {
        // Verify BrowserManager can be created
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = BrowserManager(context)
        assertNotNull(manager)
    }

    @Test
    fun mcpService_canBeInstantiated() {
        // Verify McpService singleton works
        val service = McpService.getInstance()
        assertNotNull(service)
    }
}

