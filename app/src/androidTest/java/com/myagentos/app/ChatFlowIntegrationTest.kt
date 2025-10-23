package com.myagentos.app

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for chat message flow
 * 
 * Tests the complete flow of:
 * 1. Typing a message
 * 2. Sending it
 * 3. Verifying it appears in the chat
 * 
 * This is a critical safety net test that verifies core functionality.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ChatFlowIntegrationTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        // Launch MainActivity
        scenario = ActivityScenario.launch(MainActivity::class.java)
        
        // Give app time to initialize
        Thread.sleep(1000)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun mainActivity_launches_successfully() {
        // Verify the main UI elements are present
        onView(withId(R.id.messageInput))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.sendButton))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.chatRecyclerView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun userMessage_appears_in_chat() {
        val testMessage = "Hello AgentOS Test"
        
        // Type message in input field
        onView(withId(R.id.messageInput))
            .perform(typeText(testMessage), closeSoftKeyboard())
        
        // Click send button
        onView(withId(R.id.sendButton))
            .perform(click())
        
        // Give some time for message to be added to chat
        Thread.sleep(500)
        
        // Verify message appears in the chat
        // Note: This verifies the user message is displayed
        // We're not testing AI response here as it requires API keys
        onView(withText(testMessage))
            .check(matches(isDisplayed()))
    }

    @Test
    fun messageInput_clears_after_send() {
        val testMessage = "Test message clearing"
        
        // Type message
        onView(withId(R.id.messageInput))
            .perform(typeText(testMessage), closeSoftKeyboard())
        
        // Click send
        onView(withId(R.id.sendButton))
            .perform(click())
        
        // Give time for processing
        Thread.sleep(300)
        
        // Verify input is cleared
        onView(withId(R.id.messageInput))
            .check(matches(withText("")))
    }

    @Test
    fun emptyMessage_cannot_be_sent() {
        // Try to send without typing anything
        onView(withId(R.id.sendButton))
            .perform(click())
        
        // Verify no crash and input is still empty
        onView(withId(R.id.messageInput))
            .check(matches(withText("")))
    }

    @Test
    fun historyButton_exists() {
        // Verify history button exists (may not be visible depending on state)
        onView(withId(R.id.historyButton))
            .check(matches(isAssignableFrom(android.widget.ImageButton::class.java)))
    }

    @Test
    fun newChatButton_exists() {
        // Verify new chat button exists (may not be visible depending on state)
        onView(withId(R.id.newChatButton))
            .check(matches(isAssignableFrom(android.widget.ImageButton::class.java)))
    }
}

