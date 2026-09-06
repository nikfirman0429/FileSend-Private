package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FileSendApp
import com.example.data.local.TransferHistoryEntity
import com.example.data.model.TransferDirection
import com.example.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilterTab {
    ALL,
    SENT,
    RECEIVED
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FileRepository = (application as FileSendApp).fileRepository

    private val _selectedTab = MutableStateFlow(HistoryFilterTab.ALL)
    val selectedTab: StateFlow<HistoryFilterTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredHistory: StateFlow<List<TransferHistoryEntity>> = combine(
        repository.allHistory,
        _selectedTab,
        _searchQuery
    ) { historyList, tab, query ->
        historyList.filter { item ->
            val matchesTab = when (tab) {
                HistoryFilterTab.ALL -> true
                HistoryFilterTab.SENT -> item.direction == TransferDirection.SEND
                HistoryFilterTab.RECEIVED -> item.direction == TransferDirection.RECEIVE
            }
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                item.fileName.contains(query, ignoreCase = true) ||
                        item.code.contains(query, ignoreCase = true)
            }
            matchesTab && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectTab(tab: HistoryFilterTab) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteHistoryItem(item: TransferHistoryEntity) {
        viewModelScope.launch {
            repository.deleteHistory(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }
}
