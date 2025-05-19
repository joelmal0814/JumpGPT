package com.example.jumpgpt.ui.chat

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jumpgpt.domain.model.VoiceChatState
import com.example.jumpgpt.data.repository.ChatRepository
import com.example.jumpgpt.util.AudioPlayer
import com.example.jumpgpt.domain.model.Message
import com.example.jumpgpt.domain.model.MessageRole
import com.example.jumpgpt.util.TimeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

private const val SILENCE_THRESHOLD = 2000L
private const val MAX_RECORDING_DURATION = 30000L
private const val VOICE_ACTIVATION_THRESHOLD = 2000
private const val VOICE_CHECK_INTERVAL = 100L
private const val MIN_RECORDING_DURATION = 1000L
private const val MIN_VOICE_ACTIVATION_DURATION = 300L

@HiltViewModel
class VoiceChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceChatState())
    val state: StateFlow<VoiceChatState> = _state

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var tempAudioFile: File? = null
    private var durationJob: Job? = null
    private var silenceDetectionJob: Job? = null
    private var voiceActivationJob: Job? = null
    private var lastAudioLevel: Int = 0
    private var silenceStartTime: Long = 0
    private var isListeningForVoice = false
    private var activeConversationId: String? = null
    private var recordingStartTime: Long = 0

    init {
        viewModelScope.launch {
            audioPlayer.isPlaying.collect { isPlaying ->
                _state.update { it.copy(isPlayingResponse = isPlaying) }
            }
        }
    }

    fun setActiveConversation(conversationId: String) {
        activeConversationId = conversationId
    }

    fun startVoiceActivation() {
        if (isListeningForVoice) return
        
        isListeningForVoice = true
        _state.update { it.copy(isListeningForVoice = true) }
        
        audioFile = File(context.cacheDir, "voice_record_${System.currentTimeMillis()}.mp3")
        audioFile?.deleteOnExit()
        
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            
            recordingStartTime = System.currentTimeMillis()
            _state.update { it.copy(
                isRecording = true,
                recordingDuration = 0L,
                error = null
            ) }
            
            startDurationCounter()
            
        } catch (e: Exception) {
            handleError("Failed to initialize recording: ${e.localizedMessage}")
            return
        }
        
        voiceActivationJob?.cancel()
        voiceActivationJob = viewModelScope.launch {
            var voiceStartTime: Long? = null
            
            while (isListeningForVoice) {
                delay(VOICE_CHECK_INTERVAL)
                val currentLevel = mediaRecorder?.maxAmplitude ?: 0
                
                if (currentLevel >= VOICE_ACTIVATION_THRESHOLD) {
                    if (voiceStartTime == null) {
                        voiceStartTime = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - voiceStartTime >= MIN_VOICE_ACTIVATION_DURATION) {
                        isListeningForVoice = false
                        _state.update { it.copy(isListeningForVoice = false) }
                        startSilenceDetection()
                        break
                    }
                } else {
                    if (voiceStartTime != null && System.currentTimeMillis() - voiceStartTime < MIN_VOICE_ACTIVATION_DURATION) {
                        stopRecording()
                        voiceStartTime = null
                    }
                }
            }
        }
    }

    fun stopVoiceActivation() {
        isListeningForVoice = false
        voiceActivationJob?.cancel()
        _state.update { it.copy(isListeningForVoice = false) }
        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null
    }

    fun startRecording() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                handleError("Microphone is currently in use by another application")
                return
            }

            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    // Ignore stop errors
                }
                release()
            }
            mediaRecorder = null

            audioFile = File(context.cacheDir, "voice_record_${System.currentTimeMillis()}.mp3")
            audioFile?.deleteOnExit()

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    setOutputFile(audioFile?.absolutePath)
                    prepare()
                    start()
                } catch (e: Exception) {
                    release()
                    throw e
                }
            }

            recordingStartTime = System.currentTimeMillis()
            _state.update { it.copy(
                isRecording = true,
                recordingDuration = 0L,
                error = null
            ) }

            startDurationCounter()
            startSilenceDetection()
        } catch (e: Exception) {
            handleError("Failed to start recording: ${e.localizedMessage ?: "Unknown error"}")
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        try {
            durationJob?.cancel()
            silenceDetectionJob?.cancel()
            voiceActivationJob?.cancel()
            
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {
                Log.e("VoiceChatViewModel", "Error stopping recorder", e)
            }
            mediaRecorder = null

            _state.update { it.copy(
                isRecording = false,
                isProcessing = true
            ) }

            viewModelScope.launch {
                try {
                    val currentAudioFile = audioFile
                    if (currentAudioFile == null) {
                        handleError("No audio file was created")
                        return@launch
                    }

                    if (!currentAudioFile.exists()) {
                        handleError("Audio file was not saved properly")
                        return@launch
                    }

                    if (currentAudioFile.length() == 0L) {
                        handleError("No audio was recorded")
                        return@launch
                    }

                    val transcribedText = chatRepository.speechToText(currentAudioFile)
                    if (transcribedText.isBlank()) {
                        handleError("Could not transcribe audio")
                        return@launch
                    }

                    _state.update { it.copy(
                        transcribedText = transcribedText,
                        isProcessing = false
                    ) }
                } catch (e: Exception) {
                    handleError("Failed to transcribe audio: ${e.localizedMessage}")
                }
            }
        } catch (e: Exception) {
            handleError("Failed to stop recording: ${e.localizedMessage}")
        }
    }

    suspend fun sendMessageAndSignalCompletion(text: String, onComplete: () -> Unit) {
        try {
            _state.update { it.copy(isProcessing = true) }
            
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                content = text,
                role = MessageRole.USER,
                timestamp = TimeUtil.now(),
                isVoiceMessage = true
            )

            val assistantMessage = Message(
                id = UUID.randomUUID().toString(),
                content = "",
                role = MessageRole.ASSISTANT,
                timestamp = TimeUtil.now(),
                isThinking = true
            )

            val conversationId = activeConversationId ?: throw IllegalStateException("No active conversation")
            val conversation = chatRepository.getConversationById(conversationId)
                ?: throw IllegalStateException("Active conversation not found")

            val updatedMessages = conversation.messages + userMessage + assistantMessage
            chatRepository.updateConversation(conversation.copy(messages = updatedMessages))
            
            chatRepository.sendMessage(updatedMessages, isVoiceMessage = true)
                .catch { e ->
                    Log.e("VoiceChatViewModel", "Error in message flow", e)
                    emit(Message(
                        id = "error_${UUID.randomUUID()}",
                        content = "Error: ${e.message}",
                        role = MessageRole.ASSISTANT,
                        timestamp = TimeUtil.now(),
                        isError = true
                    ))
                }
                .collect { response ->
                    if (response.isError) {
                        handleError(response.content)
                        return@collect
                    }

                    val finalMessages = updatedMessages.map { message ->
                        if (message.id == assistantMessage.id) {
                            response.copy(
                                isThinking = false,
                                isStreaming = false
                            )
                        } else {
                            message
                        }
                    }
                    
                    chatRepository.updateConversation(conversation.copy(
                        messages = finalMessages,
                        lastMessage = response.content,
                        lastUpdated = TimeUtil.now()
                    ))

                    try {
                        val audioFile = chatRepository.textToSpeech(response.content, response.id)
                        if (audioFile.exists()) {
                            audioPlayer.playAudio(audioFile, response.id)
                            
                            while (audioPlayer.isPlaying.value) {
                                delay(100)
                            }
                        } else {
                            Log.e("VoiceChatViewModel", "Failed to generate audio file for response")
                            handleError("Failed to generate speech from response")
                        }
                    } catch (e: Exception) {
                        Log.e("VoiceChatViewModel", "Error playing response", e)
                        handleError("Failed to play response: ${e.localizedMessage}")
                    }
                }

            onComplete()
        } catch (e: Exception) {
            Log.e("VoiceChatViewModel", "Error sending message", e)
            handleError("Failed to send message: ${e.localizedMessage}")
        } finally {
            _state.update { it.copy(isProcessing = false) }
        }
    }

    fun stopResponsePlayback() {
        audioPlayer.stop()
    }

    fun restartVoiceActivation() {
        if (!_state.value.isProcessing) {
            startVoiceActivation()
        }
    }

    private fun startDurationCounter() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            var duration = 0L
            while (true) {
                delay(1000)
                duration += 1000
                _state.update { it.copy(recordingDuration = duration) }
                
                if (duration >= MAX_RECORDING_DURATION) {
                    stopRecording()
                    break
                }
            }
        }
    }

    private fun startSilenceDetection() {
        silenceDetectionJob?.cancel()
        silenceDetectionJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val currentLevel = mediaRecorder?.maxAmplitude ?: 0
                val currentTime = System.currentTimeMillis()
                
                if (currentTime - recordingStartTime < MIN_RECORDING_DURATION) {
                    lastAudioLevel = currentLevel
                    continue
                }
                
                if (currentLevel < VOICE_ACTIVATION_THRESHOLD) {
                    if (lastAudioLevel >= VOICE_ACTIVATION_THRESHOLD) {
                        silenceStartTime = currentTime
                    } else if (currentTime - silenceStartTime > SILENCE_THRESHOLD) {
                        stopRecording()
                        break
                    }
                } else {
                    silenceStartTime = currentTime
                }
                
                lastAudioLevel = currentLevel
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun handleError(message: String) {
        Log.e("VoiceChatViewModel", "Error: $message")
        _state.update { it.copy(
            error = message,
            isRecording = false,
            isProcessing = false
        ) }
        mediaRecorder?.release()
        mediaRecorder = null
        audioFile?.delete()
    }

    override fun onCleared() {
        super.onCleared()
        durationJob?.cancel()
        silenceDetectionJob?.cancel()
        voiceActivationJob?.cancel()
        mediaRecorder?.release()
        mediaRecorder = null
        audioFile?.delete()
        tempAudioFile?.delete()
        audioPlayer.release()
    }
} 