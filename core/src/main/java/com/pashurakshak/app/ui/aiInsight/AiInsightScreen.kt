package com.pashurakshak.app.ui.aiInsight

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInsightScreen(
    reportId: String,
    aiAdvisory: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiInsightViewModel = viewModel { AiInsightViewModel(reportId, aiAdvisory) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val bottomPaddingPx = remember { with(density) { ChatListBottomPadding.toPx() } }

    // Follow new items (user message, typing dots, AI reply) and rest them
    // above the floating input bar instead of behind it.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .collect { listState.scrollToLatest(bottomPaddingPx) }
    }

    // While the AI reply is being revealed, follow its growth — unless the
    // user scrolled up to read earlier messages.
    val lastMessageLength = state.messages.lastOrNull()?.text?.length
    LaunchedEffect(lastMessageLength) {
        if (!state.isTyping) return@LaunchedEffect
        val info = listState.layoutInfo
        val following = info.visibleItemsInfo.lastOrNull()?.index == info.totalItemsCount - 1
        if (following) listState.scrollToLatest(bottomPaddingPx)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .collect {
                delay(150L)
                listState.scrollToLatest(bottomPaddingPx)
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ChatTopBar(onBack = onBack) },
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
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = ChatListBottomPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            if (state.isLoading) {
                item(key = "loading") { CenterProgress() }
            } else if (state.aiAdvisory == null && state.messages.isEmpty()) {
                item(key = "empty") { EmptyChatState() }
            }
            state.aiAdvisory?.let { advisory ->
                item(key = "advisory") {
                    AiMessageBubble(text = advisory)
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
                onSend = { viewModel.sendMessage() },
                isSending = state.isSending || state.isTyping,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private val ChatListBottomPadding = 88.dp

private suspend fun LazyListState.scrollToLatest(bottomPaddingPx: Float) {
    val total = layoutInfo.totalItemsCount
    if (total == 0) return
    animateScrollToItem(total - 1)
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull { it.index == total - 1 } ?: return
    val excess = last.offset + last.size - (info.viewportEndOffset - bottomPaddingPx)
    if (excess > 0) animateScrollBy(excess)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Health Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "PashuRakshak AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

@Composable
private fun AiMessageBubble(text: String, isTyping: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 24.dp),
        ) {
            if (isTyping) {
                // Plain text while revealing — markdown symbols would flash half-formed.
                Text(
                    text = "$text▍",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            } else {
                MarkdownContent(
                    text = text,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun UserMessageBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun TypingIndicatorBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    TypingDot(delayMillis = index * 140)
                }
            }
        }
    }
}

@Composable
private fun TypingDot(delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "typing")
    val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, delayMillis = delayMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer { translationY = offsetY.dp.toPx() }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
    )
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    modifier: Modifier = Modifier,
) {
    val canSend = inputText.isNotBlank() && !isSending
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.weight(1f),
        ) {
            TextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = {
                    Text(
                        text = "Ask about your animal's health...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (canSend) onSend() },
                ),
                maxLines = 4,
                enabled = !isSending,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(if (canSend) 6.dp else 2.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    if (canSend) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(46.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp),
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun CenterProgress() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Loading conversation...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyChatState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ask me anything",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Get preventive care and first-aid guidance for your animals.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Markdown rendering ────────────────────────────────────────────────────────

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: AnnotatedString) : MdBlock
    data class ListItem(val text: AnnotatedString, val marker: String) : MdBlock
    data class Paragraph(val text: AnnotatedString) : MdBlock
}

@Composable
private fun MarkdownContent(
    text: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = when (block.level) {
                            1 -> 18.sp
                            2 -> 16.5.sp
                            else -> 15.5.sp
                        },
                        lineHeight = when (block.level) {
                            1 -> 24.sp
                            2 -> 22.sp
                            else -> 21.sp
                        },
                        fontWeight = FontWeight.Bold,
                    ),
                    color = contentColor,
                )

                is MdBlock.ListItem -> Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = block.marker,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = contentColor,
                        modifier = Modifier.weight(1f),
                    )
                }

                is MdBlock.Paragraph -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = contentColor,
                )
            }
        }
    }
}

private val orderedListRegex = Regex("^(\\d+)\\.\\s+(.*)$")

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val content = paragraph.toString().trim()
        if (content.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(parseInlineMarkdown(content)))
        }
        paragraph.clear()
    }

    for (rawLine in text.lines()) {
        val line = rawLine.trim()
        when {
            line.isEmpty() -> flushParagraph()
            line.startsWith("### ") -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(3, parseInlineMarkdown(line.substring(4))))
            }
            line.startsWith("## ") -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(2, parseInlineMarkdown(line.substring(3))))
            }
            line.startsWith("# ") -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(1, parseInlineMarkdown(line.substring(2))))
            }
            line.startsWith("- ") || line.startsWith("• ") -> {
                flushParagraph()
                blocks.add(MdBlock.ListItem(parseInlineMarkdown(line.substring(2)), "•"))
            }
            line.startsWith("* ") && !line.startsWith("**") -> {
                flushParagraph()
                blocks.add(MdBlock.ListItem(parseInlineMarkdown(line.substring(2)), "•"))
            }
            orderedListRegex.matches(line) -> {
                flushParagraph()
                val dotIndex = line.indexOf(". ")
                blocks.add(
                    MdBlock.ListItem(
                        parseInlineMarkdown(line.substring(dotIndex + 2)),
                        "${line.substring(0, dotIndex)}.",
                    ),
                )
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }
    flushParagraph()
    return blocks
}

private fun parseInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    val start = length
                    append(text.substring(i + 2, end))
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("__", i) -> {
                val end = text.indexOf("__", i + 2)
                if (end > i + 2) {
                    val start = length
                    append(text.substring(i + 2, end))
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    val start = length
                    append(text.substring(i + 1, end))
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '_' -> {
                val end = text.indexOf('_', i + 1)
                if (end > i + 1) {
                    val start = length
                    append(text.substring(i + 1, end))
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    val start = length
                    append(text.substring(i + 1, end))
                    addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, length)
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
