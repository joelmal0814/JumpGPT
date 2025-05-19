package com.example.jumpgpt.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun VoiceChatScreen(
    onClose: () -> Unit,
    onMessageSent: (String) -> Unit,
    viewModel: VoiceChatViewModel,
    activeConversationId: String
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Set the active conversation ID when the screen is created
    LaunchedEffect(activeConversationId) {
        viewModel.setActiveConversation(activeConversationId)
    }

    // Start voice activation when screen is opened
    LaunchedEffect(Unit) {
        viewModel.startVoiceActivation()
    }

    // Handle message sending and response playback
    LaunchedEffect(state.transcribedText) {
        state.transcribedText?.let { text ->
            if (text.isNotBlank() && !state.isRecording && !state.isProcessing) {
                viewModel.sendMessageAndSignalCompletion(text) {
                    // Don't close the screen, just start listening for the next message
                    viewModel.startVoiceActivation()
                }
            }
        }
    }

    // Handle errors
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            if (error.isNotBlank()) {
                // Show error for 3 seconds then clear it
                delay(3000)
                viewModel.clearError()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Close button
        IconButton(
            onClick = {
                viewModel.stopResponsePlayback()
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close voice chat",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Voice visualization and controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VoiceWaveform(
                isRecording = state.isRecording,
                isListeningForVoice = state.isListeningForVoice,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Status text
            Text(
                text = when {
                    state.error != null -> state.error!!
                    state.isProcessing -> "Processing..."
                    state.isPlayingResponse -> "Playing response..."
                    state.isRecording -> "${state.recordingDuration.milliseconds}"
                    state.isListeningForVoice -> "Listening for voice..."
                    state.transcribedText != null -> state.transcribedText!!
                    else -> "Ready to listen..."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.error != null) MaterialTheme.colorScheme.error 
                       else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Manual record button (for when voice activation fails)
                if (!state.isRecording && !state.isListeningForVoice) {
                    IconButton(
                        onClick = { viewModel.startRecording() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        enabled = !state.isProcessing && !state.isPlayingResponse
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Start recording",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Stop/Play response button
                if (state.isPlayingResponse) {
                    IconButton(
                        onClick = { viewModel.stopResponsePlayback() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                        enabled = true
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Stop response",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceWaveform(
    isRecording: Boolean,
    isListeningForVoice: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val waveColor = remember(color) { color }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (isRecording || isListeningForVoice) {
            // Draw multiple sine waves with different phases and amplitudes
            for (i in 0..2) {
                val amplitude = (height / 4) * (1 - i * 0.2f)
                val frequency = 1 + i * 0.5f

                for (x in 0..width.toInt() step 5) {
                    val xRatio = x / width
                    val y = centerY + (sin(xRatio * 2 * Math.PI * frequency + phase + i) * amplitude).toFloat()

                    drawCircle(
                        color = waveColor.copy(alpha = 0.6f - i * 0.2f),
                        radius = 2.dp.toPx(),
                        center = Offset(x.toFloat(), y)
                    )
                }
            }
        } else {
            // Draw a static line when not recording
            drawLine(
                color = waveColor.copy(alpha = 0.3f),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
} 