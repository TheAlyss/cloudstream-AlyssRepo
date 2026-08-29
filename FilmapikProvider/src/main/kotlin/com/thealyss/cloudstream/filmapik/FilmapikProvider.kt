package com.thealyss.cloudstream.filmapik

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Suppress("DEPRECATION")
class FilmapikProvider : MainAPI() {
    override var mainUrl = "https://filmapik.college"
    override var name = "Filmapik"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Anime
    )

    override var lang = "id"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/category/box-office" to "Box Office",
        "$mainUrl/release-year/2026" to "Populer 2026",
        "$mainUrl/tvshows-genre/k-drama" to "Drama Korea",
        "$mainUrl/tvshows-genre/anime" to "Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}/page/$page" else request.data
        val document = app.get(url).document
        val homeItems = document.select("a.group, article.card a, div.famv-post-item a").mapNotNull { element ->
            toSearchResult(element)
        }
        return newHomePageResponse(request.name, homeItems)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val href = element.attr("href")
        if (href.isBlank() || href == "#") return null

        val title = element.selectFirst("h3")?.text() ?: return null
        val cleanTitle = title
            .replace(Regex("(?i)^Nonton\\s+Film\\s+"), "")
            .replace(Regex("(?i)\\s+Subtitle\\s+Indonesia$"), "")
            .trim()

        if (cleanTitle.isBlank()) return null

        val imgElement = element.selectFirst("img") ?: return null
        val posterUrl = imgElement.attr("src").ifBlank {
            val srcset = imgElement.attr("srcset")
            if (srcset.isNotBlank()) srcset.substringBefore(" ") else ""
        }

        val quality = element.selectFirst(".badge-quality")?.text() ?: ""
        val isTvShow = href.contains("/tvshows/") || href.contains("/tvshows-genre/") || href.contains("/series/") || href.contains("/season/")

        val tvType = if (isTvShow) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(cleanTitle, href, tvType) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(searchUrl).document
        return document.select("a.group, article.card a, div.famv-post-item a").mapNotNull { element ->
            toSearchResult(element)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val rawTitle = document.selectFirst("h1")?.text() ?: "Movie"
        val title = rawTitle
            .replace(Regex("(?i)^Nonton\\s+Film\\s+"), "")
            .replace(Regex("(?i)\\s+Subtitle\\s+Indonesia$"), "")
            .trim()

        val posterUrl = document.selectFirst("div.shrink-0 img, div.w-48.shrink-0 img, div.md\\:w-64 img, img[src*='/poster/']")?.attr("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        val backdropUrl = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?.takeIf { it.contains("/backdrop/") }
        val plot = document.selectFirst(".famv-truncated-text p, div.famv-truncated-text")?.text()
        val yearText = document.selectFirst(".badge-cyan")?.text()
        val year = yearText?.let { Regex("(\\d{4})").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val genres = document.select("a[href*='/category/']").map { it.text() }

        val actors = document.select("div.cast a, a[href*='/cast/'], a[href*='/actor/'], div.actor-item, div.famv-actor a, a[href*='/director/']").mapNotNull { actorElem ->
            val actorName = actorElem.selectFirst("span, .name, h4")?.text()?.ifBlank { null } ?: actorElem.text().trim()
            val actorImg = actorElem.selectFirst("img")?.attr("src")
            val role = actorElem.selectFirst(".character, .role, p")?.text()
            if (actorName.isNotBlank() && !actorName.contains("Cast", ignoreCase = true) && !actorName.contains("Director", ignoreCase = true)) {
                ActorData(
                    Actor(actorName, actorImg),
                    roleString = role
                )
            } else null
        }.distinctBy { it.actor }

        val ratingText = document.selectFirst(".badge-yellow, .badge-cyan, .rating, span[itemprop='ratingValue']")?.text()
        val ratingInt = ratingText?.let { Regex("""(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

        val trailerUrl = document.selectFirst("iframe[src*='youtube.com'], a[href*='youtube.com']")?.attr("src")?.ifBlank { null }
            ?: document.selectFirst("a[href*='youtube.com']")?.attr("href")

        val playUrl = if (url.endsWith("/play")) url else if (url.endsWith("/play/")) url.removeSuffix("/") else "${url.removeSuffix("/")}/play"

        val isTvShow = url.contains("/tvshows/") || url.contains("/tvshows-genre/") || url.contains("/series/") || url.contains("/season/")

        if (isTvShow) {
            val episodes = mutableListOf<Episode>()
            val seasonContainers = document.select("div.famv-season-list[data-season], div.famv-season-list")

            if (seasonContainers.isNotEmpty()) {
                seasonContainers.forEach { seasonContainer ->
                    val seasonContainerNum = seasonContainer.attr("data-season").toIntOrNull() ?: 1
                    seasonContainer.select("a.famv-episode-btn, a[href*='/episodes/'], a[href*='/episode/']").forEach { epLink ->
                        val epHref = epLink.attr("href")
                        if (epHref.isNotBlank() && epHref != "#") {
                            val epPlayUrl = if (epHref.endsWith("/play")) epHref else if (epHref.endsWith("/play/")) epHref.removeSuffix("/") else "${epHref.removeSuffix("/")}/play"
                            val rawEpText = epLink.text().trim()

                            val seasonFromText = Regex("""(?i)S(\d+)\s*[E\s:]*\s*(\d+)""").find(rawEpText)
                            val seasonFromUrl = Regex("""(?i)season-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()

                            val seasonNum = seasonFromText?.groupValues?.get(1)?.toIntOrNull()
                                ?: seasonFromUrl
                                ?: seasonContainerNum

                            val epNum = seasonFromText?.groupValues?.get(2)?.toIntOrNull()
                                ?: Regex("""(?i)episode-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                                ?: Regex("""(?i)(?:EP|Episode)\s*(\d+)""").find(rawEpText)?.groupValues?.get(1)?.toIntOrNull()
                                ?: Regex("""\b(\d+)\b""").find(rawEpText)?.groupValues?.get(1)?.toIntOrNull()
                                ?: 1

                            val cleanTitle = rawEpText
                                .replace(Regex("""(?i)^Nonton\\s+(?:Film|Series|Drama)?\\s*"""), "")
                                .replace(Regex("""(?i)\\s+Subtitle\\s+Indonesia.*$"""), "")
                                .replace(Regex("""(?i)^S\\d+\\s*[E\\s:]*\\s*E?\\d+\\s*[\\s:-]*"""), "")
                                .replace(Regex("""(?i)^(?:EP|Episode)\\s*\\d+\\s*[\\s:-]*"""), "")
                                .trim()

                            val isGenericEpName = cleanTitle.isBlank() ||
                                cleanTitle.matches(Regex("""(?i)^(?:EP|Episode|E)?\s*\d+$""")) ||
                                cleanTitle.equals(epNum.toString(), ignoreCase = true)

                            val displayName = if (!isGenericEpName) {
                                "Episode $epNum: $cleanTitle"
                            } else {
                                "Episode $epNum"
                            }

                            episodes.add(
                                newEpisode(epPlayUrl) {
                                    this.name = displayName
                                    this.season = seasonNum
                                    this.episode = epNum
                                    this.posterUrl = backdropUrl ?: posterUrl
                                    this.description = plot
                                }
                            )
                        }
                    }
                }
            } else {
                // Fallback for flat episode lists
                document.select("a.famv-episode-btn, a[href*='/episodes/'], a[href*='/episode/']").forEach { epLink ->
                    val epHref = epLink.attr("href")
                    if (epHref.isNotBlank() && epHref != "#") {
                        val epPlayUrl = if (epHref.endsWith("/play")) epHref else if (epHref.endsWith("/play/")) epHref.removeSuffix("/") else "${epHref.removeSuffix("/")}/play"
                        val rawEpText = epLink.text().trim()

                        val seasonFromText = Regex("""(?i)S(\d+)\s*[E\s:]*\s*(\d+)""").find(rawEpText)
                        val seasonFromUrl = Regex("""(?i)season-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()

                        val seasonNum = seasonFromText?.groupValues?.get(1)?.toIntOrNull()
                            ?: seasonFromUrl
                            ?: 1

                        val epNum = seasonFromText?.groupValues?.get(2)?.toIntOrNull()
                            ?: Regex("""(?i)episode-(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("""(?i)(?:EP|Episode)\s*(\d+)""").find(rawEpText)?.groupValues?.get(1)?.toIntOrNull()
                            ?: Regex("""\b(\d+)\b""").find(rawEpText)?.groupValues?.get(1)?.toIntOrNull()
                            ?: 1

                        val cleanTitle = rawEpText
                            .replace(Regex("""(?i)^Nonton\\s+(?:Film|Series|Drama)?\\s*"""), "")
                            .replace(Regex("""(?i)\\s+Subtitle\\s+Indonesia.*$"""), "")
                            .replace(Regex("""(?i)^S\\d+\\s*[E\\s:]*\\s*E?\\d+\\s*[\\s:-]*"""), "")
                            .replace(Regex("""(?i)^(?:EP|Episode)\\s*\\d+\\s*[\\s:-]*"""), "")
                            .trim()

                        val isGenericEpName = cleanTitle.isBlank() ||
                            cleanTitle.matches(Regex("""(?i)^(?:EP|Episode|E)?\s*\d+$""")) ||
                            cleanTitle.equals(epNum.toString(), ignoreCase = true)

                        val displayName = if (!isGenericEpName) {
                            "Episode $epNum: $cleanTitle"
                        } else {
                            "Episode $epNum"
                        }

                        episodes.add(
                            newEpisode(epPlayUrl) {
                                this.name = displayName
                                this.season = seasonNum
                                this.episode = epNum
                                this.posterUrl = backdropUrl ?: posterUrl
                                this.description = plot
                            }
                        )
                    }
                }
            }

            val sortedEpisodes = episodes
                .distinctBy { it.data }
                .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 1 }))

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, sortedEpisodes) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = backdropUrl ?: posterUrl
                this.plot = plot
                this.year = year
                this.tags = genres
                this.actors = actors
                this.score = Score.from10(ratingInt)
                trailerUrl?.let { addTrailer(it, addRaw = true) }
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, playUrl) {
            this.posterUrl = posterUrl
            this.backgroundPosterUrl = backdropUrl ?: posterUrl
            this.plot = plot
            this.year = year
            this.tags = genres
            this.actors = actors
            this.score = Score.from10(ratingInt)
            trailerUrl?.let { addTrailer(it, addRaw = true) }
        }
    }

    private fun base64UrlDecode(str: String): ByteArray {
        val padded = str.replace("-", "+").replace("_", "/")
        val padding = (4 - padded.length % 4) % 4
        val finalStr = if (padding in 1..3) padded + "=".repeat(padding) else padded
        return Base64.decode(finalStr, Base64.DEFAULT)
    }

    private suspend fun extractByseFilemoon(url: String, callback: (ExtractorLink) -> Unit): Boolean {
        return try {
            val code = url.trimEnd('/').substringAfterLast('/')
            val uri = Uri.parse(url)
            val host = uri.host ?: "byseqekaho.com"
            val apiUrl = "https://$host/api/videos/$code"

            val res = app.get(apiUrl, headers = mapOf("Referer" to url, "Accept" to "application/json"))
            val json = JSONObject(res.text)
            if (!json.has("playback")) return false
            val playback = json.getJSONObject("playback")
            val version = playback.optString("version").toIntOrNull() ?: return false
            val keyPartsArr = playback.getJSONArray("key_parts")
            if (keyPartsArr.length() < 20) return false

            val p1Idx = version - 1
            val p2Idx = (31 - version) - 1
            if (p1Idx < 0 || p1Idx >= keyPartsArr.length() || p2Idx < 0 || p2Idx >= keyPartsArr.length()) return false

            val p1 = base64UrlDecode(keyPartsArr.getString(p1Idx))
            val p2 = base64UrlDecode(keyPartsArr.getString(p2Idx))
            val keyBytes = p1 + p2 // 32 bytes AES key

            val ivBytes = base64UrlDecode(playback.getString("iv"))
            val payloadBytes = base64UrlDecode(playback.getString("payload"))

            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(payloadBytes)
            val decryptedStr = String(decryptedBytes, Charsets.UTF_8)

            val decryptedJson = JSONObject(decryptedStr)
            val sources = decryptedJson.optJSONArray("sources") ?: return false
            var found = false
            for (i in 0 until sources.length()) {
                val s = sources.getJSONObject(i)
                val m3u8Url = s.optString("url")
                val label = s.optString("label", "1080p")
                if (m3u8Url.isNotBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            "Filemoon",
                            "Filemoon $label (HLS)",
                            m3u8Url,
                            INFER_TYPE
                        ) {
                            this.referer = url
                            this.quality = getQualityFromName(label)
                        }
                    )
                    found = true
                }
            }
            found
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun extractVipServer(
        embedUrl: String,
        playPageUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val uri = Uri.parse(embedUrl)
            val host = uri.host ?: "v2.efek.stream"
            val embedDoc = app.get(embedUrl, referer = playPageUrl).text
            val unpacked = getAndUnpack(embedDoc)
            if (unpacked.isBlank()) return false

            val sourcesMatch = Regex("""sources\s*:\s*\[([\s\S]*?)\]""").find(unpacked)
            val searchArea = sourcesMatch?.groupValues?.get(1) ?: unpacked

            val blocks = searchArea.split(Regex("""\},?\s*\{"""))
            var found = false

            for (block in blocks) {
                val fileMatch = Regex("""['"]?file['"]?\s*:\s*['"]([^'"]+)['"]""").find(block) ?: continue
                val rawFile = fileMatch.groupValues[1].replace("\\/", "/")
                val labelMatch = Regex("""['"]?label['"]?\s*:\s*['"]([^'"]+)['"]""").find(block)
                val label = labelMatch?.groupValues?.get(1) ?: "VIP"

                val fullFileUrl = if (rawFile.startsWith("http")) {
                    rawFile
                } else {
                    "https://$host$rawFile"
                }

                // Pre-resolve 302 redirect so ExoPlayer connects directly to storage server (s1/s2/s3.efek.stream)
                val directUrl = try {
                    val headRes = app.get(
                        fullFileUrl,
                        headers = mapOf(
                            "Referer" to embedUrl,
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                    )
                    if (headRes.url.isNotBlank()) headRes.url else fullFileUrl
                } catch (e: Exception) {
                    fullFileUrl
                }

                val directHost = Uri.parse(directUrl).host ?: host
                val quality = getQualityFromName(label)

                callback.invoke(
                    newExtractorLink(
                        "VIP Server",
                        "VIP Server $label",
                        directUrl,
                        INFER_TYPE
                    ) {
                        this.referer = "https://$directHost/"
                        this.headers = mapOf(
                            "Referer" to "https://$directHost/",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                            "Accept" to "*/*",
                            "Connection" to "keep-alive"
                        )
                        this.quality = quality
                    }
                )
                found = true
            }
            found
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun loadLinks(
        dataUrl: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val playPageUrl = if (dataUrl.endsWith("/play")) dataUrl else if (dataUrl.endsWith("/play/")) dataUrl.removeSuffix("/") else "${dataUrl.removeSuffix("/")}/play"
        val document = app.get(playPageUrl).document
        val serverList = mutableListOf<Pair<String, String>>() // Name to URL

        // 1. Extract from script window.famvServers
        val scriptContent = document.select("script").html()
        val match = Regex("""window\.famvServers\s*=\s*(\[.*?\]);""").find(scriptContent)
        if (match != null) {
            try {
                val jsonArray = JSONArray(match.groupValues[1])
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.optString("name", "Server ${i + 1}")
                    val embedUrl = obj.optString("url")
                    if (embedUrl.isNotBlank()) {
                        serverList.add(name to embedUrl)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Extract from player list buttons
        document.select("a.famv-server-btn, #player-list a, .player-option").forEach { btn ->
            val embedUrl = btn.attr("data-url").ifBlank { btn.attr("href") }
            val serverName = btn.attr("data-server").ifBlank { btn.text() }.ifBlank { "Server" }
            if (embedUrl.isNotBlank() && embedUrl != "#" && serverList.none { it.second == embedUrl }) {
                serverList.add(serverName to embedUrl)
            }
        }

        // 3. Extract direct iframe src
        document.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank() && !src.startsWith("about:") && serverList.none { it.second == src }) {
                serverList.add("Iframe Server" to src)
            }
        }

        Log.d("FilmapikProvider", "serverList: $serverList")

        var linksFound = false
        serverList.forEach { (serverName, embedUrl) ->
            try {
                if (embedUrl.contains("byseqekaho.com") || embedUrl.contains("byse.") || embedUrl.contains("filemoon") || serverName.contains("FILEMOON", ignoreCase = true)) {
                    if (extractByseFilemoon(embedUrl, callback)) {
                        linksFound = true
                    }
                } else if (embedUrl.contains("efek.stream") || serverName.contains("VIP", ignoreCase = true)) {
                    if (extractVipServer(embedUrl, playPageUrl, callback)) {
                        linksFound = true
                    }
                } else if (embedUrl.contains("abyssplayer.com") || embedUrl.contains("abyss") || embedUrl.contains("hydrax") || serverName.contains("HYDRAX", ignoreCase = true)) {
                    val slug = embedUrl.trimEnd('/').substringAfterLast("/")
                    val hydraxUrls = listOf(
                        "https://abysscdn.com/?v=$slug",
                        "https://short.ink/$slug",
                        "https://hydrax.net/watch?v=$slug",
                        embedUrl
                    )
                    for (hUrl in hydraxUrls) {
                        if (loadExtractor(hUrl, playPageUrl, subtitleCallback, callback)) {
                            linksFound = true
                            break
                        }
                    }
                } else {
                    if (loadExtractor(embedUrl, playPageUrl, subtitleCallback, callback)) {
                        linksFound = true
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        return linksFound
    }
}
