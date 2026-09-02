package com.thealyss.cloudstream.ylnime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.jsoup.nodes.Element

class YlnimeProvider : MainAPI() {
    override var mainUrl = "https://ylnime.com"
    override var name = "YLnime"
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
        "$mainUrl/baru.php" to "Latest Updated",
        "$mainUrl/ongoing.php" to "Ongoing Anime",
        "$mainUrl/hyper.php" to "Popular Anime",
        "$mainUrl/completed.php" to "Completed Anime",
        "$mainUrl/movies.php" to "Anime Movies",
        "$mainUrl/index.php?search=Action" to "Action Anime",
        "$mainUrl/index.php?search=Isekai" to "Isekai Anime",
        "$mainUrl/index.php?search=Fantasy" to "Fantasy Anime",
        "$mainUrl/index.php?search=Romance" to "Romance Anime",
        "$mainUrl/index.php?search=Comedy" to "Comedy Anime",
        "$mainUrl/anime-list.php" to "A-Z Anime List"
    )

    private fun fixYlnimeUrl(url: String): String {
        val clean = url.trim()
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> {
                if (clean.contains("ylnime.com/?")) {
                    clean.replace("ylnime.com/?", "ylnime.com/index.php?")
                } else if (clean.matches(Regex("""https?://ylnime\.com\?(.*)"""))) {
                    clean.replaceFirst("ylnime.com?", "ylnime.com/index.php?")
                } else {
                    clean
                }
            }
            clean.startsWith("?") -> "$mainUrl/index.php$clean"
            clean.startsWith("/?") -> "$mainUrl/index.php?${clean.removePrefix("/?")}"
            clean.startsWith("/") -> "$mainUrl$clean"
            clean.startsWith("index.php") -> "$mainUrl/$clean"
            else -> "$mainUrl/$clean"
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val a = this.selectFirst("a.stretched-link")
            ?: this.selectFirst("a[href*='series=']")
            ?: this.selectFirst("a")
            ?: return null

        val rawHref = a.attr("href")
        if (!rawHref.contains("series=")) return null
        val href = fixYlnimeUrl(rawHref)

        val rawTitle = this.selectFirst(".card-title")?.text()
            ?: this.selectFirst("h6, h5")?.text()
            ?: a.attr("title").ifBlank { a.text() }

        val cleanTitle = rawTitle
            .replace("Nonton Anime ", "", ignoreCase = true)
            .replace("Sub Indo", "", ignoreCase = true)
            .trim()

        if (cleanTitle.isBlank()) return null

        val posterUrl = this.selectFirst("img")?.let { img ->
            val src = img.attr("src")
            if (src.isNotBlank() && !src.contains("placeholder")) src
            else img.attr("data-src").ifBlank { img.attr("data-lazy-src") }
        }

        val isTypeMovie = this.selectFirst("small")?.text()?.contains("Movie", ignoreCase = true) == true
            || href.contains("movie", ignoreCase = true)
        val tvType = if (isTypeMovie) TvType.AnimeMovie else TvType.Anime

        return newAnimeSearchResponse(cleanTitle, href, tvType) {
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
            if (request.data.contains("?")) {
                "${request.data}&page=$page"
            } else {
                "${request.data}?page=$page"
            }
        }

        val document = app.get(url, referer = "$mainUrl/").document
        val home = document.select("div.card, div.col-6, div.col-md-3, div.col-lg-2")
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?search=${query.trim().replace(" ", "+")}"
        val document = app.get(url, referer = "$mainUrl/").document
        return document.select("div.card, div.col-6, div.col-md-3, div.col-lg-2")
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val fixedUrl = fixYlnimeUrl(url)
        val document = app.get(fixedUrl, referer = "$mainUrl/").document

        val rawTitle = document.selectFirst("h1")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore(" Sub Indo")
            ?: ""
        val cleanTitle = rawTitle
            .replace("Nonton Anime ", "", ignoreCase = true)
            .replace("Nonton ", "", ignoreCase = true)
            .replace("Sub Indo HD", "", ignoreCase = true)
            .replace("Sub Indo", "", ignoreCase = true)
            .trim()

        val poster = fixUrlNull(
            document.selectFirst("img.card-img-top, div.series-poster img, img[src*='myanimelist'], img[src*='animekita']")?.let { img ->
                val src = img.attr("src")
                if (src.isNotBlank() && !src.contains("placeholder")) src
                else img.attr("data-src").ifBlank { img.attr("data-lazy-src") }
            } ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        )

        val synopsis = document.select("div.synopsis, p.synopsis, div.description, div.content, div.card-body p").text().trim()
            .replace("Dukung YLnime", "", ignoreCase = true)
            .trim()

        val genres = document.select("a[href*='search='], span.badge, div.genres a")
            .map { it.text().trim() }
            .filter { it.length > 1 && !it.contains("Sub", true) && !it.contains("Episode", true) && !it.equals("0") }

        val isOngoing = document.select("span:contains(Ongoing), div:contains(Ongoing)").isNotEmpty()
        val showStatus = if (isOngoing) ShowStatus.Ongoing else ShowStatus.Completed

        val isMovie = url.contains("movie", ignoreCase = true) || document.select("small:contains(Movie)").isNotEmpty()

        val episodes = mutableListOf<Episode>()
        val seen = HashSet<String>()

        document.select("a[href*='episode=']").forEach { a ->
            val href = fixYlnimeUrl(a.attr("href"))
            if (!seen.add(href)) return@forEach
            val rawText = a.text().replace("\r", " ").replace("\n", " ").trim()
            val epNum = Regex("""(?i)episode\s*(\d+)""").find(rawText)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""-al-\d+-(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
            val epName = if (epNum != null) {
                if (rawText.contains("(End)", true) || rawText.contains("Tamat", true)) "Episode $epNum (End)" else "Episode $epNum"
            } else {
                rawText.ifBlank { "Episode ${episodes.size + 1}" }
            }
            episodes.add(
                newEpisode(href) {
                    this.name = epName
                    this.episode = epNum
                }
            )
        }

        val sortedEpisodes = episodes
            .distinctBy { it.data }
            .sortedBy { it.episode ?: 0 }

        if (isMovie && sortedEpisodes.size <= 1) {
            val playUrl = sortedEpisodes.firstOrNull()?.data ?: fixedUrl
            return newMovieLoadResponse(cleanTitle, fixedUrl, TvType.AnimeMovie, playUrl) {
                this.posterUrl = poster
                this.plot = synopsis.ifBlank { null }
                this.tags = if (genres.isNotEmpty()) genres else null
            }
        }

        return newTvSeriesLoadResponse(cleanTitle, fixedUrl, TvType.Anime, sortedEpisodes) {
            this.posterUrl = poster
            this.plot = synopsis.ifBlank { null }
            this.tags = if (genres.isNotEmpty()) genres else null
            this.showStatus = showStatus
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val fixedUrl = fixYlnimeUrl(data)
        val document = app.get(fixedUrl, referer = "$mainUrl/").document
        val html = document.html()
        val collectedLinks = mutableListOf<ExtractorLink>()

        // 1. Extract JSON streams array
        val streamsMatch = Regex("""const\s+streams\s*=\s*(\[[\s\S]*?\]);""").find(html)
        if (streamsMatch != null) {
            try {
                val jsonArray = JSONArray(streamsMatch.groupValues[1])
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val rawLink = obj.optString("link")
                    val reso = obj.optString("reso", "720p")
                    if (rawLink.isNotBlank()) {
                        val linkUrl = rawLink.replace("\\/", "/")
                        val qualityInt = when {
                            reso.contains("1080") -> Qualities.P1080.value
                            reso.contains("720") -> Qualities.P720.value
                            reso.contains("480") -> Qualities.P480.value
                            reso.contains("360") -> Qualities.P360.value
                            else -> Qualities.P720.value
                        }
                        val qualityLabel = when (qualityInt) {
                            Qualities.P1080.value -> "1080p FHD"
                            Qualities.P720.value -> "720p HD"
                            Qualities.P480.value -> "480p SD"
                            Qualities.P360.value -> "360p SD"
                            else -> "${qualityInt}p"
                        }
                        val serverName = when {
                            linkUrl.contains("pixeldrain.com") -> "Pixeldrain"
                            linkUrl.contains("storage.animekita.org") -> "YLnime (Fast CDN)"
                            linkUrl.contains("blogger.com") || linkUrl.contains("googlevideo.com") -> "Blogger"
                            linkUrl.contains("vidhide") -> "VidHide"
                            else -> "YLnime"
                        }

                        if (linkUrl.contains(".m3u8")) {
                            M3u8Helper.generateM3u8(
                                source = serverName,
                                streamUrl = linkUrl,
                                referer = fixedUrl
                            ).forEach { mLink ->
                                collectedLinks.add(
                                    newExtractorLink(
                                        source = serverName,
                                        name = "$serverName - $qualityLabel",
                                        url = mLink.url,
                                        type = INFER_TYPE
                                    ) {
                                        this.referer = fixedUrl
                                        this.quality = qualityInt
                                    }
                                )
                            }
                        } else {
                            collectedLinks.add(
                                newExtractorLink(
                                    source = serverName,
                                    name = "$serverName - $qualityLabel",
                                    url = linkUrl,
                                    type = INFER_TYPE
                                ) {
                                    this.referer = "https://ylnime.com/"
                                    this.headers = mapOf(
                                        "Referer" to "https://ylnime.com/",
                                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                    )
                                    this.quality = qualityInt
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Extract embedded iframes if any
        document.select("iframe[src]").forEach { iframe ->
            val src = fixUrlNull(iframe.attr("src")) ?: return@forEach
            if (!src.contains("ads") && !src.contains("cbox") && !src.contains("facebook")) {
                loadExtractor(src, fixedUrl, subtitleCallback) { link ->
                    collectedLinks.add(link)
                }
            }
        }

        // 3. Deduplicate and sort so highest quality runs FIRST!
        val sortedLinks = collectedLinks
            .distinctBy { it.url }
            .sortedByDescending { it.quality }

        sortedLinks.forEach(callback)

        return sortedLinks.isNotEmpty()
    }
}
