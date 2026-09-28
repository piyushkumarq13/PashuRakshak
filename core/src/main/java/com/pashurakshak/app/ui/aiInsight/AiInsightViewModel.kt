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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ChatMessage(
    val role: String,
    val text: String,
    val id: String = java.util.UUID.randomUUID().toString(),
)

data class AiInsightUiState(
    val reportId: String = "",
    val aiAdvisory: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isTyping: Boolean = false,
    val typingMessageId: String? = null,
    val error: String? = null,
) {
    val isOffline: Boolean get() = aiAdvisory == null && messages.isEmpty()
}

class AiInsightViewModel(private val reportId: String, initialAdvisory: String? = null) : ViewModel() {

    private val _uiState = MutableStateFlow(AiInsightUiState(reportId = reportId, aiAdvisory = initialAdvisory))
    val uiState: StateFlow<AiInsightUiState> = _uiState.asStateFlow()

    init {
        loadChat()
    }

    private fun loadChat() {
        viewModelScope.launch {
            val messages = runCatching { fetchChat() }.getOrNull() ?: emptyList()
            _uiState.update { it.copy(messages = messages, isLoading = false) }
        }
    }

    private suspend fun fetchChat(): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = BuildConfig.API_BASE_URL
            if (baseUrl.isBlank()) return@withContext emptyList()
            val sessionToken = SessionManager.sessionToken ?: return@withContext emptyList()
            val url = URL("$baseUrl/api/v1/pashu-health/reports/${reportId}/chat")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 120_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                connection.setRequestProperty("Authorization", "Bearer $sessionToken")
                val code = connection.responseCode
                if (code !in 200..299) return@withContext emptyList()
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val arr = json.optJSONArray("messages") ?: return@withContext emptyList()
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    ChatMessage(
                        role = obj.optString("role", "assistant"),
                        text = obj.optString("content", obj.optString("text", "")),
                    )
                }
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            Log.w("AiInsightVM", "fetchChat failed: ${error.message}")
            emptyList()
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val state = _uiState.value
        if (state.inputText.isBlank() || state.isSending || state.isTyping) return
        val message = state.inputText.trim()
        viewModelScope.launch {
            // Show the user's message immediately — never wait for the network.
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage("user", message),
                    inputText = "",
                    isSending = true,
                    error = null,
                )
            }
            val response = runCatching { sendChat(message) }.getOrNull()
            revealReply(
                reply = response?.takeIf { it.isNotBlank() } ?: UNAVAILABLE_REPLY,
                failed = response == null,
            )
        }
    }

    /** Reveals the assistant reply word-by-word so it looks like the AI is writing. */
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
                    messages = state.messages.map { m ->
                        if (m.id == assistantMessage.id) m.copy(text = partial) else m
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
        // Snap forward to the next space so words appear whole.
        val boundary = text.indexOf(' ', startIndex = target)
        return if (boundary > 0) (boundary + 1).coerceAtMost(text.length) else text.length
    }

    private companion object {
        const val UNAVAILABLE_REPLY = "AI assistance is unavailable right now. Please try again shortly."
        const val REVEAL_CHARS = 8
        const val REVEAL_DELAY_MS = 24L
    }

    private suspend fun sendChat(message: String): String? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = BuildConfig.API_BASE_URL
            if (baseUrl.isBlank()) return@withContext null
            val sessionToken = SessionManager.sessionToken ?: return@withContext null
            val payload = JSONObject().put("message", message)
            val connection = URL("$baseUrl/api/v1/pashu-health/reports/${reportId}/chat").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 120_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                connection.setRequestProperty("Authorization", "Bearer $sessionToken")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) return@withContext null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                json.optString("reply", "").takeIf { it.isNotBlank() }
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            Log.w("AiInsightVM", "sendChat failed: ${error.message}")
            null
        }
    }
}
