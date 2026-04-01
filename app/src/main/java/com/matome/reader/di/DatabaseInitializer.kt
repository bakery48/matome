package com.matome.reader.di

import com.matome.reader.data.local.FeedDao
import com.matome.reader.data.model.Feed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Default feeds to seed on first launch
private val DEFAULT_FEEDS = listOf(
    Feed(name = "NHKニュース", url = "https://www.nhk.or.jp/rss/news/cat0.xml"),
    Feed(name = "朝日新聞", url = "https://www.asahi.com/rss/asahi/newsheadlines.rdf"),
    Feed(name = "ITmedia NEWS", url = "https://rss.itmedia.co.jp/rss/2.0/itmedia_all.xml"),
    Feed(name = "Gigazine", url = "https://gigazine.net/news/rss_2.0/"),
    Feed(name = "TechCrunch Japan", url = "https://jp.techcrunch.com/feed/")
)

@Singleton
class DatabaseInitializer @Inject constructor(
    private val feedDao: FeedDao
) {
    fun initializeIfEmpty() {
        CoroutineScope(Dispatchers.IO).launch {
            val feeds = feedDao.getAllFeeds().first()
            if (feeds.isEmpty()) {
                DEFAULT_FEEDS.forEach { feedDao.insertFeed(it) }
            }
        }
    }
}
