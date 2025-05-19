package com.example.jumpgpt.domain.model

data class VoiceChatState(
    val isRecording: Boolean = false,
    val isListeningForVoice: Boolean = false,
    val isProcessing: Boolean = false,
    val isPlayingResponse: Boolean = false,
    val recordingDuration: Long = 0L,
    val transcribedText: String? = null,
    val error: String? = null,
    val audioLevel: Float = 0f
) 