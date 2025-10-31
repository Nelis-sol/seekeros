package com.myagentos.app.data.local.dao

import androidx.room.*
import com.myagentos.app.data.local.entity.UserAgent
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAgentDao {
    
    @Query("SELECT * FROM user_agents ORDER BY createdAt ASC")
    fun getAllAgents(): Flow<List<UserAgent>>
    
    @Query("SELECT * FROM user_agents WHERE id = :agentId")
    suspend fun getAgentById(agentId: String): UserAgent?
    
    @Query("SELECT * FROM user_agents WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultAgent(): UserAgent?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: UserAgent)
    
    @Update
    suspend fun updateAgent(agent: UserAgent)
    
    @Delete
    suspend fun deleteAgent(agent: UserAgent)
    
    @Query("DELETE FROM user_agents WHERE id = :agentId")
    suspend fun deleteAgentById(agentId: String)
    
    @Query("UPDATE user_agents SET isDefault = 0")
    suspend fun clearAllDefaults()
}

