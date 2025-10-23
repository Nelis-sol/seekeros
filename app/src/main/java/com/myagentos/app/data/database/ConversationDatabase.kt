package com.myagentos.app.data.database

import com.myagentos.app.R
import com.myagentos.app.domain.model.ChatMessage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class ConversationDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "conversations.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_CONVERSATIONS = "conversations"
        
        // Column names
        private const val COLUMN_ID = "id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_FIRST_MESSAGE = "first_message"
        private const val COLUMN_LAST_MESSAGE = "last_message"
        private const val COLUMN_MESSAGES_JSON = "messages_json"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val COLUMN_UPDATED_AT = "updated_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_CONVERSATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_FIRST_MESSAGE TEXT,
                $COLUMN_LAST_MESSAGE TEXT,
                $COLUMN_MESSAGES_JSON JSON NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        
        db.execSQL(createTable)
        Log.d("ConversationDatabase", "Database created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONVERSATIONS")
        onCreate(db)
    }

    // Data class for conversation
    data class Conversation(
        val id: Long = 0,
        val title: String,
        val firstMessage: String?,
        val lastMessage: String?,
        val messages: List<ChatMessage>,
        val createdAt: Long,
        val updatedAt: Long
    )

    // Save a new conversation
    fun saveConversation(conversation: Conversation): Long {
        val db = writableDatabase
        val values = android.content.ContentValues().apply {
            put(COLUMN_TITLE, conversation.title)
            put(COLUMN_FIRST_MESSAGE, conversation.firstMessage)
            put(COLUMN_LAST_MESSAGE, conversation.lastMessage)
            put(COLUMN_MESSAGES_JSON, messagesToJson(conversation.messages))
            put(COLUMN_CREATED_AT, conversation.createdAt)
            put(COLUMN_UPDATED_AT, conversation.updatedAt)
        }
        
        val id = db.insert(TABLE_CONVERSATIONS, null, values)
        Log.d("ConversationDatabase", "Saved conversation with ID: $id")
        return id
    }

    // Update an existing conversation
    fun updateConversation(id: Long, conversation: Conversation) {
        val db = writableDatabase
        val values = android.content.ContentValues().apply {
            put(COLUMN_TITLE, conversation.title)
            put(COLUMN_FIRST_MESSAGE, conversation.firstMessage)
            put(COLUMN_LAST_MESSAGE, conversation.lastMessage)
            put(COLUMN_MESSAGES_JSON, messagesToJson(conversation.messages))
            put(COLUMN_UPDATED_AT, conversation.updatedAt)
        }
        
        val rowsAffected = db.update(TABLE_CONVERSATIONS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        Log.d("ConversationDatabase", "Updated conversation $id, rows affected: $rowsAffected")
    }

    // Get all conversations (for history list)
    fun getAllConversations(): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CONVERSATIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_UPDATED_AT DESC" // Most recent first
        )

        cursor.use {
            while (it.moveToNext()) {
                val conversation = Conversation(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE)),
                    firstMessage = it.getString(it.getColumnIndexOrThrow(COLUMN_FIRST_MESSAGE)),
                    lastMessage = it.getString(it.getColumnIndexOrThrow(COLUMN_LAST_MESSAGE)),
                    messages = jsonToMessages(it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGES_JSON))),
                    createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                    updatedAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_UPDATED_AT))
                )
                conversations.add(conversation)
            }
        }

        Log.d("ConversationDatabase", "Retrieved ${conversations.size} conversations")
        return conversations
    }

    // Get a specific conversation by ID
    fun getConversation(id: Long): Conversation? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CONVERSATIONS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                return Conversation(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    title = it.getString(it.getColumnIndexOrThrow(COLUMN_TITLE)),
                    firstMessage = it.getString(it.getColumnIndexOrThrow(COLUMN_FIRST_MESSAGE)),
                    lastMessage = it.getString(it.getColumnIndexOrThrow(COLUMN_LAST_MESSAGE)),
                    messages = jsonToMessages(it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGES_JSON))),
                    createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                    updatedAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_UPDATED_AT))
                )
            }
        }
        return null
    }

    // Delete a conversation
    fun deleteConversation(id: Long) {
        val db = writableDatabase
        val rowsAffected = db.delete(TABLE_CONVERSATIONS, "$COLUMN_ID = ?", arrayOf(id.toString()))
        Log.d("ConversationDatabase", "Deleted conversation $id, rows affected: $rowsAffected")
    }

    // Clean up old conversations (keep only last 50)
    fun cleanupOldConversations() {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_CONVERSATIONS", null)
        cursor.use {
            if (it.moveToFirst()) {
                val count = it.getInt(0)
                if (count > 50) {
                    val deleteCount = count - 50
                    val deletedRows = db.delete(
                        TABLE_CONVERSATIONS,
                        "$COLUMN_ID IN (SELECT $COLUMN_ID FROM $TABLE_CONVERSATIONS ORDER BY $COLUMN_UPDATED_AT ASC LIMIT $deleteCount)",
                        null
                    )
                    Log.d("ConversationDatabase", "Cleaned up $deletedRows old conversations")
                }
            }
        }
    }

    // Helper methods for JSON conversion
    private fun messagesToJson(messages: List<ChatMessage>): String {
        val jsonArray = JSONArray()
        messages.forEach { message ->
            val jsonObject = JSONObject().apply {
                put("text", message.text)
                put("is_user", message.isUser)
                put("timestamp", message.timestamp)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    private fun jsonToMessages(jsonString: String): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val message = ChatMessage(
                    text = jsonObject.getString("text"),
                    isUser = jsonObject.getBoolean("is_user"),
                    timestamp = jsonObject.getLong("timestamp")
                )
                messages.add(message)
            }
        } catch (e: Exception) {
            Log.e("ConversationDatabase", "Error parsing messages JSON", e)
        }
        return messages
    }
}
