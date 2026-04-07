package com.matome.reader.data.remote

import com.matome.reader.data.model.Article
import com.matome.reader.data.model.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RssFetcher @Inject constructor(
    private val client: OkHttpClient,
    private val parser: RssParser
) {
    suspend fun fetchFeed(feed: Feed): Result<List<Article>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(feed.url)
                .header("User-Agent", "MatomeReader/1.0 (Android)")
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty response"))
            val articles = parser.parse(body.byteStream(), feed)
            Result.success(articles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
