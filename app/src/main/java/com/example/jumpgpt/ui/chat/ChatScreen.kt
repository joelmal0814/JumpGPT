package com.example.jumpgpt.ui.chat

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.jumpgpt.domain.model.Conversation
import com.example.jumpgpt.domain.model.Message
import com.example.jumpgpt.ui.chat.components.ChatInput
import com.example.jumpgpt.ui.chat.components.MessageBubble
import com.example.jumpgpt.ui.history.HistoryViewModel
import com.example.jumpgpt.util.TimeUtil
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import android.view.inputmethod.InputMethodManager
import android.app.Activity
import android.content.Context
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.focusProperties
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.jumpgpt.ui.chat.components.SoundWaveIcon

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
    onVoiceChatClick: (String) -> Unit,
    onHistoryClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
    historyViewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val historyState by historyViewModel.state.collectAsState()
    val scrollState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var isDrawerOpen by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(
        initialValue = if (isDrawerOpen) DrawerValue.Open else DrawerValue.Closed
    )
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    
    var drawerWidth by remember { mutableStateOf(320.dp) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    
    LaunchedEffect(imeVisible) {
        drawerWidth = if (imeVisible) {
            configuration.screenWidthDp.dp
        } else {
            320.dp
        }
    }

    // Watch for drawer target state changes to handle keyboard dismissal
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Closed) {
            keyboardController?.hide()
            focusManager.clearFocus()
            drawerWidth = 320.dp // Reset drawer width
        }
    }

    // Handle focus and keyboard when drawer state changes
    LaunchedEffect(drawerState.currentValue, drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) {
            // Clear focus before drawer animation starts
            focusManager.clearFocus()
            keyboardController?.hide()
        } else if (drawerState.targetValue == DrawerValue.Closed) {
            // Clear focus and hide keyboard when drawer is closing
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    // Keep drawer state in sync with isDrawerOpen
    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen) {
            focusManager.clearFocus()
            keyboardController?.hide()
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    // Keep isDrawerOpen in sync with drawer state
    LaunchedEffect(drawerState.currentValue) {
        isDrawerOpen = drawerState.currentValue == DrawerValue.Open
    }

    var gestureState by remember { mutableStateOf(ChatGestureState()) }

    val recordAudioPermissionState = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO
    ) { isGranted ->
        if (isGranted) {
            val conversationId = state.conversation?.id ?: ""
            onVoiceChatClick(conversationId)
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

    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(0.dp)
            ) {
                HistoryDrawerContent(
                    conversations = historyState.conversations,
                    isLoading = historyState.isLoading,
                    currentConversationId = state.conversation?.id,
                    onConversationClick = { conversationId ->
                        viewModel.loadConversation(conversationId)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteConversation = historyViewModel::deleteConversation,
                    drawerState = drawerState,
                    onSearchFocusChanged = { isFocused ->
                        drawerWidth = if (isFocused) configuration.screenWidthDp.dp else 320.dp
                    }
                )
            }
        },
        drawerState = drawerState,
        gesturesEnabled = true,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                ChatTopBar(
                    onHistoryClick = {
                        scope.launch {
                            keyboardController?.hide()
                            if (drawerState.isClosed) {
                                drawerState.open()
                            } else {
                                drawerState.close()
                            }
                        }
                    },
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
                            onPlayClick = viewModel::onPlayClick,
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
                            columnPosition = gestureState.columnPosition,
                            loadingTtsMessageId = state.loadingTtsMessageId
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
                        enabled = !isDrawerOpen,
                        onVoiceClick = {
                            when (recordAudioPermissionState.status) {
                                PermissionStatus.Granted -> {
                                    val conversationId = state.conversation?.id ?: ""
                                    onVoiceChatClick(conversationId)
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
}

@Composable
private fun ChatHistoryIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(
        modifier = modifier
            .size(24.dp)
            .padding(2.dp)
    ) {
        val strokeWidth = 2.dp.toPx()
        val spacing = 5.dp.toPx()
        val startY = (size.height - 2 * spacing) / 2
        
        // Draw three lines with decreasing length
        drawLine(
            color = tint,
            start = Offset(0f, startY),
            end = Offset(size.width, startY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(0f, startY + spacing),
            end = Offset(size.width * 0.75f, startY + spacing),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(0f, startY + 2 * spacing),
            end = Offset(size.width * 0.5f, startY + 2 * spacing),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                ChatHistoryIcon(
                    tint = MaterialTheme.colorScheme.onSurface
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
    loadingTtsMessageId: String? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = scrollState,
        contentPadding = PaddingValues(vertical = ChatScreenDefaults.VERTICAL_MESSAGE_SPACING.dp),
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var startY = 0f
                    var lastY = 0f
                    var hasStartedGesture = false

                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Press -> {
                                startY = event.changes[0].position.y
                                lastY = startY
                                hasStartedGesture = false
                            }
                            PointerEventType.Move -> {
                                val currentY = event.changes[0].position.y
                                val deltaY = currentY - lastY
                                
                                // Only start checking for keyboard dismissal if moving downward
                                if (deltaY > 0 && !hasStartedGesture) {
                                    val adjustedStartY = startY + columnPosition.y
                                    val adjustedCurrentY = currentY + columnPosition.y
                                    
                                    if (onDragGesture(adjustedStartY, adjustedCurrentY)) {
                                        hasStartedGesture = true
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                
                                lastY = currentY
                            }
                            PointerEventType.Release -> {
                                hasStartedGesture = false
                            }
                        }
                    }
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
                isLoadingTts = loadingTtsMessageId == message.id,
                onPlayClick = { onPlayClick(message.id) },
                onCopyClick = { onCopyClick(message.content) }
            )
        }
    }
}

private fun handleDragGesture(
    startY: Float,
    currentY: Float,
    chatInputTopY: Float,
    keyboardController: SoftwareKeyboardController?
): Boolean {
    val dragDistance = currentY - startY
    val dragThreshold = 20f // Add a small threshold to ensure intentional drag
    
    return if (startY < chatInputTopY && 
               currentY >= chatInputTopY && 
               dragDistance > dragThreshold) {
        keyboardController?.hide()
        true
    } else {
        false
    }
}

@Composable
private fun HistoryDrawerContent(
    conversations: List<Conversation>,
    isLoading: Boolean,
    currentConversationId: String?,
    onConversationClick: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState,
    onSearchFocusChanged: (Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var isSearchEnabled by remember { mutableStateOf(true) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var canSearchFocus by remember { mutableStateOf(false) }
    
    // Handle drawer state changes
    LaunchedEffect(drawerState.targetValue) {
        when (drawerState.targetValue) {
            DrawerValue.Open -> {
                // Initially prevent focus
                canSearchFocus = false
                // Allow focus after a short delay
                delay(300)
                canSearchFocus = true
            }
            DrawerValue.Closed -> {
                // Reset state when drawer is closing
                canSearchFocus = false
                isSearchFocused = false
                searchQuery = ""
                onSearchFocusChanged(false)
            }
        }
    }

    val handleCancel = remember<() -> Unit> {
        {
            scope.launch {
                // Try to hide keyboard multiple ways
                keyboardController?.hide()
                val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                val currentFocus = (context as? Activity)?.window?.currentFocus
                currentFocus?.let { view ->
                    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
                }
                
                delay(50) // Short delay to ensure keyboard starts hiding
                isSearchEnabled = false
                searchQuery = ""
                focusManager.clearFocus()
                isSearchFocused = false
                onSearchFocusChanged(false) // Notify parent about focus change
                delay(100)
                isSearchEnabled = true
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                enabled = isSearchEnabled,
                placeholder = {
                    Text(
                        "Search",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            lineHeight = 16.sp
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .onFocusChanged { focusState -> 
                        if (isSearchEnabled) {
                            isSearchFocused = focusState.isFocused
                            onSearchFocusChanged(focusState.isFocused) // Notify parent about focus change
                        }
                    }
                    .focusProperties { 
                        canFocus = canSearchFocus
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 16.sp
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
            )
            
            AnimatedContent(
                targetState = searchQuery.isNotEmpty() || isSearchFocused,
                transitionSpec = { 
                    fadeIn() togetherWith fadeOut()
                }
            ) { showCancel ->
                if (showCancel) {
                    TextButton(
                        onClick = handleCancel,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    IconButton(
                        onClick = { onConversationClick("") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "New Chat",
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No conversations yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(0.dp)
            ) {
                items(
                    items = conversations.filter { 
                        searchQuery.isEmpty() || it.title.contains(searchQuery, ignoreCase = true)
                    },
                    key = { it.id }
                ) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        isSelected = conversation.id == currentConversationId,
                        onConversationClick = onConversationClick,
                        onDelete = onDeleteConversation,
                        modifier = Modifier.height(40.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    onConversationClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Conversation") },
            text = { Text("Are you sure you want to delete this conversation?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(conversation.id)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ListItem(
        modifier = modifier
            .clickable { onConversationClick(conversation.id) }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .padding(horizontal = 4.dp),
        headlineContent = {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 0.sp,
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp)
            )
        },
        trailingContent = {
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete conversation",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}