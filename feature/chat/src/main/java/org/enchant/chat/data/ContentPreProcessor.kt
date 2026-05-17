package org.enchant.chat.data

import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.Patterns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import java.util.regex.Pattern
import org.enchant.core.network.ApiClient

data class UrlSpan(val url: String, val start: Int, val end: Int)

data class FormattingSpan(val start: Int, val end: Int, val type: SpanType)
enum class SpanType { BOLD, ITALIC, CODE, SPOILER }

data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?
)

object ContentPreProcessor {
    private val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
    private val italicPattern = Regex("_(.+?)_")
    private val codePattern = Regex("`(.+?)`")
    private val spoilerPattern = Regex("\\|\\|(.+?)\\|\\|")

    private var apiClient: ApiClient? = null
    @Volatile
    private var initialized = false

    fun init(client: ApiClient) {
        apiClient = client
        initialized = true
    }

    fun detectUrls(text: String): List<UrlSpan> {
        if (!initialized) return emptyList()
        val urls = mutableListOf<UrlSpan>()
        val localhostPattern = Pattern.compile("https?://localhost:\\d+(/\\S*)?")
        val localhostMatcher = localhostPattern.matcher(text)
        while (localhostMatcher.find()) {
            urls.add(UrlSpan(localhostMatcher.group(), localhostMatcher.start(), localhostMatcher.end()))
        }
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            var url = matcher.group()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            urls.add(UrlSpan(url, matcher.start(), matcher.end()))
        }
        return urls
    }

    fun parseFormatting(text: String): List<FormattingSpan> {
        if (!initialized) return emptyList()
        val spans = mutableListOf<FormattingSpan>()
        spans.addAll(boldPattern.findAll(text).map {
            FormattingSpan(it.range.first, it.range.last + 1, SpanType.BOLD)
        })
        spans.addAll(italicPattern.findAll(text).map {
            FormattingSpan(it.range.first, it.range.last + 1, SpanType.ITALIC)
        })
        spans.addAll(codePattern.findAll(text).map {
            FormattingSpan(it.range.first, it.range.last + 1, SpanType.CODE)
        })
        spans.addAll(spoilerPattern.findAll(text).map {
            FormattingSpan(it.range.first, it.range.last + 1, SpanType.SPOILER)
        })
        return spans.sortedBy { it.start }
    }

    suspend fun generateLinkPreview(url: String): LinkPreview? {
        if (!initialized) return null
        return withContext(Dispatchers.Default) {
            try {
                val client = apiClient ?: return@withContext null
                val response = client.get("/v1/chats/link-preview", mapOf("url" to url))
                response.fold(
                    onSuccess = { json ->
                        LinkPreview(
                            url = url,
                            title = json["title"]?.jsonPrimitive?.content,
                            description = json["description"]?.jsonPrimitive?.content,
                            imageUrl = json["image"]?.jsonPrimitive?.content
                        )
                    },
                    onFailure = { null }
                )
            } catch (_: Exception) { null }
        }
    }

    fun applyFormatting(text: String): CharSequence {
        val sb = SpannableStringBuilder(text)
        Linkify.addLinks(sb, Linkify.WEB_URLS)
        return sb
    }
}
