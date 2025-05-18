package com.example.jumpgpt.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VoiceChatScreen(
    onClose: () -> Unit,
    onSendVoiceMessage: (String) -> Unit,
    viewModel: VoiceChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Close button
        IconButton(
            onClick = onClose,
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
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Recording duration or status text
            Text(
                text = when {
                    state.error != null -> state.error!!
                    state.isProcessing -> "Processing..."
                    state.isRecording -> "${state.recordingDuration.milliseconds}"
                    state.transcribedText != null -> state.transcribedText!!
                    else -> "Tap to start speaking"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (state.error != null) MaterialTheme.colorScheme.error 
                       else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Record button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.isRecording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (state.isRecording) {
                            viewModel.stopRecording()
                        } else {
                            viewModel.startRecording()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    enabled = !state.isProcessing
                ) {
                    Icon(
                        imageVector = if (state.isRecording) Icons.Default.Send else Icons.Default.Mic,
                        contentDescription = if (state.isRecording) "Stop recording" else "Start recording",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Send button (only show when we have transcribed text)
            if (state.transcribedText != null && !state.isRecording && !state.isProcessing) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        onSendVoiceMessage(state.transcribedText!!)
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send Message")
                }
            }
        }
    }
}

@Composable
fun VoiceWaveform(
    isRecording: Boolean,
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

        if (isRecording) {
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