package com.example.jumpgpt.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.jumpgpt.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY lastUpdated DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: String): ConversationEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)
    
    @Update
    suspend fun updateConversation(conversation: ConversationEntity)
    
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)
    
    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY lastUpdated DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>
} 