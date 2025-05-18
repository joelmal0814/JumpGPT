package com.example.jumpgpt.ui.chat

import android.Manifest
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jumpgpt.domain.model.Message
import com.example.jumpgpt.ui.chat.components.ChatInput
import com.example.jumpgpt.ui.chat.components.MessageBubble
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.coroutineScope

private object ChatScreenDefaults {
    const val VERTICAL_MESSAGE_SPACING = 8
}

/**
 * Represents the UI state for chat screen gesture handling
 */
private data class ChatGestureState(
    val columnPosition: Offset = Offset.Zero,
    val chatInputTopY: Float = 0f,
    val dragStartY: Float? = null,
    val currentDragY: Float? = null
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    onVoiceChatClick: () -> Unit,
    onHistoryClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var gestureState by remember { mutableStateOf(ChatGestureState()) }

    val recordAudioPermissionState = rememberPermissionState(
        Manifest.permission.RECORD_AUDIO
    ) { isGranted ->
        if (isGranted) {
            onVoiceChatClick()
        } else {
            viewModel.showError("Microphone permission is required for voice chat")
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scrollState.animateScrollToItem(
                index = state.messages.size - 1,
                scrollOffset = 0
            )
            keyboardController?.hide()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ChatTopBar(
                onHistoryClick = onHistoryClick,
                title = state.conversation?.title ?: "JumpGPT",
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        gestureState = gestureState.copy(
                            columnPosition = coordinates.positionInRoot()
                        )
                    }
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    ChatMessageList(
                        messages = state.messages,
                        scrollState = scrollState,
                        isPlaying = state.isPlaying,
                        currentPlayingMessageId = state.currentPlayingMessageId,
                        onPlayClick = viewModel::onPlayResponse,
                        onCopyClick = { content ->
                            clipboardManager.setText(AnnotatedString(content))
                        },
                        onDragGesture = { startY, currentY ->
                            handleDragGesture(
                                startY = startY,
                                currentY = currentY,
                                chatInputTopY = gestureState.chatInputTopY,
                                keyboardController = keyboardController
                            )
                        },
                        columnPosition = gestureState.columnPosition
                    )
                }

                ChatInput(
                    text = state.inputText,
                    onTextChange = viewModel::onInputTextChanged,
                    onSendClick = {
                        viewModel.onSendMessage()
                        keyboardController?.hide()
                    },
                    isRecording = state.isRecording,
                    onVoiceClick = {
                        when (recordAudioPermissionState.status) {
                            PermissionStatus.Granted -> {
                                onVoiceChatClick()
                            }
                            is PermissionStatus.Denied -> {
                                if ((recordAudioPermissionState.status as PermissionStatus.Denied).shouldShowRationale) {
                                    viewModel.showError("Microphone access is needed for voice chat. Please grant the permission in Settings.")
                                } else {
                                    recordAudioPermissionState.launchPermissionRequest()
                                }
                            }
                        }
                    },
                    modifier = Modifier.onGloballyPositioned {
                        gestureState = gestureState.copy(
                            chatInputTopY = it.boundsInRoot().top
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    onHistoryClick: () -> Unit,
    title: String,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onHistoryClick,
                modifier = Modifier.semantics {
                    contentDescription = "View conversation history"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
        modifier = modifier
    )
}

@Composable
private fun ChatMessageList(
    messages: List<Message>,
    scrollState: LazyListState,
    isPlaying: Boolean,
    currentPlayingMessageId: String?,
    onPlayClick: (String) -> Unit,
    onCopyClick: (String) -> Unit,
    onDragGesture: (Float, Float) -> Boolean,
    columnPosition: Offset,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = scrollState,
        contentPadding = PaddingValues(vertical = ChatScreenDefaults.VERTICAL_MESSAGE_SPACING.dp),
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                coroutineScope {
                    detectDragGestures(
                        onDragGesture = onDragGesture,
                        columnPosition = columnPosition
                    )
                }
            }
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            MessageBubble(
                message = message,
                isPlaying = isPlaying && currentPlayingMessageId == message.id,
                onPlayClick = { onPlayClick(message.id) },
                onCopyClick = { onCopyClick(message.content) }
            )
        }
    }
}

private suspend fun PointerInputScope.detectDragGestures(
    onDragGesture: (Float, Float) -> Boolean,
    columnPosition: Offset
) {
    while(true) {
        awaitPointerEventScope {
            val down = awaitFirstDown()
            var startY = down.position.y
            var currentY = startY

            do {
                val event = awaitPointerEvent()
                val position = event.changes[0].position
                currentY = position.y
                
                val adjustedStartY = startY + columnPosition.y
                val adjustedCurrentY = currentY + columnPosition.y
                
                if (onDragGesture(adjustedStartY, adjustedCurrentY)) {
                    event.changes.forEach { it.consume() }
                    startY = 0f
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

private fun handleDragGesture(
    startY: Float,
    currentY: Float,
    chatInputTopY: Float,
    keyboardController: SoftwareKeyboardController?
): Boolean {
    return if (startY < chatInputTopY && currentY >= chatInputTopY) {
        keyboardController?.hide()
        true
    } else {
        false
    }
}