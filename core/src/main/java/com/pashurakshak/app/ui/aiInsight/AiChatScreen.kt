package com.pashurakshak.app.ui.aiInsight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VetAiChatScreen(
    reportId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vetViewModel: AiChatViewModel = viewModel(key = "vet-ai-chat-${reportId.orEmpty()}") {
        AiChatViewModel(
            audience = AiChatAudience.VET,
            initialReportId = reportId,
        )
    }
    AiChatScreen(
        onBack = onBack,
        modifier = modifier,
        viewModel = vetViewModel,
    )
}

@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiChatViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage = state.error
    val isVet = state.audience == AiChatAudience.VET
    val headerTitle = if (isVet) "Vet AI Assistant" else "AI Health Assistant"
    val headerSubtitle = when {
        !isVet -> "PashuRakshak AI"
        !state.activeReportLabel.isNullOrBlank() -> "Case: ${state.activeReportLabel}"
        !state.activeReportId.isNullOrBlank() -> "Case-specific consultation"
        else -> "Clinical decision support"
    }
    val inputPlaceholder = when {
        !isVet -> "Ask about your animal's health..."
        !state.activeReportId.isNullOrBlank() -> "Ask about this case..."
        else -> "Ask a veterinary question..."
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val bottomPaddingPx = remember { with(density) { ChatListBottomPadding.toPx() } }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .collect { listState.scrollToLatest(bottomPaddingPx) }
    }

    LaunchedEffect(state.isTyping, state.typingMessageId) {
        if (!state.isTyping) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.lastOrNull { it.index == info.totalItemsCount - 1 }?.size
        }.collect {
            listState.snapToLatest(bottomPaddingPx)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .collect {
                delay(150L)
                listState.scrollToLatest(bottomPaddingPx)
            }
    }

    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatHistoryDrawer(
                state = state,
                onNewChat = {
                    viewModel.startNewChat()
                    closeDrawer()
                },
                onOpenConversation = { id ->
                    viewModel.openConversation(id)
                    closeDrawer()
                },
                onRetry = viewModel::refreshSessions,
            )
        },
        modifier = modifier.fillMaxSize(),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ChatTopBar(
                    onBack = onBack,
                    title = headerTitle,
                    subtitle = headerSubtitle,
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Chat history",
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = ChatListBottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.isLoadingConversation && state.messages.isEmpty()) {
                        item(key = "loading") { CenterProgress() }
                    } else if (state.messages.isEmpty()) {
                        item(key = "empty") {
                            if (errorMessage == null) {
                                EmptyChatState(
                                    title = when {
                                        !isVet -> "Ask me anything"
                                        !state.activeReportId.isNullOrBlank() -> "Ask about this case"
                                        else -> "Ask a clinical question"
                                    },
                                    message = when {
                                        !isVet -> "Get preventive care and first-aid guidance for your animals."
                                        !state.activeReportId.isNullOrBlank() ->
                                            "Use this case's details for differentials, diagnostics, and next steps."
                                        else -> "Get veterinary decision support for any case or clinical question."
                                    },
                                )
                            } else {
                                ChatErrorState(errorMessage)
                            }
                        }
                    }
                    items(state.messages, key = { it.id }) { message ->
                        if (message.role == "user") {
                            UserMessageBubble(text = message.text)
                        } else {
                            AiMessageBubble(
                                text = message.text,
                                isTyping = state.isTyping && message.id == state.typingMessageId,
                            )
                        }
                    }
                    if (state.isSending) {
                        item(key = "typing") { TypingIndicatorBubble() }
                    }
                }
                ChatInputBar(
                    inputText = state.inputText,
                    onInputChanged = viewModel::onInputChanged,
                    onSend = viewModel::sendMessage,
                    isSending = state.isSending || state.isTyping || state.isLoadingConversation,
                    placeholder = inputPlaceholder,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun ChatHistoryDrawer(
    state: AiChatUiState,
    onNewChat: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRetry: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = if (state.audience == AiChatAudience.VET) "Vet AI Chats" else "AI Chats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (state.audience == AiChatAudience.VET) {
                        "Your saved consults and case chats"
                    } else {
                        "Your saved conversations"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(
                onClick = onNewChat,
                enabled = !state.isSending && !state.isTyping,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AddComment,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "New chat")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            when {
                state.isLoadingSessions && state.sessions.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }

                state.sessionsError != null && state.sessions.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.sessionsError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onRetry) {
                            Text(text = "Retry")
                        }
                    }
                }

                state.sessions.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No conversations yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start chatting and it will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(state.sessions, key = { it.id }) { session ->
                            NavigationDrawerItem(
                                label = {
                                    Column {
                                        if (!session.reportLabel.isNullOrBlank()) {
                                            Text(
                                                text = "CASE · ${session.reportLabel}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Text(
                                            text = session.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (session.id == state.activeConversationId) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            },
                                        )
                                        Text(
                                            text = formatSessionTime(session.updatedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                selected = session.id == state.activeConversationId,
                                onClick = { onOpenConversation(session.id) },
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Could not load conversation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatSessionTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        .format(Date(now)) == SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestamp))
    val pattern = if (sameDay) "h:mm a" else "d MMM"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

