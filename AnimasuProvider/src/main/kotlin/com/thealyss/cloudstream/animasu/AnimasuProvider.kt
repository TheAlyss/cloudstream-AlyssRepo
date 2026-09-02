package com.thealyss.cloudstream.animasu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class AnimasuProvider : MainAPI() {
    override var mainUrl = "https://animasu.love"
    override var name = "Animasu"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = true
    override val instantLinkLoading = true
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.TvSeries,
        TvType.AnimeMovie
    )

    override val mainPage = mainPageOf(
        "$mainUrl/anime/?status=ongoing&order=update" to "Ongoing Anime",
        "$mainUrl/anime/?status=&order=popular" to "Popular Anime",
        "$mainUrl/anime/?status=&order=latest" to "Latest Updated",
        "$mainUrl/anime/?status=completed&order=latest" to "Completed Anime",
        "$mainUrl/anime/?status=&type=movie&order=latest" to "Anime Movies",
        "$mainUrl/genre/isekai/" to "Isekai Anime",
        "$mainUrl/genre/aksi/" to "Action Anime",
        "$mainUrl/genre/donghua/" to "Donghua (Chinese Anime)",
        "$mainUrl/genre/fantasi/" to "Fantasy Anime",
        "$mainUrl/genre/komedi/" to "Comedy Anime",
        "$mainUrl/genre/romansa/" to "Romance Anime"
    )

    private fun Element.toSearchResponse(): SearchResponse? {
        val a = this.selectFirst("a") ?: this.selectFirst(".bsx a") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null

        val rawTitle = a.attr("title").ifBlank {
            this.selectFirst(".tt")?.text() ?: a.text()
        }
        val cleanTitle = rawTitle
            .replace("Nonton Anime ", "", ignoreCase = true)
            .replace("Sub Indo", "", ignoreCase = true)
            .trim()

        if (cleanTitle.isBlank()) return null

        val posterUrl = this.selectFirst("img")?.let { img ->
            val src = img.attr("src")
            if (src.isNotBlank() && !src.contains("yandex") && !src.contains("logo") && !src.contains("asugirl")) {
                src
            } else {
                img.attr("data-src").ifBlank { img.attr("data-lazy-src") }
            }
        }

        val isTypeMovie = this.selectFirst(".typez")?.text()?.contains("Movie", ignoreCase = true) == true
        val tvType = if (isTypeMovie) TvType.AnimeMovie else TvType.Anime

        return newMovieSearchResponse(cleanTitle, href, tvType) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page <= 1) {
            request.data
        } else {
            if (request.data.contains("/anime/?")) {
                request.data.replace("/anime/?", "/anime/page/$page/?")
            } else if (request.data.contains("/genre/")) {
                "${request.data.removeSuffix("/")}/page/$page/"
            } else {
                "${request.data.removeSuffix("/")}/page/$page/"
            }
        }

        val document = app.get(url, referer = "$mainUrl/").document
        val home = document.select("div.bsx, div.animepost, div.bs")
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.trim().replace(" ", "+")}"
        val document = app.get(url, referer = "$mainUrl/").document
        return document.select("div.bsx, div.animepost, div.bs")
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, referer = "$mainUrl/").document

        val rawTitle = document.selectFirst("div.infox h1")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: ""
        val cleanTitle = rawTitle
            .replace("Nonton Anime ", "", ignoreCase = true)
            .replace("Sub Indo", "", ignoreCase = true)
            .trim()

        val poster = fixUrlNull(
            document.selectFirst("div.thumb img")?.let { img ->
                val src = img.attr("src")
                if (src.isNotBlank() && !src.contains("yandex") && !src.contains("logo") && !src.contains("asugirl")) src
                else img.attr("data-src").ifBlank { img.attr("data-lazy-src") }
            } ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val synopsis = document.select("div.sinopsis, div[itemprop=articleBody], span.desc").text().trim()
        val genres = document.select("div.spe span:contains(Genre) a, .genxed a, div.spe a[href*=/genre/]").map { it.text().trim() }

        val isOngoing = document.select("div.spe span:contains(Status)").text().contains("Sedang Tayang", ignoreCase = true)
        val showStatus = if (isOngoing) ShowStatus.Ongoing else ShowStatus.Completed

        val yearText = document.select("div.spe span:contains(Rilis), div.spe span:contains(Musim)").text()
        val year = Regex("""\b(19\d\d|20\d\d)\b""").find(yearText)?.value?.toIntOrNull()

        val isMovie = document.select("div.spe span:contains(Jenis)").text().contains("Movie", ignoreCase = true)

        val seen = HashSet<String>()
        val episodes = mutableListOf<Episode>()

        val epElements = document.select("ul.episodelst li, div.eplister li, .listupd li, div.bxcl li, .episodelst li")
        if (epElements.isNotEmpty()) {
            epElements.forEach { li ->
                val a = li.selectFirst("a") ?: return@forEach
                val href = fixUrlNull(a.attr("href")) ?: return@forEach
                if (!seen.add(href)) return@forEach
                val text = a.text().ifBlank { a.attr("title") }
                val epNum = Regex("""(?i)episode\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(?i)episode-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                val name = if (epNum != null) "Episode $epNum" else text.ifBlank { "Episode ${episodes.size + 1}" }
                episodes.add(
                    newEpisode(href) {
                        this.name = name
                        this.episode = epNum
                    }
                )
            }
        } else {
            document.select("a[href*=/nonton-]").forEach { a ->
                val href = fixUrlNull(a.attr("href")) ?: return@forEach
                if (!seen.add(href)) return@forEach
                val text = a.text().ifBlank { a.attr("title") }
                val epNum = Regex("""(?i)episode\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(?i)episode-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                val name = if (epNum != null) "Episode $epNum" else text.ifBlank { "Episode ${episodes.size + 1}" }
                episodes.add(
                    newEpisode(href) {
                        this.name = name
                        this.episode = epNum
                    }
                )
            }
        }

        val sortedEpisodes = episodes
            .distinctBy { it.data }
            .sortedBy { it.episode ?: 0 }

        if (isMovie && sortedEpisodes.size <= 1) {
            val playUrl = sortedEpisodes.firstOrNull()?.data ?: url
            return newMovieLoadResponse(cleanTitle, url, TvType.AnimeMovie, playUrl) {
                this.posterUrl = poster
                this.plot = synopsis.ifBlank { null }
                this.tags = if (genres.isNotEmpty()) genres else null
                this.year = year
            }
        }

        return newTvSeriesLoadResponse(cleanTitle, url, TvType.Anime, sortedEpisodes) {
            this.posterUrl = poster
            this.plot = synopsis.ifBlank { null }
            this.tags = if (genres.isNotEmpty()) genres else null
            this.year = year
            this.showStatus = showStatus
        }
    }

    private suspend fun extractYourUpload(url: String, callback: (ExtractorLink) -> Unit): Boolean {
        return try {
            val embedDoc = app.get(url, referer = "$mainUrl/").text
            val fileMatch = Regex("""file\s*:\s*['"]([^'"]+\.mp4[^'"]*)['"]""").find(embedDoc)
                ?: Regex("""property=["']og:video["']\s+content=["']([^"']+)["']""").find(embedDoc)
            val mp4Url = fileMatch?.groupValues?.get(1) ?: return false
            callback.invoke(
                newExtractorLink(
                    source = "YourUpload",
                    name = "YourUpload (720p Direct)",
                    url = mp4Url,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = "https://www.yourupload.com/"
                    this.headers = mapOf(
                        "Referer" to "https://www.yourupload.com/",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    this.quality = Qualities.P720.value
                }
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun extractBerkasDrive(url: String, callback: (ExtractorLink) -> Unit): Boolean {
        return try {
            val doc = app.get(url, referer = "$mainUrl/").text
            val videoMatch = Regex("""<source[^>]+src=["']([^"']+)["']|<video[^>]+src=["']([^"']+)["']""").find(doc)
                ?: Regex("""file\s*:\s*["']([^"']+\.mp4[^"']*)["']""").find(doc)
            val mp4Url = (videoMatch?.groupValues?.get(1)?.ifBlank { null } ?: videoMatch?.groupValues?.get(2)) ?: return false
            callback.invoke(
                newExtractorLink(
                    source = "BerkasDrive",
                    name = "BerkasDrive (720p Direct)",
                    url = mp4Url,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = url
                    this.headers = mapOf(
                        "Referer" to url,
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    this.quality = Qualities.P720.value
                }
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun extractAbyss(url: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        return try {
            val slug = url.split("/").filter { it.isNotBlank() }.lastOrNull() ?: return false
            val abysscdnUrl = "https://abysscdn.com/?v=$slug"
            val shortInkUrl = "https://short.ink/$slug"
            loadExtractor(abysscdnUrl, url, subtitleCallback, callback)
            loadExtractor(shortInkUrl, url, subtitleCallback, callback)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, referer = "$mainUrl/").document
        val seenUrls = HashSet<String>()

        // 1. Base64 encoded iframe mirrors in <select class="mirror">
        document.select("select.mirror option, select[name=mirror] option").forEach { option ->
            val b64 = option.attr("value").trim()
            if (b64.isNotBlank()) {
                try {
                    val decoded = base64Decode(b64)
                    val src = Regex("""src=["']([^"']+)["']""").find(decoded)?.groupValues?.get(1)
                    if (!src.isNullOrBlank()) {
                        val fixedSrc = fixUrl(src)
                        if (seenUrls.add(fixedSrc)) {
                            if (fixedSrc.contains("yourupload.com")) {
                                if (!extractYourUpload(fixedSrc, callback)) {
                                    loadExtractor(fixedSrc, data, subtitleCallback, callback)
                                }
                            } else if (fixedSrc.contains("berkasdrive.com") || fixedSrc.contains("mitedrive.com")) {
                                if (!extractBerkasDrive(fixedSrc, callback)) {
                                    loadExtractor(fixedSrc, data, subtitleCallback, callback)
                                }
                            } else if (fixedSrc.contains("abyssplayer.com") || fixedSrc.contains("abysscdn.com") || fixedSrc.contains("short.ink")) {
                                extractAbyss(fixedSrc, subtitleCallback, callback)
                            } else {
                                loadExtractor(fixedSrc, data, subtitleCallback, callback)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 2. Default player iframe in #pembed
        document.select("div.player-embed iframe, div#pembed iframe, div.responsive-embed-stream iframe").forEach { iframe ->
            val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
            if (src.isNotBlank() && !src.contains("facebook") && !src.contains("cbox")) {
                val fixedSrc = fixUrl(src)
                if (seenUrls.add(fixedSrc)) {
                    if (fixedSrc.contains("yourupload.com")) {
                        if (!extractYourUpload(fixedSrc, callback)) {
                            loadExtractor(fixedSrc, data, subtitleCallback, callback)
                        }
                    } else if (fixedSrc.contains("berkasdrive.com") || fixedSrc.contains("mitedrive.com")) {
                        if (!extractBerkasDrive(fixedSrc, callback)) {
                            loadExtractor(fixedSrc, data, subtitleCallback, callback)
                        }
                    } else if (fixedSrc.contains("abyssplayer.com") || fixedSrc.contains("abysscdn.com") || fixedSrc.contains("short.ink")) {
                        extractAbyss(fixedSrc, subtitleCallback, callback)
                    } else {
                        loadExtractor(fixedSrc, data, subtitleCallback, callback)
                    }
                }
            }
        }

        return seenUrls.isNotEmpty()
    }
}
