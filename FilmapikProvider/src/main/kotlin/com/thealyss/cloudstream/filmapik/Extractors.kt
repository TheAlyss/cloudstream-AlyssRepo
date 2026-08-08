package com.thealyss.cloudstream.filmapik

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FilemoonV2
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class Byseqekaho : ExtractorApi() {
    override var name = "Filemoon"
    override var mainUrl = "https://byseqekaho.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Try FilemoonV2 standard static unpacker first
        val filemoonV2 = object : FilemoonV2() {
            override var mainUrl = "https://byseqekaho.com"
            override var name = "Filemoon"
        }
        var found = false
        filemoonV2.getUrl(url, referer, subtitleCallback) { link ->
            callback.invoke(link)
            found = true
        }

        // If static unpacker yields no link, resolve using Android WebView
        if (!found) {
            val webView = WebViewResolver(
                Regex(""".*\.(?:m3u8|mp4).*""")
            )
            val resolved = webView.resolveUsingWebView(url)
            val streamUrl = resolved.first?.url?.toString()
            if (!streamUrl.isNullOrEmpty()) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = streamUrl,
                        type = if (streamUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        }
    }
}

class AbyssPlayer : ExtractorApi() {
    override var name = "Hydrax"
    override var mainUrl = "https://abyssplayer.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = url.substringAfterLast("/")
        if (videoId.isBlank()) return

        // Resolve JS player using Android WebView
        val webView = WebViewResolver(
            Regex(""".*\.(?:m3u8|mp4).*""")
        )
        val resolved = webView.resolveUsingWebView(url)
        val streamUrl = resolved.first?.url?.toString()
        if (!streamUrl.isNullOrEmpty()) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = streamUrl,
                    type = if (streamUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = "https://abysscdn.com/?v=$videoId"
                    this.quality = Qualities.P1080.value
                }
            )
        }
    }
}

open class VipServer : ExtractorApi() {
    override var name = "VIP Server"
    override var mainUrl = "https://v2.efek.stream"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer ?: "$mainUrl/").text
        val unpacked = getAndUnpack(response) ?: response

        val matches = Regex("""'label'\s*:\s*'([^']+)'\s*,\s*'type'\s*:\s*'([^']+)'\s*,\s*'file'\s*:\s*'([^']+)'""").findAll(unpacked)
        matches.forEach { match ->
            val qualityLabel = match.groupValues[1]
            val streamPath = match.groupValues[3]
            val fullStreamUrl = if (streamPath.startsWith("http")) streamPath else "$mainUrl$streamPath"
            val qualityInt = parseQuality(qualityLabel)

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "$name $qualityLabel",
                    url = fullStreamUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = url
                    this.quality = qualityInt
                }
            )
        }
    }

    private fun parseQuality(qualityLabel: String): Int {
        return when {
            qualityLabel.contains("1080") -> Qualities.P1080.value
            qualityLabel.contains("720") -> Qualities.P720.value
            qualityLabel.contains("480") -> Qualities.P480.value
            qualityLabel.contains("360") -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
}

class FaEfekStream : VipServer() {
    override var mainUrl = "https://fa.efek.stream"
}
