package com.matome.reader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.matome.reader.data.model.Article
import com.matome.reader.data.model.Feed

@Database(
    entities = [Feed::class, Article::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
}
