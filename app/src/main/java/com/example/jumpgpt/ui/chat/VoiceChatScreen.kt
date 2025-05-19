package com.example.jumpgpt.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jumpgpt.ui.chat.components.OrbitingLinesAnimation
import com.example.jumpgpt.ui.chat.components.AnimationState
import com.example.jumpgpt.ui.theme.Purple80
import kotlin.math.sin

@Composable
fun VoiceChatScreen(
    conversationId: String,
    onClose: () -> Unit,
    viewModel: VoiceChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showError by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        viewModel.setActiveConversation(conversationId)
    }

    LaunchedEffect(Unit) {
        viewModel.startVoiceActivation()
    }

    LaunchedEffect(state.transcribedText) {
        state.transcribedText?.let { text ->
            if (text.isNotBlank()) {
                viewModel.sendMessageAndSignalCompletion(text) {
                    viewModel.startVoiceActivation()
                }
            }
        }
    }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            showError = true
        } else {
            showError = false
        }
    }

    LaunchedEffect(showError) {
        if (!showError && state.error == null) {
            viewModel.startVoiceActivation()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!showError || state.error == null) {
                OrbitingLinesAnimation(
                    modifier = Modifier.size(200.dp),
                    color = Purple80,
                    state = when {
                        state.isPlayingResponse -> AnimationState.PLAYING
                        state.isProcessing -> AnimationState.PROCESSING
                        state.isRecording -> AnimationState.RECORDING
                        else -> AnimationState.LISTENING
                    },
                    audioLevel = state.audioLevel
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = when {
                    showError && state.error != null -> "Error occurred"
                    state.isPlayingResponse -> "Playing response..."
                    state.isProcessing -> "Processing..."
                    state.isRecording -> "Recording..."
                    state.isListeningForVoice -> "Listening..."
                    else -> "Ready"
                },
                color = Color.White,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )

            if (showError && state.error != null) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = state.error!!,
                    color = Color.Red,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.clearError()
                        viewModel.restartVoiceActivation()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Try Again")
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