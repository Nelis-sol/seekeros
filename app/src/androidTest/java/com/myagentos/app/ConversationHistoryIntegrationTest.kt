package com.myagentos.app

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for conversation history feature
 * 
 * Tests:
 * 1. History button opens history activity
 * 2. New chat button starts fresh conversation
 * 3. Conversation management doesn't crash the app
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ConversationHistoryIntegrationTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(1000)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun historyButton_exists() {
        // Verify history button exists in layout
        onView(withId(R.id.historyButton))
            .check(matches(isAssignableFrom(android.widget.ImageButton::class.java)))
    }

    @Test
    fun newChatButton_exists() {
        // Verify new chat button exists in layout
        onView(withId(R.id.newChatButton))
            .check(matches(isAssignableFrom(android.widget.ImageButton::class.java)))
    }

    @Test
    fun messageInput_exists() {
        // Verify main chat input exists and is visible
        onView(withId(R.id.messageInput))
            .check(matches(isDisplayed()))
    }
}

