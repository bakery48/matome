package com.matome.reader.data.local

import androidx.room.*
import com.matome.reader.data.model.Article
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun getAllArticles(): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY publishedAt DESC")
    fun getArticlesByFeed(feedId: Long): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY publishedAt DESC")
    fun getBookmarkedArticles(): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleById(id: Long): Article?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<Article>)

    @Update
    suspend fun updateArticle(article: Article)

    @Query("UPDATE articles SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE articles SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun setBookmarked(id: Long, isBookmarked: Boolean)

    @Query("DELETE FROM articles WHERE feedId = :feedId AND isBookmarked = 0")
    suspend fun deleteNonBookmarkedByFeed(feedId: Long)

    @Query("""
        DELETE FROM articles
        WHERE isBookmarked = 0
        AND id NOT IN (
            SELECT id FROM articles
            ORDER BY publishedAt DESC
            LIMIT :keepCount
        )
    """)
    suspend fun pruneOldArticles(keepCount: Int = 500)

    @Query("SELECT COUNT(*) FROM articles WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>
}
