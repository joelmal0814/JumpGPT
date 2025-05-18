package com.example.jumpgpt.ui.chat

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jumpgpt.domain.model.VoiceChatState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VoiceChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceChatState())
    val state: StateFlow<VoiceChatState> = _state

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var durationJob: Job? = null

    fun startRecording() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                handleError("Microphone is currently in use by another application")
                return
            }

            audioFile = File(context.cacheDir, "voice_record_${System.currentTimeMillis()}.mp3")
            audioFile?.deleteOnExit()

            mediaRecorder?.release()
            mediaRecorder = null

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

            _state.update { it.copy(
                isRecording = true,
                recordingDuration = 0L,
                error = null
            ) }

            startDurationCounter()
        } catch (e: Exception) {
            handleError("Failed to start recording: ${e.localizedMessage ?: "Unknown error"}")
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        try {
            durationJob?.cancel()
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            _state.update { it.copy(
                isRecording = false,
                isProcessing = true
            ) }

            viewModelScope.launch {
                delay(1500)
                _state.update { it.copy(
                    transcribedText = "This is a simulated voice transcription.",
                    isProcessing = false
                ) }
            }
        } catch (e: Exception) {
            handleError("Failed to stop recording: ${e.localizedMessage}")
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
            }
        }
    }

    private fun handleError(message: String) {
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
        mediaRecorder?.release()
        mediaRecorder = null
        audioFile?.delete()
    }
} 