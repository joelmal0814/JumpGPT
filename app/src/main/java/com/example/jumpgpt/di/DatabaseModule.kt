package com.example.jumpgpt.di

import android.content.Context
import androidx.room.Room
import com.example.jumpgpt.data.local.JumpGPTDatabase
import com.example.jumpgpt.data.local.dao.ConversationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): JumpGPTDatabase {
        return Room.databaseBuilder(
            context,
            JumpGPTDatabase::class.java,
            JumpGPTDatabase.DATABASE_NAME
        ).build()
    }
    
    @Provides
    fun provideConversationDao(database: JumpGPTDatabase): ConversationDao {
        return database.conversationDao()
    }
} 