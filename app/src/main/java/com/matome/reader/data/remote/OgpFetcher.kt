package com.matome.reader.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OgpFetcher"
private val OGP_REGEX = Regex(
    """<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""",
    RegexOption.IGNORE_CASE
)
private val OGP_REGEX2 = Regex(
    """<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""",
    RegexOption.IGNORE_CASE
)

@Singleton
class OgpFetcher @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun fetchOgImage(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MatomeReader/1.0 (Android)")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            // headのみ読めば十分（最大64KB）
            val body = response.body?.source()?.apply { request(65536) }
            val html = body?.buffer?.readUtf8() ?: return@withContext null
            OGP_REGEX.find(html)?.groupValues?.getOrNull(1)
                ?: OGP_REGEX2.find(html)?.groupValues?.getOrNull(1)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to fetch OGP for $url: ${e.message}")
            null
        }
    }
}
