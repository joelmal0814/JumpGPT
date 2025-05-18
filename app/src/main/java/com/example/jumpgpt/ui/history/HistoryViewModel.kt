package com.example.jumpgpt.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jumpgpt.domain.model.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryState(
    val conversations: List<Conversation> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state

    init {
        loadConversations()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                _state.update { it.copy(
                    conversations = emptyList(),
                    isLoading = false
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(
                    error = e.localizedMessage,
                    isLoading = false
                ) }
            }
        }
    }
} 