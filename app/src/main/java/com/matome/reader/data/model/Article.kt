package com.matome.reader.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = Feed::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("feedId"), Index("link", unique = true)]
)
data class Article(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val feedId: Long,
    val feedName: String,
    val title: String,
    val link: String,
    val description: String?,
    val imageUrl: String?,
    val publishedAt: Long,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
)
