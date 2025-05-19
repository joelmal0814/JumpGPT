package com.example.jumpgpt.domain.model

import java.time.Instant

data class ChatState(
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val inputText: String = "",
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val currentPlayingMessageId: String? = null,
    val loadingTtsMessageId: String? = null
) {
    val canSendMessage: Boolean
        get() = inputText.isNotBlank() && !isLoading && !isRecording
        
    val canStartRecording: Boolean
        get() = !isLoading && !isRecording && inputText.isBlank()
} 