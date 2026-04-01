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

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedFeedId.flatMapLatest { feedId ->
            if (feedId == null) repository.getAllArticles()
            else repository.getArticlesByFeed(feedId)
        },
        repository.getAllFeeds(),
        _selectedFeedId,
        _isRefreshing,
        _errorMessage,
        repository.getUnreadCount()
    ) { articles, feeds, selectedFeedId, isRefreshing, errorMessage, unreadCount ->
        HomeUiState(
            articles = articles,
            feeds = feeds,
            selectedFeedId = selectedFeedId,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage,
            unreadCount = unreadCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isRefreshing = true)
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            val result = repository.refreshAllFeeds()
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
