package com.example.jumpgpt.domain.model

data class VoiceChatState(
    val isRecording: Boolean = false,
    val isListeningForVoice: Boolean = false,
    val recordingDuration: Long = 0L,
    val transcribedText: String? = null,
    val error: String? = null,
    val isProcessing: Boolean = false,
    val isPlayingResponse: Boolean = false
) 