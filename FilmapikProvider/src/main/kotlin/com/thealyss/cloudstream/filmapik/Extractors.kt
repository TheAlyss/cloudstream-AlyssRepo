package com.thealyss.cloudstream.filmapik

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.FilemoonV2
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class Byseqekaho : FilemoonV2() {
    override var name = "Filemoon"
    override var mainUrl = "https://byseqekaho.com"
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

        try {
            val apiResponse = app.get("https://player-cdn.com/?v=$videoId", referer = "https://filmapik.college/").text
            val atobMatch = Regex("""atob\s*\(\s*["']([A-Za-z0-9+/=]+)["']\s*\)""").find(apiResponse)
            if (atobMatch != null) {
                val b64Str = atobMatch.groupValues[1]
                val jsonBytes = Base64.decode(b64Str, Base64.DEFAULT)
                val jsonStr = String(jsonBytes, Charsets.UTF_8)
                val jsonObj = JSONObject(jsonStr)
                val domain = jsonObj.optString("domain")
                val id = jsonObj.optString("id")

                if (domain.isNotBlank() && id.isNotBlank()) {
                    val qualities = mapOf(
                        "" to Qualities.P360.value,
                        "www" to Qualities.P720.value,
                        "whw" to Qualities.P1080.value
                    )

                    qualities.forEach { (prefix, quality) ->
                        val streamUrl = "https://$domain/$prefix$id"
                        callback.invoke(
                            newExtractorLink(
                                source = name,
                                name = "$name ${getQualityName(quality)}",
                                url = streamUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = "https://abysscdn.com/?v=$videoId"
                                this.quality = quality
                            }
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun getQualityName(quality: Int): String {
        return when (quality) {
            Qualities.P1080.value -> "1080p"
            Qualities.P720.value -> "720p"
            Qualities.P360.value -> "360p"
            else -> "SD"
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
        try {
            val response = app.get(url, referer = referer ?: "$mainUrl/").text
            val unpacked = getAndUnpack(response) ?: response

            val matches = Regex("""["']label["']\s*:\s*["']([^"']+)["']\s*,\s*["']type["']\s*:\s*["']([^"']+)["']\s*,\s*["']file["']\s*:\s*["']([^"']+)["']""").findAll(unpacked)
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
        } catch (e: Throwable) {
            e.printStackTrace()
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
