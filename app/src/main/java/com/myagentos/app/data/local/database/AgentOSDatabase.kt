package com.myagentos.app.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.myagentos.app.data.local.dao.UserAgentDao
import com.myagentos.app.data.local.entity.UserAgent

@Database(
    entities = [UserAgent::class],
    version = 1,
    exportSchema = false
)
abstract class AgentOSDatabase : RoomDatabase() {
    
    abstract fun userAgentDao(): UserAgentDao
    
    companion object {
        @Volatile
        private var INSTANCE: AgentOSDatabase? = null
        
        fun getDatabase(context: Context): AgentOSDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgentOSDatabase::class.java,
                    "agentos_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

