package com.matome.reader.data.local

import androidx.room.*
import com.matome.reader.data.model.Feed
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    @Query("SELECT * FROM feeds ORDER BY name ASC")
    fun getAllFeeds(): Flow<List<Feed>>

    @Query("SELECT * FROM feeds WHERE isEnabled = 1 ORDER BY name ASC")
    suspend fun getEnabledFeeds(): List<Feed>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun getFeedById(id: Long): Feed?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: Feed): Long

    @Update
    suspend fun updateFeed(feed: Feed)

    @Delete
    suspend fun deleteFeed(feed: Feed)

    @Query("UPDATE feeds SET lastFetchedAt = :timestamp, lastError = NULL WHERE id = :id")
    suspend fun updateLastFetched(id: Long, timestamp: Long)

    @Query("UPDATE feeds SET lastError = :error WHERE id = :id")
    suspend fun updateLastError(id: Long, error: String)
}
