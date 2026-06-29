package com.dc.aurora.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dc.aurora.notifications.NotificationHelper
import com.dc.aurora.services.ChatService
import com.dc.aurora.services.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class Message(val text: String, val isUser: Boolean)

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val repo = ScheduleRepository(app)
    private val chat = ChatService(repo)
    private val prefs = app.getSharedPreferences("dc_prefs", android.content.Context.MODE_PRIVATE)

    init {
        NotificationHelper.ensureChannels(app)
        NotificationHelper.scheduleMorningBriefingTomorrow(app)
        maybeAutoShowBriefing()
    }

    fun prefill(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun send() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isLoading) return
        _uiState.update { it.copy(
            messages = it.messages + Message(text, isUser = true),
            inputText = "",
            isLoading = true,
            error = null,
        ) }
        viewModelScope.launch { sendToAI(text) }
    }

    private suspend fun sendToAI(text: String) {
        runCatching {
            chat.send(text, getApplication())
        }.onSuccess { reply ->
            _uiState.update { it.copy(
                messages = it.messages + Message(reply, isUser = false),
                isLoading = false,
            ) }
        }.onFailure { err ->
            _uiState.update { it.copy(
                messages = it.messages + Message(
                    "오류가 발생했어요: ${err.message}", isUser = false
                ),
                isLoading = false,
                error = err.message,
            ) }
        }
    }

    private fun maybeAutoShowBriefing() {
        val now = LocalTime.now()
        if (now.hour < 6 || now.hour >= 11) return
        val today = LocalDate.now().toString()
        if (prefs.getString("last_briefing_date", null) == today) return
        prefs.edit().putString("last_briefing_date", today).apply()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            sendToAI("오늘 일정 보여줘")
        }
    }
}
