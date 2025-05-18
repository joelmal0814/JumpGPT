package com.example.jumpgpt.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.jumpgpt.domain.model.MessageRole
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val lastMessage: String?,
    val lastUpdated: Long,
    @TypeConverters(MessageListConverter::class)
    val messages: List<MessageEntity> = emptyList()
)

data class MessageEntity(
    val id: String,
    val content: String,
    @TypeConverters(MessageRoleConverter::class)
    val role: MessageRole,
    val timestamp: Long,
    val isThinking: Boolean = false,
    val isStreaming: Boolean = false,
    val isVoiceMessage: Boolean = false
)

class MessageListConverter {
    private val gson = Gson()
    
    @TypeConverter
    fun fromMessageList(messages: List<MessageEntity>): String {
        return gson.toJson(messages)
    }
    
    @TypeConverter
    fun toMessageList(messagesString: String): List<MessageEntity> {
        val type = object : TypeToken<List<MessageEntity>>() {}.type
        return gson.fromJson(messagesString, type)
    }
}

class MessageRoleConverter {
    @TypeConverter
    fun fromMessageRole(role: MessageRole): String {
        return role.name
    }

    @TypeConverter
    fun toMessageRole(value: String): MessageRole {
        return MessageRole.valueOf(value)
    }
} 