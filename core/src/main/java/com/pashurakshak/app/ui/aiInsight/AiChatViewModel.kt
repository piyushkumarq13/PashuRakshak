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

data class AiChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
)

data class AiChatUiState(
    val sessions: List<AiChatSession> = emptyList(),
    val activeConversationId: String? = null,
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

class AiChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        refreshSessions()
    }

    fun refreshSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSessions = true, sessionsError = null) }
            val sessions = runCatching { fetchSessions() }.getOrNull()
            _uiState.update {
                it.copy(
                    sessions = sessions?.let { fetched -> mergeSessions(fetched, it.sessions) } ?: it.sessions,
                    isLoadingSessions = false,
                    sessionsError = if (sessions == null) "Could not load chat history." else null,
                )
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
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeConversationId = conversationId,
                    messages = emptyList(),
                    inputText = "",
                    isLoadingConversation = true,
                    error = null,
                )
            }
            val messages = runCatching { fetchConversation(conversationId) }.getOrNull()
            _uiState.update {
                if (it.activeConversationId != conversationId) {
                    it
                } else {
                    it.copy(
                        messages = messages ?: emptyList(),
                        isLoadingConversation = false,
                        error = if (messages == null) "Could not load this conversation." else null,
                    )
                }
            }
        }
    }

    fun startNewChat() {
        val state = _uiState.value
        if (state.isSending || state.isTyping) return
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

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage("user", message),
                    inputText = "",
                    isSending = true,
                    error = null,
                )
            }

            val response = runCatching { sendChat(conversationId, message) }.getOrNull()
            if (response != null) {
                _uiState.update {
                    it.copy(
                        activeConversationId = response.conversationId,
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

    private suspend fun fetchSessions(): List<AiChatSession> = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.API_BASE_URL
        if (baseUrl.isBlank()) return@withContext emptyList()
        val sessionToken = SessionManager.sessionToken ?: return@withContext emptyList()
        val connection = URL("$baseUrl/api/v1/pashu-health/ai-chats").openConnection() as HttpURLConnection
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
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetchConversation(conversationId: String): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            val baseUrl = BuildConfig.API_BASE_URL
            if (baseUrl.isBlank()) return@withContext emptyList()
            val sessionToken = SessionManager.sessionToken ?: return@withContext emptyList()
            val encodedId = URLEncoder.encode(conversationId, "UTF-8")
            val connection = URL("$baseUrl/api/v1/pashu-health/ai-chats/$encodedId")
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
                val arr = JSONObject(body).optJSONArray("messages") ?: return@withContext emptyList()
                (0 until arr.length()).map { index ->
                    val obj = arr.getJSONObject(index)
                    ChatMessage(
                        role = obj.optString("role", "assistant"),
                        text = obj.optString("content", ""),
                        id = obj.optString("id", "").ifBlank { java.util.UUID.randomUUID().toString() },
                    )
                }
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun sendChat(conversationId: String?, message: String): SendChatResult? =
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = BuildConfig.API_BASE_URL
                if (baseUrl.isBlank()) return@withContext null
                val sessionToken = SessionManager.sessionToken ?: return@withContext null
                val payload = JSONObject().put("message", message)
                if (!conversationId.isNullOrBlank()) {
                    payload.put("conversationId", conversationId)
                }
                val connection = URL("$baseUrl/api/v1/pashu-health/ai-chats")
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
                    )
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                Log.w("AiChatVM", "sendChat failed: ${error.message}")
                null
            }
        }

    private data class SendChatResult(
        val conversationId: String,
        val reply: String,
        val title: String?,
    )

    private companion object {
        const val UNAVAILABLE_REPLY = "AI assistance is unavailable right now. Please try again shortly."
        const val REVEAL_CHARS = 8
        const val REVEAL_DELAY_MS = 24L
    }
}
