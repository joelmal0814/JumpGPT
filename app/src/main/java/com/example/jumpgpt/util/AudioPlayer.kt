package com.example.jumpgpt.util

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudioPlayer"

@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPlayingId = MutableStateFlow<String?>(null)
    val currentPlayingId: StateFlow<String?> = _currentPlayingId.asStateFlow()

    fun playAudio(audioFile: File, messageId: String) {
        try {
            // If same audio is already playing, stop it
            if (currentPlayingId.value == messageId && isPlaying.value) {
                stop()
                return
            }
            
            // Stop any currently playing audio
            stop()
            
            // Create and configure new MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    resetState()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    resetState()
                    true
                }
                prepare()
                start()
            }
            
            currentFile = audioFile
            _isPlaying.value = true
            _currentPlayingId.value = messageId
            
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            resetState()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
            mediaPlayer = null
            resetState()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio", e)
            resetState()
        }
    }

    private fun resetState() {
        _isPlaying.value = false
        _currentPlayingId.value = null
        mediaPlayer = null
    }

    fun release() {
        stop()
        currentFile = null
    }
} 