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

    private suspend fun extractStreamsFromJson(html: String, pageUrl: String): List<ExtractorLink> {
        val links = mutableListOf<ExtractorLink>()
        val streamsMatch = Regex("""const\s+streams\s*=\s*(\[[\s\S]*?\]);""").find(html) ?: return links
        try {
            val jsonArray = JSONArray(streamsMatch.groupValues[1])
            val serverTypeCounts = mutableMapOf<String, Int>()
            val parsedItems = mutableListOf<Triple<String, Int, String>>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val rawLink = obj.optString("link")
                val reso = obj.optString("reso", "720p")
                if (rawLink.isNotBlank()) {
                    val linkUrl = rawLink.replace("\\/", "/")
                    val qualityInt = when {
                        reso.contains("1080") || linkUrl.contains("1080p") -> Qualities.P1080.value
                        reso.contains("720") || linkUrl.contains("720p") -> Qualities.P720.value
                        reso.contains("480") || linkUrl.contains("480p") -> Qualities.P480.value
                        reso.contains("360") || linkUrl.contains("360p") -> Qualities.P360.value
                        else -> Qualities.P720.value
                    }
                    val baseServerName = when {
                        linkUrl.contains("pixeldrain.com") -> "Pixeldrain"
                        linkUrl.contains("storage.animekita.org") || linkUrl.contains("animekita") -> "YLnime (Fast CDN)"
                        linkUrl.contains("blogger.com") || linkUrl.contains("googlevideo.com") -> "Blogger"
                        linkUrl.contains("vidhide") -> "VidHide"
                        else -> "YLnime Server"
                    }
                    serverTypeCounts[baseServerName] = (serverTypeCounts[baseServerName] ?: 0) + 1
                    parsedItems.add(Triple(baseServerName, qualityInt, linkUrl))
                }
            }

            val currentServerIndex = mutableMapOf<String, Int>()
            for ((baseServerName, qualityInt, linkUrl) in parsedItems) {
                val totalCount = serverTypeCounts[baseServerName] ?: 1
                val displayName = if (totalCount > 1) {
                    val idx = (currentServerIndex[baseServerName] ?: 0) + 1
                    currentServerIndex[baseServerName] = idx
                    if (baseServerName.contains("Fast CDN")) "YLnime (Fast CDN $idx)" else "$baseServerName $idx"
                } else {
                    baseServerName
                }

                if (linkUrl.contains(".m3u8")) {
                    M3u8Helper.generateM3u8(
                        source = displayName,
                        streamUrl = linkUrl,
                        referer = pageUrl
                    ).forEach { mLink ->
                        links.add(
                            newExtractorLink(
                                source = displayName,
                                name = displayName,
                                url = mLink.url,
                                type = INFER_TYPE
                            ) {
                                this.referer = pageUrl
                                this.quality = qualityInt
                            }
                        )
                    }
                } else {
                    links.add(
                        newExtractorLink(
                            source = displayName,
                            name = displayName,
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return links
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
        val visitedUrls = mutableSetOf(fixedUrl)

        // 1. Extract streams from base page
        collectedLinks.addAll(extractStreamsFromJson(html, fixedUrl))

        // 2. Extract from resolution buttons (1080p, 720p, 480p, 360p)
        val resoLinks = document.select("a[href*='reso=']").map { it.attr("href") }
            .map { fixYlnimeUrl(it) }
            .distinct()
            .sortedByDescending { url ->
                when {
                    url.contains("reso=1080") -> 4
                    url.contains("reso=720") -> 3
                    url.contains("reso=480") -> 2
                    url.contains("reso=360") -> 1
                    else -> 0
                }
            }

        val allResoUrls = resoLinks.toMutableList()
        val url1080Constructed = if (!fixedUrl.contains("&reso=")) "$fixedUrl&reso=1080p" else null
        if (url1080Constructed != null && !allResoUrls.any { it.contains("reso=1080") }) {
            allResoUrls.add(0, url1080Constructed)
        }

        for (resoUrl in allResoUrls) {
            if (visitedUrls.add(resoUrl)) {
                try {
                    val resoDoc = app.get(resoUrl, referer = "$mainUrl/").document
                    collectedLinks.addAll(extractStreamsFromJson(resoDoc.html(), resoUrl))
                } catch (e: Exception) {
                    // Ignore errors for optional resolution pages
                }
            }
        }

        // 3. Extract embedded iframes if any
        document.select("iframe[src]").forEach { iframe ->
            val src = fixUrlNull(iframe.attr("src")) ?: return@forEach
            if (!src.contains("ads") && !src.contains("cbox") && !src.contains("facebook")) {
                loadExtractor(src, fixedUrl, subtitleCallback) { link ->
                    collectedLinks.add(link)
                }
            }
        }

        // 4. Deduplicate and sort so 1080p FHD plays FIRST!
        val sortedLinks = collectedLinks
            .distinctBy { it.url }
            .sortedByDescending { it.quality }

        sortedLinks.forEach(callback)

        return sortedLinks.isNotEmpty()
    }
}
