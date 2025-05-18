package com.example.jumpgpt.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jumpgpt.domain.model.ChatState
import com.example.jumpgpt.domain.model.Message
import com.example.jumpgpt.domain.model.MessageRole
import com.example.jumpgpt.util.TimeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    fun onInputTextChanged(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun onSendMessage() {
        val currentText = _state.value.inputText.trim()
        if (currentText.isBlank() || _state.value.isLoading) return
        sendMessage(currentText, false)
    }

    fun onSendVoiceMessage(transcribedText: String) {
        if (transcribedText.isBlank() || _state.value.isLoading) return
        sendMessage(transcribedText, true)
    }

    private fun sendMessage(text: String, isVoiceMessage: Boolean) {
        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            content = text,
            role = MessageRole.USER,
            timestamp = TimeUtil.now(),
            isVoiceMessage = isVoiceMessage
        )

        _state.update { currentState ->
            currentState.copy(
                messages = currentState.messages + userMessage,
                inputText = "",
                isLoading = true
            )
        }

        viewModelScope.launch {
            val aiMessage = Message(
                id = UUID.randomUUID().toString(),
                content = "",
                role = MessageRole.ASSISTANT,
                timestamp = TimeUtil.now(),
                isThinking = true
            )

            _state.update { it.copy(
                messages = it.messages + aiMessage
            ) }

            kotlinx.coroutines.delay(1000)

            _state.update { currentState ->
                val updatedMessages = currentState.messages.map { message ->
                    if (message.id == aiMessage.id) {
                        message.copy(
                            content = "This is a simulated AI response to: $text",
                            isThinking = false,
                            isStreaming = false
                        )
                    } else {
                        message
                    }
                }
                currentState.copy(
                    messages = updatedMessages,
                    isLoading = false
                )
            }
        }
    }

    fun onVoiceInputStart() {
        try {
            audioFile = File(context.cacheDir, "audio_record.mp3")
            audioFile?.deleteOnExit()

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            _state.update { it.copy(isRecording = true) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onVoiceInputStop() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            _state.update { it.copy(isRecording = false) }

            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                kotlinx.coroutines.delay(1000)
                sendMessage("This is a simulated voice transcription", true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onPlayResponse(messageId: String) {
        _state.update { it.copy(
            isPlaying = true,
            currentPlayingMessageId = messageId
        ) }
    }

    fun onStopPlayback() {
        _state.update { it.copy(
            isPlaying = false,
            currentPlayingMessageId = null
        ) }
    }

    fun onCopyMessage(messageId: String) {
    }

    fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
        mediaRecorder = null
        audioFile?.delete()
    }
} 