package com.example.jumpgpt.data.mapper

import com.example.jumpgpt.data.local.entity.ConversationEntity
import com.example.jumpgpt.data.local.entity.MessageEntity
import com.example.jumpgpt.domain.model.Conversation
import com.example.jumpgpt.domain.model.Message
import com.example.jumpgpt.domain.model.MessageRole

fun ConversationEntity.toDomain(): Conversation {
    return Conversation(
        id = id,
        title = title,
        lastMessage = lastMessage,
        lastUpdated = lastUpdated,
        messages = messages.map { it.toDomain() }
    )
}

fun Conversation.toEntity(): ConversationEntity {
    return ConversationEntity(
        id = id,
        title = title,
        lastMessage = lastMessage,
        lastUpdated = lastUpdated,
        messages = messages.map { it.toEntity() }
    )
}

fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        content = content,
        role = role,
        timestamp = timestamp,
        isThinking = isThinking,
        isStreaming = isStreaming,
        isVoiceMessage = isVoiceMessage,
        finishReason = null,
        parentMessageId = null
    )
}

fun Message.toEntity(): MessageEntity {
    return MessageEntity(
        id = id,
        content = content,
        role = role,
        timestamp = timestamp,
        isThinking = isThinking,
        isStreaming = isStreaming,
        isVoiceMessage = isVoiceMessage
    )
} 