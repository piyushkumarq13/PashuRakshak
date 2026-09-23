package com.pashurakshak.app.ui.aiInsight

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.pashurakshak.app.BuildConfig
import kotlinx.coroutines.Dispatchers
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

data class ChatMessage(val role: String, val text: String)

data class AiInsightUiState(
    val reportId: String = "",
    val aiAdvisory: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
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
            val idToken = currentIdToken() ?: return@withContext emptyList()
            val connection = URL("${BuildConfig.API_BASE_URL}/api/v1/pashu-health/reports/$reportId/chat")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                connection.setRequestProperty("Authorization", "Bearer $idToken")
                val code = connection.responseCode
                if (code !in 200..299) return@withContext emptyList()
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val arr = json.optJSONArray("messages") ?: return@withContext emptyList()
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    ChatMessage(
                        role = obj.optString("role", "assistant"),
                        text = obj.optString("text", ""),
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
        if (state.inputText.isBlank() || state.isSending) return
        val message = state.inputText
        val currentMessages = state.messages
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            val response = runCatching { sendChat(message) }.getOrNull()
            val updatedMessages = currentMessages + ChatMessage("user", message) +
                (if (response != null) listOf(ChatMessage("assistant", response)) else emptyList())
            _uiState.update { it.copy(isSending = false, inputText = "", messages = updatedMessages) }
        }
    }

    private suspend fun sendChat(message: String): String? = withContext(Dispatchers.IO) {
        try {
            val idToken = currentIdToken() ?: return@withContext null
            val payload = JSONObject().put("message", message)
            val connection = URL("${BuildConfig.API_BASE_URL}/api/v1/pashu-health/reports/$reportId/chat")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                connection.setRequestProperty("Authorization", "Bearer $idToken")
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

    private fun currentIdToken(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            Tasks.await(user.getIdToken(false)).token
        } catch (_: Exception) { null }
    }
}
