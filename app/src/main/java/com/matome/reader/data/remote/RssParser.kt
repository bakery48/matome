package com.matome.reader.data.remote

import android.util.Log
import com.matome.reader.data.model.Article
import com.matome.reader.data.model.Feed
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "RssParser"

class RssParser {

    private val rssDateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    )

    fun parse(inputStream: InputStream, feed: Feed): List<Article> {
        return try {
            // 名前空間なしでパース（互換性が高い）
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            val feedType = detectFeedType(parser)
            Log.d(TAG, "Feed ${feed.name}: detected type=$feedType")
            when (feedType) {
                FeedType.RSS -> parseRss(parser, feed)
                FeedType.ATOM -> parseAtom(parser, feed)
                FeedType.UNKNOWN -> {
                    Log.w(TAG, "Unknown feed type for ${feed.url}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse feed ${feed.url}", e)
            emptyList()
        }
    }

    private enum class FeedType { RSS, ATOM, UNKNOWN }

    private fun detectFeedType(parser: XmlPullParser): FeedType {
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase()
                Log.d(TAG, "Root element: ${parser.name}")
                return when {
                    name == "rss" || name == "rdf:rdf" || name.endsWith(":rdf") -> FeedType.RSS
                    name == "feed" -> FeedType.ATOM
                    else -> FeedType.UNKNOWN
                }
            }
            eventType = parser.next()
        }
        return FeedType.UNKNOWN
    }

    private fun parseRss(parser: XmlPullParser, feed: Feed): List<Article> {
        val articles = mutableListOf<Article>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.lowercase() == "item") {
                parseRssItem(parser, feed)?.let { articles.add(it) }
            }
            eventType = parser.next()
        }
        Log.d(TAG, "Parsed ${articles.size} articles from ${feed.name}")
        return articles
    }

    private fun parseRssItem(parser: XmlPullParser, feed: Feed): Article? {
        var title = ""
        var link = ""
        var description: String? = null
        var imageUrl: String? = null
        var pubDate: Long = System.currentTimeMillis()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name.lowercase() == "item")) {
            if (eventType == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase()
                when {
                    name == "title" ->
                        title = parser.nextText().trim()

                    name == "link" -> {
                        val text = runCatching { parser.nextText().trim() }.getOrDefault("")
                        if (text.isNotBlank()) link = text
                    }

                    name == "description" -> {
                        val text = runCatching { parser.nextText().trim() }.getOrDefault("")
                        description = extractTextFromHtml(text)
                        if (imageUrl == null) imageUrl = extractImageFromHtml(text)
                    }

                    name == "pubdate" || name == "dc:date" ->
                        pubDate = parseDate(runCatching { parser.nextText().trim() }.getOrDefault(""))

                    name == "enclosure" -> {
                        val type = parser.getAttributeValue(null, "type") ?: ""
                        val url = parser.getAttributeValue(null, "url") ?: ""
                        if (type.startsWith("image/") && url.isNotBlank() && imageUrl == null) {
                            imageUrl = url
                        }
                        skipElement(parser)
                    }

                    // media:thumbnail / media:content
                    name == "media:thumbnail" || name == "media:content" -> {
                        val url = parser.getAttributeValue(null, "url") ?: ""
                        if (url.isNotBlank() && imageUrl == null) imageUrl = url
                        skipElement(parser)
                    }

                    else -> skipElement(parser)
                }
            }
            if (eventType == XmlPullParser.END_DOCUMENT) break
            eventType = parser.next()
        }

        if (link.isBlank()) return null
        return Article(
            feedId = feed.id,
            feedName = feed.name,
            title = title,
            link = link,
            description = description,
            imageUrl = imageUrl,
            publishedAt = pubDate
        )
    }

    private fun parseAtom(parser: XmlPullParser, feed: Feed): List<Article> {
        val articles = mutableListOf<Article>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.lowercase() == "entry") {
                parseAtomEntry(parser, feed)?.let { articles.add(it) }
            }
            eventType = parser.next()
        }
        Log.d(TAG, "Parsed ${articles.size} articles from ${feed.name}")
        return articles
    }

    private fun parseAtomEntry(parser: XmlPullParser, feed: Feed): Article? {
        var title = ""
        var link = ""
        var description: String? = null
        var imageUrl: String? = null
        var publishedAt: Long = 0L

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name.lowercase() == "entry")) {
            if (eventType == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase()
                when {
                    name == "title" ->
                        title = runCatching { parser.nextText().trim() }.getOrDefault("")

                    name == "link" -> {
                        val rel = parser.getAttributeValue(null, "rel") ?: "alternate"
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        if ((rel == "alternate" || rel == "") && href.isNotBlank()) link = href
                        skipElement(parser)
                    }

                    name == "summary" || name == "content" -> {
                        val text = runCatching { parser.nextText().trim() }.getOrDefault("")
                        if (description == null) {
                            description = extractTextFromHtml(text)
                            if (imageUrl == null) imageUrl = extractImageFromHtml(text)
                        }
                    }

                    name == "published" ->
                        publishedAt = parseDate(runCatching { parser.nextText().trim() }.getOrDefault(""))

                    name == "updated" && publishedAt == 0L ->
                        publishedAt = parseDate(runCatching { parser.nextText().trim() }.getOrDefault(""))

                    name == "media:thumbnail" || name == "media:content" -> {
                        val url = parser.getAttributeValue(null, "url") ?: ""
                        if (url.isNotBlank() && imageUrl == null) imageUrl = url
                        skipElement(parser)
                    }

                    else -> skipElement(parser)
                }
            }
            if (eventType == XmlPullParser.END_DOCUMENT) break
            eventType = parser.next()
        }

        if (link.isBlank()) return null
        return Article(
            feedId = feed.id,
            feedName = feed.name,
            title = title,
            link = link,
            description = description,
            imageUrl = imageUrl,
            publishedAt = if (publishedAt == 0L) System.currentTimeMillis() else publishedAt
        )
    }

    // 自己閉じタグ・ネストしたタグを安全にスキップ
    private fun skipElement(parser: XmlPullParser) {
        try {
            var depth = 1
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.END_DOCUMENT -> return
                }
            }
        } catch (_: Exception) {}
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        for (format in rssDateFormats) {
            try {
                return format.parse(dateStr)?.time ?: continue
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    private fun extractTextFromHtml(html: String): String {
        return html
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
            .take(300)
    }

    private fun extractImageFromHtml(html: String): String? {
        val imgRegex = Regex("""<img[^>]+src=['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
        return imgRegex.find(html)?.groupValues?.getOrNull(1)
    }
}
