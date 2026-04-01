package com.matome.reader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class Feed(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val lastFetchedAt: Long = 0L,
    val faviconUrl: String? = null
)
