package com.matome.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matome.reader.data.model.Feed
import com.matome.reader.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val feeds: List<Feed> = emptyList(),
    val snackbarMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: NewsRepository
) : ViewModel() {

    private val _snackbarMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.getAllFeeds(),
        _snackbarMessage
    ) { feeds, message ->
        SettingsUiState(feeds = feeds, snackbarMessage = message)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun addFeed(name: String, url: String) {
        viewModelScope.launch {
            val feed = Feed(name = name, url = url)
            repository.addFeed(feed)
            _snackbarMessage.value = "フィード「$name」を追加しました"
        }
    }

    fun toggleFeed(feed: Feed) {
        viewModelScope.launch {
            repository.updateFeed(feed.copy(isEnabled = !feed.isEnabled))
        }
    }

    fun toggleBaseballRelated(feed: Feed) {
        viewModelScope.launch {
            repository.setBaseballRelated(feed.id, !feed.isBaseballRelated)
        }
    }

    fun deleteFeed(feed: Feed) {
        viewModelScope.launch {
            repository.deleteFeed(feed)
            _snackbarMessage.value = "フィード「${feed.name}」を削除しました"
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            _snackbarMessage.value = "キャッシュをクリアしました"
        }
    }

    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }
}
