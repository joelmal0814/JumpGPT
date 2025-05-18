package com.example.jumpgpt.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Conversation(
    val id: String,
    val title: String,
    val lastMessage: String?,
    val lastUpdated: Long,
    val messages: List<Message> = emptyList()
) : Parcelable

@Parcelize
data class Message(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long,
    val isError: Boolean = false,
    val isThinking: Boolean = false,
    val isStreaming: Boolean = false,
    val isVoiceMessage: Boolean = false,
    val finishReason: String? = null,
    val parentMessageId: String? = null
) : Parcelable

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT
} 