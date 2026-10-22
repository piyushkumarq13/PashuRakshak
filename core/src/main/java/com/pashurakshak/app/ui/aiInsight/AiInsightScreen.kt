package com.pashurakshak.app.ui.aiInsight

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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

    // While the AI reply is being revealed, keep its tail pinned above the
    // floating input bar. Instant snaps, not animations, so the fast reveal
    // ticks never cancel in-flight scrolls. Pauses if the user scrolls the
    // reply out of view.
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

internal val ChatListBottomPadding = 88.dp

internal suspend fun LazyListState.scrollToLatest(bottomPaddingPx: Float) {
    val total = layoutInfo.totalItemsCount
    if (total == 0) return
    animateScrollToItem(total - 1)
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull { it.index == total - 1 } ?: return
    val excess = last.offset + last.size - (info.viewportEndOffset - bottomPaddingPx)
    if (excess > 0) animateScrollBy(excess)
}

internal fun LazyListState.snapToLatest(bottomPaddingPx: Float) {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull { it.index == info.totalItemsCount - 1 } ?: return
    val excess = last.offset + last.size - (info.viewportEndOffset - bottomPaddingPx)
    if (excess > 0) dispatchRawDelta(excess.toFloat())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
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
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

@Composable
internal fun AiMessageBubble(text: String, isTyping: Boolean = false) {
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
            MarkdownContent(
                text = text,
                contentColor = MaterialTheme.colorScheme.onSurface,
                showCursor = isTyping,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
internal fun UserMessageBubble(text: String) {
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
internal fun TypingIndicatorBubble() {
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
internal fun ChatInputBar(
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
internal fun CenterProgress() {
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
internal fun EmptyChatState() {
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
    data class Heading(val level: Int, val inlines: List<MdInline>) : MdBlock
    data class UnorderedItem(val indent: Int, val checked: Boolean?, val inlines: List<MdInline>) : MdBlock
    data class OrderedItem(val indent: Int, val number: String, val inlines: List<MdInline>) : MdBlock
    data class Paragraph(val inlines: List<MdInline>) : MdBlock
    data class Quote(val inlines: List<MdInline>) : MdBlock
    data class CodeFence(val code: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

private sealed interface MdInline {
    data class Text(val value: String) : MdInline
    data class Bold(val children: List<MdInline>) : MdInline
    data class Italic(val children: List<MdInline>) : MdInline
    data class BoldItalic(val children: List<MdInline>) : MdInline
    data class Strike(val children: List<MdInline>) : MdInline
    data class Code(val value: String) : MdInline
    data class Link(val label: String, val url: String) : MdInline
}

@Composable
private fun MarkdownContent(
    text: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    showCursor: Boolean = false,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val cursorColor = MaterialTheme.colorScheme.primary
    val cursor = remember(cursorColor) { AnnotatedString("▍", SpanStyle(color = cursorColor)) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (blocks.isEmpty() && showCursor) {
            Text(
                text = cursor,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            )
        }
        blocks.forEachIndexed { index, block ->
            val trailingCursor = if (showCursor && index == blocks.lastIndex) cursor else null
            when (block) {
                is MdBlock.Heading -> {
                    Text(
                        text = buildInline(block.inlines, contentColor).plusCursor(trailingCursor),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = when (block.level) {
                                1 -> 18.sp
                                2 -> 16.5.sp
                                3 -> 15.5.sp
                                4 -> 15.sp
                                5 -> 14.5.sp
                                else -> 14.sp
                            },
                            lineHeight = when (block.level) {
                                1 -> 24.sp
                                2 -> 22.sp
                                else -> 21.sp
                            },
                            fontWeight = FontWeight.Bold,
                        ),
                        color = contentColor,
                        modifier = if (block.level <= 2) Modifier.padding(top = 4.dp) else Modifier,
                    )
                }

                is MdBlock.UnorderedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indent * 14).dp),
                    ) {
                        if (block.checked != null) {
                            Text(
                                text = if (block.checked) "✓" else "○",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                                color = if (block.checked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(18.dp),
                            )
                        } else {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                                color = contentColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(18.dp),
                            )
                        }
                        Text(
                            text = buildInline(block.inlines, contentColor).plusCursor(trailingCursor),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                            color = contentColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MdBlock.OrderedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indent * 14).dp),
                    ) {
                        Text(
                            text = "${block.number}.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(22.dp),
                        )
                        Text(
                            text = buildInline(block.inlines, contentColor).plusCursor(trailingCursor),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                            color = contentColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is MdBlock.Paragraph -> Text(
                    text = buildInline(block.inlines, contentColor).plusCursor(trailingCursor),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = contentColor,
                )

                is MdBlock.Quote -> {
                    val quoteBar = MaterialTheme.colorScheme.primary
                    Text(
                        text = buildInline(block.inlines, contentColor).plusCursor(trailingCursor),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = contentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .drawBehind {
                                drawRect(
                                    color = quoteBar,
                                    size = Size(3.dp.toPx(), size.height),
                                )
                            }
                            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    )
                }

                is MdBlock.CodeFence -> {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                                append(block.code)
                                pop()
                                trailingCursor?.let { append(it) }
                            },
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp),
                        )
                    }
                }

                is MdBlock.Table -> TableBlock(block, contentColor, trailingCursor)

                MdBlock.Divider -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                }
            }
            if (trailingCursor != null && block is MdBlock.Divider) {
                Text(
                    text = trailingCursor,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                )
            }
        }
    }
}

private val MinTableColumnWidth = 104.dp

@Composable
private fun TableBlock(
    block: MdBlock.Table,
    contentColor: Color,
    trailingCursor: AnnotatedString?,
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val columnCount = block.header.size.coerceAtLeast(1)
    // Fills the bubble when few columns; grows beyond the screen (and scrolls
    // on its own) when there are many, so columns keep breathing room.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val tableWidth = maxOf(screenWidth - 64.dp, MinTableColumnWidth * columnCount)

    Column {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.width(tableWidth),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                    ) {
                        block.header.forEach { cell ->
                            Text(
                                text = buildInline(parseInline(cell.replace(brTagRegex, "\n")), contentColor),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = contentColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = borderColor)
                    block.rows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            val cells = row + List((columnCount - row.size).coerceAtLeast(0)) { "" }
                            cells.take(columnCount).forEach { cell ->
                                Text(
                                    text = buildInline(parseInline(cell.replace(brTagRegex, "\n")), contentColor),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                                    color = contentColor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                )
                            }
                        }
                        HorizontalDivider(color = borderColor)
                    }
                }
            }
        }
        if (trailingCursor != null) {
            Text(
                text = trailingCursor,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            )
        }
    }
}

private fun AnnotatedString.plusCursor(cursor: AnnotatedString?): AnnotatedString =
    if (cursor == null) this else this + cursor

@Composable
private fun buildInline(inlines: List<MdInline>, contentColor: Color): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString {
        fun emit(list: List<MdInline>, extra: SpanStyle) {
            list.forEach { inline ->
                when (inline) {
                    is MdInline.Text -> withStyle(extra) { append(inline.value) }

                    is MdInline.Bold -> emit(
                        inline.children,
                        extra + SpanStyle(fontWeight = FontWeight.Bold),
                    )

                    is MdInline.Italic -> emit(
                        inline.children,
                        extra + SpanStyle(fontStyle = FontStyle.Italic),
                    )

                    is MdInline.BoldItalic -> emit(
                        inline.children,
                        extra + SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                    )

                    is MdInline.Strike -> emit(
                        inline.children,
                        extra + SpanStyle(textDecoration = TextDecoration.LineThrough),
                    )

                    is MdInline.Code -> withStyle(
                        extra + SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp,
                            background = codeBackground,
                        ),
                    ) {
                        append(inline.value)
                    }

                    is MdInline.Link -> withLink(
                        LinkAnnotation.Url(
                            url = inline.url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                    ) {
                        withStyle(extra) { append(inline.label) }
                    }
                }
            }
        }
        emit(inlines, SpanStyle(color = contentColor))
    }
}

// ── Markdown parsing ──────────────────────────────────────────────────────────

private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
private val orderedItemRegex = Regex("^(\\d+)[.)]\\s+(.*)$")
private val unorderedItemRegex = Regex("^[-*•]\\s+(.*)$")
private val checkboxRegex = Regex("^\\[( |x|X)\\]\\s+(.*)$")
private val tableDividerRegex = Regex("^\\|?[\\s:\\-|]+\\|?$")
private val hrRegex = Regex("^(\\*{3,}|-{3,}|_{3,})$")
private val brTagRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val linkInlineRegex = Regex("^\\[([^\\]]+)\\]\\(([^)\\s]+)\\)")
private val midLineListSeparator = Regex("\\s+[-*•]\\s+")

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    val lines = text.lines()

    fun flushParagraph() {
        val content = paragraph.toString().trim()
        paragraph.clear()
        if (content.isNotEmpty()) {
            blocks.addAll(splitLeadInList(content))
        }
    }

    fun isTableStart(index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        val line = lines[index].trim()
        val next = lines[index + 1].trim()
        return line.contains('|') && next.contains('-') && tableDividerRegex.matches(next)
    }

    fun parseTable(index: Int): Pair<MdBlock.Table, Int> {
        val header = splitTableRow(lines[index])
        val rows = mutableListOf<List<String>>()
        var j = index + 2
        while (j < lines.size) {
            val line = lines[j].trim()
            if (line.isEmpty() || !line.contains('|')) break
            rows.add(splitTableRow(line))
            j++
        }
        return MdBlock.Table(header, rows) to (j - 1)
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trim()
        val indentSpaces = raw.takeWhile { it == ' ' }.length

        when {
            line.isEmpty() -> flushParagraph()

            line.startsWith("```") -> {
                flushParagraph()
                val code = StringBuilder()
                var j = i + 1
                while (j < lines.size && !lines[j].trim().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[j])
                    j++
                }
                blocks.add(MdBlock.CodeFence(code.toString()))
                i = j
            }

            isTableStart(i) -> {
                flushParagraph()
                val (table, lastIndex) = parseTable(i)
                blocks.add(table)
                i = lastIndex
            }

            headingRegex.matches(line) -> {
                flushParagraph()
                val match = headingRegex.find(line)!!
                blocks.add(MdBlock.Heading(match.groupValues[1].length, parseInline(match.groupValues[2])))
            }

            line.startsWith(">") -> {
                flushParagraph()
                blocks.add(MdBlock.Quote(parseInline(line.removePrefix(">").trim())))
            }

            hrRegex.matches(line) -> {
                flushParagraph()
                blocks.add(MdBlock.Divider)
            }

            unorderedItemRegex.matches(line) || orderedItemRegex.matches(line) -> {
                flushParagraph()
                val indent = (indentSpaces / 2).coerceIn(0, 4)
                val unordered = unorderedItemRegex.find(line)
                if (unordered != null) {
                    var body = unordered.groupValues[1]
                    var checked: Boolean? = null
                    val checkbox = checkboxRegex.find(body)
                    if (checkbox != null) {
                        checked = checkbox.groupValues[1].lowercase() == "x"
                        body = checkbox.groupValues[2]
                    }
                    blocks.add(MdBlock.UnorderedItem(indent, checked, parseInline(body)))
                } else {
                    val ordered = orderedItemRegex.find(line)!!
                    blocks.add(
                        MdBlock.OrderedItem(indent, ordered.groupValues[1], parseInline(ordered.groupValues[2])),
                    )
                }
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

private fun splitTableRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

/**
 * LLMs often emit `**Heading:** - item - item` on a single line. Treat the text
 * before the first in-line dash as a lead-in and the dashes as list markers.
 */
private fun splitLeadInList(content: String): List<MdBlock> {
    val first = midLineListSeparator.find(content) ?: return listOf(MdBlock.Paragraph(parseInline(content)))
    val lead = content.substring(0, first.range.first).trim()
    val isLeadIn = lead.endsWith(':') || midLineListSeparator.findAll(content).count() >= 2
    if (!isLeadIn) return listOf(MdBlock.Paragraph(parseInline(content)))
    val parts = midLineListSeparator.split(content)
    val result = mutableListOf<MdBlock>()
    if (lead.isNotEmpty()) result.add(MdBlock.Paragraph(parseInline(lead)))
    parts.drop(1).forEach { item ->
        if (item.isNotBlank()) result.add(MdBlock.UnorderedItem(0, null, parseInline(item.trim())))
    }
    return result
}

private fun parseInline(text: String): List<MdInline> {
    val out = mutableListOf<MdInline>()
    val buf = StringBuilder()
    var i = 0

    fun flush() {
        if (buf.isNotEmpty()) {
            out.add(MdInline.Text(buf.toString()))
            buf.clear()
        }
    }

    while (i < text.length) {
        when {
            text.startsWith("***", i) -> {
                val end = text.indexOf("***", i + 3)
                if (end > i + 3) {
                    flush()
                    out.add(MdInline.BoldItalic(parseInline(text.substring(i + 3, end))))
                    i = end + 3
                } else {
                    buf.append(text[i])
                    i++
                }
            }

            text.startsWith("___", i) -> {
                val end = text.indexOf("___", i + 3)
                if (end > i + 3) {
                    flush()
                    out.add(MdInline.BoldItalic(parseInline(text.substring(i + 3, end))))
                    i = end + 3
                } else {
                    buf.append(text[i])
                    i++
                }
            }

            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    flush()
                    out.add(MdInline.Bold(parseInline(text.substring(i + 2, end))))
                    i = end + 2
                } else {
                    buf.append(text[i])
                    i++
                }
            }

            text.startsWith("__", i) -> {
                val end = text.indexOf("__", i + 2)
                if (end > i + 2) {
                    flush()
                    out.add(MdInline.Bold(parseInline(text.substring(i + 2, end))))
                    i = end + 2
                } else {
                    buf.append(text[i])
                    i++
                }
            }

            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i + 2) {
                    flush()
                    out.add(MdInline.Strike(parseInline(text.substring(i + 2, end))))
                    i = end + 2
                } else {
                    buf.append(text[i])
                    i++
                }
            }

            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    flush()
                    out.add(MdInline.Italic(parseInline(text.substring(i + 1, end))))
                    i = end + 1
                } else {
                    buf.append(text[i])
                    i++
                }
            }

            text[i] == '_' -> {
                val isSnakeCase = i > 0 && (text[i - 1].isLetterOrDigit())
                val end = if (isSnakeCase) -1 else text.indexOf('_', i + 1)
                if (end > i + 1) {
                    flush()
                    out.add(MdInline.Italic(parseInline(text.substring(i + 1, end))))
                    i = end + 1
                } else {
                    buf.append(text[i])
                    i++
                }
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush()
                    out.add(MdInline.Code(text.substring(i + 1, end)))
                    i = end + 1
                } else {
                    buf.append(text[i])
                    i++
                }
            }

            text[i] == '[' -> {
                val match = linkInlineRegex.find(text, i)
                if (match != null && match.range.first == i) {
                    flush()
                    out.add(MdInline.Link(match.groupValues[1], match.groupValues[2]))
                    i = match.range.last + 1
                } else {
                    buf.append(text[i])
                    i++
                }
            }

            else -> {
                buf.append(text[i])
                i++
            }
        }
    }
    flush()
    return out
}
