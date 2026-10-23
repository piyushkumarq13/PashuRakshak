package com.pashurakshak.app.ui.aiInsight

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private fun JSONObject.optionalString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() && it != "null" }
}

enum class AiChatAudience { FARMER, VET }

data class AiChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
    val reportId: String? = null,
    val reportLabel: String? = null,
)

data class AiChatUiState(
    val audience: AiChatAudience = AiChatAudience.FARMER,
    val sessions: List<AiChatSession> = emptyList(),
    val activeConversationId: String? = null,
    val activeReportId: String? = null,
    val activeReportLabel: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoadingSessions: Boolean = true,
    val isLoadingConversation: Boolean = false,
    val isSending: Boolean = false,
    val isTyping: Boolean = false,
    val typingMessageId: String? = null,
    val error: String? = null,
    val sessionsError: String? = null,
)

class AiChatViewModel(
    private val audience: AiChatAudience = AiChatAudience.FARMER,
    private val initialReportId: String? = null,
) : ViewModel() {

    private var initialCaseHandled = false
    private var userStartedNewChat = false

    private val _uiState = MutableStateFlow(
        AiChatUiState(
            audience = audience,
            activeReportId = initialReportId.takeIf {
                audience == AiChatAudience.VET && !it.isNullOrBlank()
            },
        ),
    )
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        refreshSessions()
    }

    fun refreshSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSessions = true, sessionsError = null) }
            val fetchedSessions = runCatching { fetchSessions() }.getOrNull()
            if (fetchedSessions == null) {
                _uiState.update {
                    it.copy(
                        isLoadingSessions = false,
                        sessionsError = "Could not load chat history.",
                    )
                }
                return@launch
            }

            val shouldResumeCase = !initialCaseHandled &&
                !userStartedNewChat &&
                audience == AiChatAudience.VET &&
                !initialReportId.isNullOrBlank() &&
                _uiState.value.activeConversationId == null &&
                _uiState.value.messages.isEmpty() &&
                _uiState.value.inputText.isBlank()

            val mergedSessions = mergeSessions(fetchedSessions, _uiState.value.sessions)
            _uiState.update {
                it.copy(
                    sessions = mergedSessions,
                    isLoadingSessions = false,
                    sessionsError = null,
                )
            }
            initialCaseHandled = true

            if (shouldResumeCase) {
                mergedSessions
                    .firstOrNull { it.reportId == initialReportId }
                    ?.let { openConversation(it.id) }
            }
        }
    }

    private fun mergeSessions(
        fetched: List<AiChatSession>,
        current: List<AiChatSession>,
    ): List<AiChatSession> = (fetched + current)
        .groupBy { it.id }
        .map { (_, sessions) -> sessions.maxBy { it.updatedAt } }
        .sortedByDescending { it.updatedAt }

    fun openConversation(conversationId: String) {
        val state = _uiState.value
        if (state.isSending || state.isTyping) return
        if (state.activeConversationId == conversationId && state.error == null) return
        val scopedSession = state.sessions.firstOrNull { it.id == conversationId }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeConversationId = conversationId,
                    activeReportId = scopedSession?.reportId,
                    activeReportLabel = scopedSession?.reportLabel,
                    messages = emptyList(),
                    inputText = "",
                    isLoadingConversation = true,
                    error = null,
                )
            }
            val conversation = runCatching { fetchConversation(conversationId) }.getOrNull()
            _uiState.update {
                if (it.activeConversationId != conversationId) {
                    it
                } else {
                    it.copy(
                        activeReportId = conversation?.reportId ?: scopedSession?.reportId,
                        activeReportLabel = conversation?.reportLabel ?: scopedSession?.reportLabel,
                        messages = conversation?.messages ?: emptyList(),
                        isLoadingConversation = false,
                        error = if (conversation == null) "Could not load this conversation." else null,
                    )
                }
            }
        }
    }

    fun startNewChat() {
        val state = _uiState.value
        if (state.isSending || state.isTyping) return
        userStartedNewChat = true
        _uiState.update {
            it.copy(
                activeConversationId = null,
                messages = emptyList(),
                inputText = "",
                isLoadingConversation = false,
                error = null,
            )
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val state = _uiState.value
        if (state.inputText.isBlank() || state.isSending || state.isTyping || state.isLoadingConversation) return
        val message = state.inputText.trim()
        val conversationId = state.activeConversationId
        val reportId = state.activeReportId.takeIf { audience == AiChatAudience.VET }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage("user", message),
                    inputText = "",
                    isSending = true,
                    error = null,
                )
            }

            val response = runCatching { sendChat(conversationId, message, reportId) }.getOrNull()
            if (response != null) {
                _uiState.update {
                    it.copy(
                        activeConversationId = response.conversationId,
                        activeReportId = response.reportId ?: reportId,
                        activeReportLabel = response.reportLabel ?: it.activeReportLabel,
                        sessions = upsertSession(it.sessions, response, message),
                    )
                }
                revealReply(reply = response.reply, failed = false)
            } else {
                revealReply(reply = UNAVAILABLE_REPLY, failed = true)
            }
        }
    }

    private fun upsertSession(
        sessions: List<AiChatSession>,
        response: SendChatResult,
        userMessage: String,
    ): List<AiChatSession> {
        val existing = sessions.firstOrNull { it.id == response.conversationId }
        val updated = AiChatSession(
            id = response.conversationId,
            title = existing?.title ?: response.title ?: titleFrom(userMessage),
            updatedAt = System.currentTimeMillis(),
            messageCount = (existing?.messageCount ?: 0) + 2,
            reportId = response.reportId ?: existing?.reportId,
            reportLabel = response.reportLabel ?: existing?.reportLabel,
        )
        return (listOf(updated) + sessions.filterNot { it.id == response.conversationId })
            .sortedByDescending { it.updatedAt }
    }

    private fun titleFrom(message: String): String {
        val line = message.lineSequence().firstOrNull()?.trim().orEmpty()
        return if (line.length > 80) "${line.take(77)}..." else line.ifBlank { "New conversation" }
    }

    private suspend fun revealReply(reply: String, failed: Boolean) {
        val assistantMessage = ChatMessage("assistant", "")
        _uiState.update {
            it.copy(
                isSending = false,
                isTyping = true,
                typingMessageId = assistantMessage.id,
                messages = it.messages + assistantMessage,
                error = if (failed) "Failed to get AI response. Please try again." else null,
            )
        }
        var revealed = 0
        while (revealed < reply.length) {
            revealed = nextRevealIndex(reply, revealed)
            val partial = reply.substring(0, revealed)
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map { message ->
                        if (message.id == assistantMessage.id) message.copy(text = partial) else message
                    },
                )
            }
            delay(REVEAL_DELAY_MS)
        }
        _uiState.update { it.copy(isTyping = false, typingMessageId = null) }
    }

    private fun nextRevealIndex(text: String, from: Int): Int {
        val target = (from + REVEAL_CHARS).coerceAtMost(text.length)
        if (target >= text.length) return text.length
        val boundary = text.indexOf(' ', startIndex = target)
        return if (boundary > 0) (boundary + 1).coerceAtMost(text.length) else text.length
    }

    private val chatPath: String
        get() = if (audience == AiChatAudience.VET) "vet-ai-chats" else "ai-chats"

    private suspend fun fetchSessions(): List<AiChatSession> = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.API_BASE_URL
        if (baseUrl.isBlank()) return@withContext emptyList()
        val sessionToken = SessionManager.sessionToken ?: return@withContext emptyList()
        val connection = URL("$baseUrl/api/v1/pashu-health/$chatPath")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
            connection.setRequestProperty("Authorization", "Bearer $sessionToken")
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Chat history request failed")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(body).optJSONArray("conversations") ?: return@withContext emptyList()
            (0 until arr.length()).map { index ->
                val obj = arr.getJSONObject(index)
                AiChatSession(
                    id = obj.optString("id"),
                    title = obj.optString("title", "New conversation"),
                    updatedAt = obj.optLong("updatedAt", 0L),
                    messageCount = obj.optInt("messageCount", 0),
                    reportId = obj.optionalString("reportId"),
                    reportLabel = obj.optionalString("reportLabel"),
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetchConversation(conversationId: String): ConversationResult? =
        withContext(Dispatchers.IO) {
            val baseUrl = BuildConfig.API_BASE_URL
            if (baseUrl.isBlank()) return@withContext null
            val sessionToken = SessionManager.sessionToken ?: return@withContext null
            val encodedId = URLEncoder.encode(conversationId, "UTF-8")
            val connection = URL("$baseUrl/api/v1/pashu-health/$chatPath/$encodedId")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                connection.setRequestProperty("Authorization", "Bearer $sessionToken")
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Conversation request failed")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val arr = json.optJSONArray("messages") ?: return@withContext ConversationResult(
                    messages = emptyList(),
                    reportId = json.optionalString("reportId"),
                    reportLabel = json.optionalString("reportLabel"),
                )
                ConversationResult(
                    messages = (0 until arr.length()).map { index ->
                        val obj = arr.getJSONObject(index)
                        ChatMessage(
                            role = obj.optString("role", "assistant"),
                            text = obj.optString("content", ""),
                            id = obj.optString("id", "").ifBlank {
                                java.util.UUID.randomUUID().toString()
                            },
                        )
                    },
                    reportId = json.optionalString("reportId"),
                    reportLabel = json.optionalString("reportLabel"),
                )
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun sendChat(
        conversationId: String?,
        message: String,
        reportId: String?,
    ): SendChatResult? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = BuildConfig.API_BASE_URL
            if (baseUrl.isBlank()) return@withContext null
            val sessionToken = SessionManager.sessionToken ?: return@withContext null
            val payload = JSONObject().put("message", message)
            if (!conversationId.isNullOrBlank()) {
                payload.put("conversationId", conversationId)
            }
            if (!reportId.isNullOrBlank()) {
                payload.put("reportId", reportId)
            }
            val connection = URL("$baseUrl/api/v1/pashu-health/$chatPath")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 120_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                connection.setRequestProperty("Authorization", "Bearer $sessionToken")
                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                if (connection.responseCode !in 200..299) return@withContext null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val id = json.optString("conversationId")
                if (id.isBlank()) return@withContext null
                SendChatResult(
                    conversationId = id,
                    reply = json.optString("reply").ifBlank { UNAVAILABLE_REPLY },
                    title = json.optString("title").takeIf { it.isNotBlank() },
                    reportId = json.optionalString("reportId"),
                    reportLabel = json.optionalString("reportLabel"),
                )
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            Log.w("AiChatVM", "sendChat failed: ${error.message}")
            null
        }
    }

    private data class ConversationResult(
        val messages: List<ChatMessage>,
        val reportId: String?,
        val reportLabel: String?,
    )

    private data class SendChatResult(
        val conversationId: String,
        val reply: String,
        val title: String?,
        val reportId: String?,
        val reportLabel: String?,
    )

    private companion object {
        const val UNAVAILABLE_REPLY = "AI assistance is unavailable right now. Please try again shortly."
        const val REVEAL_CHARS = 8
        const val REVEAL_DELAY_MS = 24L
    }
}
