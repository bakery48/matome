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

data class ParsedArticle(
    val title: String,
    val link: String,
    val description: String?,
    val imageUrl: String?,
    val publishedAt: Long
)

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
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            val feedType = detectFeedType(parser)
            when (feedType) {
                FeedType.RSS -> parseRss(parser, feed)
                FeedType.ATOM -> parseAtom(parser, feed)
                FeedType.UNKNOWN -> emptyList()
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
                return when (parser.name.lowercase()) {
                    "rss", "rdf:rdf" -> FeedType.RSS
                    "feed" -> FeedType.ATOM
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
                val localName = parser.name.substringAfterLast(':').lowercase()
                val namespace = parser.namespace ?: ""
                val isMediaNs = namespace.contains("media") || namespace.contains("mrss") ||
                        parser.name.startsWith("media:")
                when {
                    localName == "title" && !isMediaNs ->
                        title = parser.nextText().trim()
                    localName == "link" && !isMediaNs -> {
                        val text = parser.nextText().trim()
                        if (text.isNotBlank()) link = text
                    }
                    localName == "description" && !isMediaNs -> {
                        val text = parser.nextText().trim()
                        description = extractTextFromHtml(text)
                        if (imageUrl == null) imageUrl = extractImageFromHtml(text)
                    }
                    localName == "pubdate" || localName == "date" ->
                        pubDate = parseDate(parser.nextText().trim())
                    localName == "enclosure" -> {
                        // enclosureは自己閉じタグなのでnextText()不要
                        val type = parser.getAttributeValue(null, "type") ?: ""
                        if (type.startsWith("image/") && imageUrl == null) {
                            imageUrl = parser.getAttributeValue(null, "url")
                        }
                        skipElement(parser)
                    }
                    // media:thumbnail / media:content
                    isMediaNs && (localName == "thumbnail" || localName == "content") -> {
                        if (imageUrl == null) {
                            imageUrl = parser.getAttributeValue(null, "url")
                        }
                        skipElement(parser)
                    }
                    else -> skipElement(parser)
                }
            }
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
        return articles
    }

    private fun parseAtomEntry(parser: XmlPullParser, feed: Feed): Article? {
        var title = ""
        var link = ""
        var description: String? = null
        var imageUrl: String? = null
        var publishedAt: Long = System.currentTimeMillis()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name.lowercase() == "entry")) {
            if (eventType == XmlPullParser.START_TAG) {
                val localName = parser.name.substringAfterLast(':').lowercase()
                val namespace = parser.namespace ?: ""
                val isMediaNs = namespace.contains("media") || namespace.contains("mrss") ||
                        parser.name.startsWith("media:")
                when {
                    localName == "title" && !isMediaNs ->
                        title = parser.nextText().trim()
                    localName == "link" -> {
                        val rel = parser.getAttributeValue(null, "rel") ?: "alternate"
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        if (rel == "alternate" && href.isNotBlank()) link = href
                        skipElement(parser)
                    }
                    localName == "summary" || localName == "content" -> {
                        val text = parser.nextText().trim()
                        if (description == null) {
                            description = extractTextFromHtml(text)
                            if (imageUrl == null) imageUrl = extractImageFromHtml(text)
                        }
                    }
                    localName == "published" -> {
                        publishedAt = parseDate(parser.nextText().trim())
                    }
                    localName == "updated" && publishedAt == 0L -> {
                        publishedAt = parseDate(parser.nextText().trim())
                    }
                    isMediaNs && (localName == "thumbnail" || localName == "content") -> {
                        if (imageUrl == null) {
                            imageUrl = parser.getAttributeValue(null, "url")
                        }
                        skipElement(parser)
                    }
                    else -> skipElement(parser)
                }
            }
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
            publishedAt = publishedAt
        )
    }

    // 自己閉じタグ・ネストしたタグを安全にスキップする
    private fun skipElement(parser: XmlPullParser) {
        try {
            var depth = 1
            while (depth > 0) {
                val event = parser.next()
                when (event) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                    XmlPullParser.END_DOCUMENT -> return
                }
            }
        } catch (_: Exception) {}
    }

    private fun parseDate(dateStr: String): Long {
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
        val imgRegex = Regex("<img[^>]+src=['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
        return imgRegex.find(html)?.groupValues?.getOrNull(1)
    }
}
