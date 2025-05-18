package com.example.jumpgpt.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.jumpgpt.data.local.dao.ConversationDao
import com.example.jumpgpt.data.local.entity.ConversationEntity
import com.example.jumpgpt.data.local.entity.MessageListConverter
import com.example.jumpgpt.data.local.entity.MessageRoleConverter

@Database(
    entities = [ConversationEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(MessageListConverter::class, MessageRoleConverter::class)
abstract class JumpGPTDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    
    companion object {
        const val DATABASE_NAME = "jumpgpt_db"
    }
} 