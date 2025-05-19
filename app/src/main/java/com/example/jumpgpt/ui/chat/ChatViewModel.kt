package com.example.jumpgpt.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jumpgpt.data.repository.ChatRepository
import com.example.jumpgpt.domain.model.ChatState
import com.example.jumpgpt.domain.model.Conversation
import com.example.jumpgpt.domain.model.Message
import com.example.jumpgpt.domain.model.MessageRole
import com.example.jumpgpt.util.AudioPlayer
import com.example.jumpgpt.util.TimeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

private const val TAG = "TokenStreaming"

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val audioPlayer: AudioPlayer,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var streamStartTime: Long = 0
    private var currentConversationId: String? = null

    init {
        viewModelScope.launch {
            audioPlayer.isPlaying.collect { isPlaying ->
                _state.update { it.copy(isPlaying = isPlaying) }
            }
        }
        viewModelScope.launch {
            audioPlayer.currentPlayingId.collect { messageId ->
                _state.update { it.copy(currentPlayingMessageId = messageId) }
            }
        }

        // Load conversation if ID is provided
        savedStateHandle.get<String>("conversationId")?.let { id ->
            if (id.isNotEmpty()) {
                loadConversation(id)
            }
        }
    }

    fun loadConversation(id: String) {
        viewModelScope.launch {
            try {
                if (id.isEmpty()) {
                    // Empty ID means new chat
                    currentConversationId = null
                    _state.update { it.copy(
                        conversation = null,
                        messages = emptyList(),
                        inputText = ""
                    ) }
                    return@launch
                }

                val conversation = chatRepository.getConversationById(id)
                if (conversation != null) {
                    currentConversationId = conversation.id
                    _state.update { it.copy(
                        conversation = conversation,
                        messages = conversation.messages
                    ) }
                }
            } catch (e: Exception) {
                showError("Failed to load conversation")
            }
        }
    }

    private fun saveConversation(messages: List<Message>) {
        viewModelScope.launch {
            try {
                val conversation = _state.value.conversation
                if (conversation != null) {
                    // Update existing conversation
                    chatRepository.updateConversation(conversation.copy(
                        messages = messages,
                        lastMessage = messages.lastOrNull { !it.isThinking }?.content,
                        lastUpdated = TimeUtil.now()
                    ))
                } else if (messages.size >= 2) {
                    // Create new conversation after first exchange
                    val id = chatRepository.createConversation(messages)
                    currentConversationId = id
                    loadConversation(id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving conversation", e)
            }
        }
    }

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
        streamStartTime = System.currentTimeMillis()
        Log.d(TAG, "ViewModel: Starting message send at 0ms")
        
        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            content = text,
            role = MessageRole.USER,
            timestamp = TimeUtil.now(),
            isVoiceMessage = isVoiceMessage
        )

        val updatedMessages = _state.value.messages + userMessage
        _state.update { currentState ->
            currentState.copy(
                messages = updatedMessages,
                inputText = "",
                isLoading = true
            )
        }

        viewModelScope.launch {
            Log.d(TAG, "ViewModel: Creating assistant message at ${System.currentTimeMillis() - streamStartTime}ms")
            val aiMessage = Message(
                id = UUID.randomUUID().toString(),
                content = "",
                role = MessageRole.ASSISTANT,
                timestamp = TimeUtil.now(),
                isThinking = true
            )

            val messagesWithAi = updatedMessages + aiMessage
            _state.update { it.copy(
                messages = messagesWithAi
            ) }

            Log.d(TAG, "ViewModel: Starting to collect response at ${System.currentTimeMillis() - streamStartTime}ms")
            chatRepository.sendMessage(messagesWithAi).collect { response ->
                val currentTime = System.currentTimeMillis() - streamStartTime
                Log.d(TAG, "ViewModel: Update received at ${currentTime}ms - isStreaming: ${response.isStreaming}, content length: ${response.content.length}")
                
                val finalMessages = _state.value.messages.map { message ->
                    if (message.id == aiMessage.id) {
                        response.copy(
                            isThinking = false,
                            isStreaming = response.isStreaming
                        )
                    } else {
                        message
                    }
                }
                
                _state.update { currentState ->
                    currentState.copy(
                        messages = finalMessages,
                        isLoading = false
                    )
                }

                // Save conversation after response is complete
                if (!response.isStreaming) {
                    saveConversation(finalMessages)
                }
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

    fun onPlayClick(messageId: String) {
        val message = _state.value.messages.find { it.id == messageId }
        if (message != null) {
            if (_state.value.isPlaying && _state.value.currentPlayingMessageId == messageId) {
                audioPlayer.stop()
            } else {
                viewModelScope.launch {
                    try {
                        _state.update { it.copy(loadingTtsMessageId = messageId) }
                        val audioFile = chatRepository.textToSpeech(message.content, messageId)
                        audioPlayer.playAudio(audioFile, messageId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error playing TTS", e)
                        showError("Failed to play audio")
                    } finally {
                        _state.update { it.copy(loadingTtsMessageId = null) }
                    }
                }
            }
        }
    }

    fun onCopyMessage(messageId: String) {
    }

    fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun createNewConversation(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Create a new conversation with a default title
                val conversationId = chatRepository.createConversation(emptyList())
                // Load the new conversation
                loadConversation(conversationId)
                // Call the completion callback with the new ID
                onComplete(conversationId)
            } catch (e: Exception) {
                showError("Failed to create new conversation")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
        mediaRecorder = null
        audioFile?.delete()
        audioPlayer.release()
    }
} 