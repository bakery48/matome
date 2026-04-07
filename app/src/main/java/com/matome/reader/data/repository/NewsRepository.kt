package com.matome.reader.data.repository

import com.matome.reader.data.local.ArticleDao
import com.matome.reader.data.local.FeedDao
import com.matome.reader.data.model.Article
import com.matome.reader.data.model.Feed
import com.matome.reader.data.remote.RssFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepository @Inject constructor(
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    private val rssFetcher: RssFetcher
) {
    // Feeds
    fun getAllFeeds(): Flow<List<Feed>> = feedDao.getAllFeeds()

    suspend fun addFeed(feed: Feed): Long = feedDao.insertFeed(feed)

    suspend fun updateFeed(feed: Feed) = feedDao.updateFeed(feed)

    suspend fun deleteFeed(feed: Feed) = feedDao.deleteFeed(feed)

    // Articles
    fun getAllArticles(): Flow<List<Article>> = articleDao.getAllArticles()

    fun getArticlesByFeed(feedId: Long): Flow<List<Article>> =
        articleDao.getArticlesByFeed(feedId)

    fun getBookmarkedArticles(): Flow<List<Article>> = articleDao.getBookmarkedArticles()

    fun getUnreadCount(): Flow<Int> = articleDao.getUnreadCount()

    suspend fun markAsRead(articleId: Long) = articleDao.markAsRead(articleId)

    suspend fun toggleBookmark(article: Article) {
        articleDao.setBookmarked(article.id, !article.isBookmarked)
    }

    // Refresh
    suspend fun refreshAllFeeds(): RefreshResult = withContext(Dispatchers.IO) {
        val feeds = feedDao.getEnabledFeeds()
        var successCount = 0
        var errorCount = 0

        feeds.forEach { feed ->
            val result = rssFetcher.fetchFeed(feed)
            result.onSuccess { articles ->
                if (articles.isNotEmpty()) {
                    articleDao.insertArticles(articles)
                }
                feedDao.updateLastFetched(feed.id, System.currentTimeMillis())
                successCount++
            }.onFailure { e ->
                android.util.Log.e("NewsRepository", "Failed to fetch ${feed.url}", e)
                errorCount++
            }
        }

        articleDao.pruneOldArticles()
        RefreshResult(successCount, errorCount)
    }

    suspend fun refreshFeed(feed: Feed): Result<Int> {
        val result = rssFetcher.fetchFeed(feed)
        return result.map { articles ->
            if (articles.isNotEmpty()) {
                articleDao.insertArticles(articles)
                feedDao.updateLastFetched(feed.id, System.currentTimeMillis())
            }
            articles.size
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        val feeds = feedDao.getAllFeeds().first()
        feeds.forEach { feed ->
            articleDao.deleteNonBookmarkedByFeed(feed.id)
        }
    }
}

data class RefreshResult(val successCount: Int, val errorCount: Int)
