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
                val namespace = parser.namespace ?: ""
                when {
                    parser.name.lowercase() == "title" -> title = parser.nextText().trim()
                    parser.name.lowercase() == "link" -> {
                        val text = parser.nextText().trim()
                        if (text.isNotBlank()) link = text
                    }
                    parser.name.lowercase() == "description" -> {
                        val text = parser.nextText().trim()
                        description = extractTextFromHtml(text)
                        if (imageUrl == null) {
                            imageUrl = extractImageFromHtml(text)
                        }
                    }
                    parser.name.lowercase() == "pubdate" -> {
                        pubDate = parseDate(parser.nextText().trim())
                    }
                    parser.name.lowercase() == "dc:date" -> {
                        pubDate = parseDate(parser.nextText().trim())
                    }
                    parser.name.lowercase() == "enclosure" -> {
                        val type = parser.getAttributeValue(null, "type") ?: ""
                        if (type.startsWith("image/")) {
                            imageUrl = parser.getAttributeValue(null, "url")
                        }
                        parser.nextText()
                    }
                    // media:thumbnail or media:content
                    namespace.contains("media") && (parser.name.contains("thumbnail") || parser.name.contains("content")) -> {
                        if (imageUrl == null) {
                            imageUrl = parser.getAttributeValue(null, "url")
                        }
                        try { parser.nextText() } catch (_: Exception) {}
                    }
                    else -> try { parser.nextText() } catch (_: Exception) {}
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
                when (parser.name.lowercase()) {
                    "title" -> title = parser.nextText().trim()
                    "link" -> {
                        val rel = parser.getAttributeValue(null, "rel") ?: "alternate"
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        if (rel == "alternate" && href.isNotBlank()) link = href
                        try { parser.nextText() } catch (_: Exception) {}
                    }
                    "summary", "content" -> {
                        val text = parser.nextText().trim()
                        if (description == null) {
                            description = extractTextFromHtml(text)
                            imageUrl = extractImageFromHtml(text)
                        }
                    }
                    "published", "updated" -> {
                        if (publishedAt == 0L || parser.name.lowercase() == "published") {
                            publishedAt = parseDate(parser.nextText().trim())
                        } else {
                            parser.nextText()
                        }
                    }
                    else -> try { parser.nextText() } catch (_: Exception) {}
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
