package com.example.jumpgpt.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jumpgpt.domain.model.Message
import com.example.jumpgpt.domain.model.MessageRole
import dev.jeziellago.compose.markdowntext.MarkdownText

private val UserMessageBackground = Color(0xFFF1F1F1) // Light gray for user messages

@Composable
fun MessageBubble(
    message: Message,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUserMessage = message.role == MessageRole.USER
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isUserMessage) 64.dp else 24.dp,
                end = if (isUserMessage) 24.dp else 64.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        if (isUserMessage) {
            // User message with light gray bubble
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(UserMessageBackground)
                    .padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )
            }
        } else {
            // AI message with no bubble
            Column {
                if (message.isThinking) {
                    ThinkingIndicator(
                        color = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        MarkdownText(
                            markdown = message.content,
                            color = contentColor,
                            fontSize = 16.sp
                        )
                        
                        if (!message.isThinking) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 20.dp)
                            ) {
                                IconButton(
                                    onClick = onCopyClick,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy message",
                                        tint = contentColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = onPlayClick,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Stop playback" else "Play message",
                                        tint = contentColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.4f))
            )
        }
    }
} 