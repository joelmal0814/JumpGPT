package com.example.jumpgpt.ui.chat.components

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.LineHeightStyle.Alignment as LineHeightAlignment
import androidx.compose.ui.text.style.LineHeightStyle.Trim as LineHeightTrim
import com.example.jumpgpt.domain.model.Message
import com.example.jumpgpt.domain.model.MessageRole
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "TokenStreaming"
private const val FADE_LENGTH = 20 // How many characters to fade out after the fade point
private const val CHARS_PER_SECOND = 40 // How many characters to reveal per second
private const val MESSAGE_FONT_SIZE = 16 // Font size in sp for all message text
private const val MESSAGE_LINE_HEIGHT = 23 // Line height in sp for all message text
private const val MARKDOWN_LINE_HEIGHT = 14 // Further reduced line height for markdown

private val UserMessageBackground = Color(0xFFF1F1F1) // Light gray for user messages

@Composable
fun StreamingText(
    text: String,
    fadePoint: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildAnnotatedString {
            // Fully visible text before fade point
            if (fadePoint > 0) {
                withStyle(SpanStyle(color = color)) {
                    append(text.substring(0, fadePoint))
                }
            }
            
            // Fading text after fade point
            val remainingText = text.substring(fadePoint)
            val fadeLength = minOf(FADE_LENGTH, remainingText.length)
            
            if (fadeLength > 0) {
                for (i in 0 until fadeLength) {
                    val alpha = 1f - (i.toFloat() / fadeLength)
                    withStyle(SpanStyle(color = color.copy(alpha = alpha))) {
                        append(remainingText[i])
                    }
                }
                
                // Any remaining text is fully transparent
                if (fadeLength < remainingText.length) {
                    withStyle(SpanStyle(color = color.copy(alpha = 0f))) {
                        append(remainingText.substring(fadeLength))
                    }
                }
            }
        },
        modifier = modifier,
        softWrap = true,
        fontSize = MESSAGE_FONT_SIZE.sp,
        lineHeight = MESSAGE_LINE_HEIGHT.sp,
        letterSpacing = 0.sp,
        style = MaterialTheme.typography.bodyMedium.copy(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightAlignment.Center,
                trim = LineHeightTrim.None
            )
        )
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isPlaying: Boolean = false,
    isLoadingTts: Boolean = false,
    onPlayClick: () -> Unit = {},
    onCopyClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUserMessage = message.role == MessageRole.USER
    val backgroundColor = if (isUserMessage) {
        UserMessageBackground
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isUserMessage) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    var targetFadePoint by remember { mutableStateOf(0) }
    var lastProcessedLength by remember { mutableStateOf(0) }
    var animationDuration by remember { mutableStateOf(0) }
    
    val animatedFadePoint by animateIntAsState(
        targetValue = targetFadePoint,
        animationSpec = tween(
            durationMillis = animationDuration,
            easing = LinearEasing
        ),
        label = "fade point"
    )
    
    LaunchedEffect(message.content) {
        if (message.isStreaming && message.content.length > lastProcessedLength) {
            val newContentLength = message.content.length - lastProcessedLength
            animationDuration = (newContentLength * 1000 / CHARS_PER_SECOND)
            Log.d(TAG, "New content length: $newContentLength, duration: $animationDuration ms")
            targetFadePoint = message.content.length
            lastProcessedLength = message.content.length
        } else if (!message.isStreaming) {
            targetFadePoint = 0
            lastProcessedLength = 0
            animationDuration = 0
        }
    }

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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor)
                    .padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    fontSize = MESSAGE_FONT_SIZE.sp,
                    lineHeight = MESSAGE_LINE_HEIGHT.sp,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightAlignment.Center,
                            trim = LineHeightTrim.None
                        )
                    ),
                    color = contentColor
                )
            }
        } else {
            Column {
                if (message.isThinking) {
                    ThinkingIndicator(
                        color = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(backgroundColor)
                            .padding(12.dp)
                    ) {
                        if (message.isStreaming) {
                            StreamingText(
                                text = message.content,
                                fadePoint = animatedFadePoint,
                                color = contentColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            NoHyphenMarkdownText(
                                markdown = message.content,
                                color = contentColor,
                                fontSize = MESSAGE_FONT_SIZE.sp,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightAlignment.Center,
                                        trim = LineHeightTrim.None
                                    )
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        if (!message.isThinking) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 20.dp)
                            ) {
                                IconButton(
                                    onClick = { onCopyClick(message.content) },
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
                                    if (isLoadingTts) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = contentColor.copy(alpha = 0.7f),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Stop playback" else "Play message",
                                            tint = if (isPlaying) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.7f),
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
            val infiniteTransition = rememberInfiniteTransition(label = "thinking")
            
            // Bounce animation
            val bounceAnim by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing,
                        delayMillis = index * 120
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bounce"
            )
            
            // Shimmer animation
            val shimmerAnim by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        easing = LinearEasing,
                        delayMillis = index * 120
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "shimmer"
            )

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .offset(y = (-6 * bounceAnim).dp)
                    .clip(CircleShape)
                    .background(
                        color = color.copy(alpha = shimmerAnim),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun NoHyphenMarkdownText(
    markdown: String,
    color: Color,
    fontSize: TextUnit,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    // Replace any soft hyphens or word break opportunities with spaces
    val processedMarkdown = markdown.replace("\u00AD", " ")
                                  .replace("\u200B", " ")
                                  .replace("-\n", " ")
                                  .replace("- ", " ")
    
    MarkdownText(
        markdown = processedMarkdown,
        color = color,
        fontSize = fontSize,
        style = style,
        modifier = modifier
    )
} 