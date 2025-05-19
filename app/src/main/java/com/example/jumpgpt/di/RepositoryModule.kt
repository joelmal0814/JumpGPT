package com.example.jumpgpt.di

import android.content.Context
import com.example.jumpgpt.data.local.dao.ConversationDao
import com.example.jumpgpt.data.remote.api.ChatApi
import com.example.jumpgpt.data.repository.ChatRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideChatRepository(
        chatApi: ChatApi,
        conversationDao: ConversationDao,
        gson: Gson,
        @ApplicationContext context: Context
    ): ChatRepository {
        return ChatRepository(chatApi, conversationDao, gson, context)
    }
} 