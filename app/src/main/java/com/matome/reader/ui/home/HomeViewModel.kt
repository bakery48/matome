package com.matome.reader.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matome.reader.data.model.Article
import com.matome.reader.data.model.Feed
import com.matome.reader.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BaseballFilter { ALL, BASEBALL_ONLY, HIDE_BASEBALL }

data class HomeUiState(
    val articles: List<Article> = emptyList(),
    val feeds: List<Feed> = emptyList(),
    val selectedFeedId: Long? = null,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val unreadCount: Int = 0,
    val baseballFilter: BaseballFilter = BaseballFilter.ALL,
    val hasBaseballFeeds: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: NewsRepository
) : ViewModel() {

    private val _selectedFeedId = MutableStateFlow<Long?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _baseballFilter = MutableStateFlow(BaseballFilter.ALL)

    private val _refreshState = combine(_isRefreshing, _errorMessage) { refreshing, error ->
        refreshing to error
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedFeedId.flatMapLatest { feedId ->
            if (feedId == null) repository.getAllArticles()
            else repository.getArticlesByFeed(feedId)
        },
        repository.getAllFeeds(),
        combine(_selectedFeedId, _baseballFilter) { feedId, filter -> feedId to filter },
        _refreshState,
        repository.getUnreadCount()
    ) { articles, feeds, (selectedFeedId, baseballFilter), refreshState, unreadCount ->
        val baseballFeedIds = feeds.filter { it.isBaseballRelated }.map { it.id }.toSet()
        val hasBaseballFeeds = baseballFeedIds.isNotEmpty()

        val filtered = when (baseballFilter) {
            BaseballFilter.ALL -> articles
            BaseballFilter.BASEBALL_ONLY -> articles.filter { it.feedId in baseballFeedIds }
            BaseballFilter.HIDE_BASEBALL -> articles.filter { it.feedId !in baseballFeedIds }
        }

        HomeUiState(
            articles = filtered,
            feeds = feeds,
            selectedFeedId = selectedFeedId,
            isRefreshing = refreshState.first,
            errorMessage = refreshState.second,
            unreadCount = unreadCount,
            baseballFilter = baseballFilter,
            hasBaseballFeeds = hasBaseballFeeds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isRefreshing = true)
    )

    init {
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

    fun cycleBaseballFilter() {
        _baseballFilter.value = when (_baseballFilter.value) {
            BaseballFilter.ALL -> BaseballFilter.BASEBALL_ONLY
            BaseballFilter.BASEBALL_ONLY -> BaseballFilter.HIDE_BASEBALL
            BaseballFilter.HIDE_BASEBALL -> BaseballFilter.ALL
        }
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
