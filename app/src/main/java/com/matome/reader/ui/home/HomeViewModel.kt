package com.matome.reader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matome.reader.data.model.Article
import com.matome.reader.data.model.Feed
import com.matome.reader.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

data class HomeUiState(
    val articles: List<Article> = emptyList(),
    val feeds: List<Feed> = emptyList(),
    val selectedFeedId: Long? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val unreadCount: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: NewsRepository
) : ViewModel() {

    private val _selectedFeedId = MutableStateFlow<Long?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    // combine は5つまでしか型付きラムダをサポートしないため、先に2つをまとめる
    private val _refreshState = combine(_isRefreshing, _errorMessage) { refreshing, error ->
        refreshing to error
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedFeedId.flatMapLatest { feedId ->
            if (feedId == null) repository.getAllArticles()
            else repository.getArticlesByFeed(feedId)
        },
        repository.getAllFeeds(),
        _selectedFeedId,
        _refreshState,
        repository.getUnreadCount()
    ) { articles, feeds, selectedFeedId, refreshState, unreadCount ->
        HomeUiState(
            articles = articles,
            feeds = feeds,
            selectedFeedId = selectedFeedId,
            isRefreshing = refreshState.first,
            errorMessage = refreshState.second,
            unreadCount = unreadCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isRefreshing = true)
    )

    init {
        // DatabaseInitializerがフィードを挿入するのを待ってからrefresh
        viewModelScope.launch {
            repository.getAllFeeds()
                .filter { it.isNotEmpty() }
                .take(1)
                .collect()
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            Log.d("HomeViewModel", "refresh started")
            val result = repository.refreshAllFeeds()
            Log.d("HomeViewModel", "refresh done: success=${result.successCount}, error=${result.errorCount}")
            if (result.errorCount > 0 && result.successCount == 0) {
                _errorMessage.value = "フィードの読み込みに失敗しました"
            }
            _isRefreshing.value = false
        }
    }

    fun selectFeed(feedId: Long?) {
        _selectedFeedId.value = feedId
    }

    fun markAsRead(articleId: Long) {
        viewModelScope.launch {
            repository.markAsRead(articleId)
        }
    }

    fun toggleBookmark(article: Article) {
        viewModelScope.launch {
            repository.toggleBookmark(article)
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
