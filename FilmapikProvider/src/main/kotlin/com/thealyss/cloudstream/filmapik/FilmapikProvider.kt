package com.thealyss.cloudstream.filmapik

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.json.JSONArray

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
        val homeItems = document.select("a.group, article.card a").mapNotNull { element ->
            toSearchResult(element)
        }
        return newHomePageResponse(request.name, homeItems)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val href = element.attr("href")
        if (href.isBlank() || href == "#") return null

        val imgElement = element.selectFirst("img") ?: return null
        val title = element.selectFirst("h3")?.text()
            ?: imgElement.attr("alt")
            ?: return null

        val cleanTitle = title
            .replace(Regex("(?i)^Nonton\\s+Film\\s+"), "")
            .replace(Regex("(?i)\\s+Subtitle\\s+Indonesia$"), "")
            .trim()

        val posterUrl = imgElement.attr("src").ifBlank {
            val srcset = imgElement.attr("srcset")
            if (srcset.isNotBlank()) srcset.substringBefore(" ") else ""
        }

        val quality = element.selectFirst(".badge-quality")?.text() ?: ""
        val isTvShow = href.contains("/tvshows-genre/") || href.contains("/series/") || href.contains("/season/")

        val tvType = if (isTvShow) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(cleanTitle, href, tvType) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(searchUrl).document
        return document.select("a.group, article.card a").mapNotNull { element ->
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

        val posterUrl = document.selectFirst("span.famv-img-shimmer img, div.w-48 img")?.attr("src")
        val plot = document.selectFirst(".famv-truncated-text p, div.famv-truncated-text")?.text()
        val yearText = document.selectFirst(".badge-cyan")?.text()
        val year = yearText?.let { Regex("(\\d{4})").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val genres = document.select("a[href*='/category/']").map { it.text() }

        val playUrl = if (url.endsWith("/play/")) url else "${url.removeSuffix("/")}/play/"

        val isTvShow = url.contains("/tvshows-genre/") || url.contains("/series/") || url.contains("/season/")

        if (isTvShow) {
            val episodes = listOf(
                newEpisode(playUrl) {
                    this.name = "Episode 1"
                    this.season = 1
                    this.episode = 1
                }
            )
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.plot = plot
                this.year = year
                this.tags = genres
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, playUrl) {
            this.posterUrl = posterUrl
            this.plot = plot
            this.year = year
            this.tags = genres
        }
    }

    override suspend fun loadLinks(
        dataUrl: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(dataUrl).document
        val serverUrls = mutableListOf<String>()

        // 1. Extract from script window.famvServers
        val scriptContent = document.select("script").html()
        val match = Regex("""window\.famvServers\s*=\s*(\[.*?\]);""").find(scriptContent)
        if (match != null) {
            try {
                val jsonArray = JSONArray(match.groupValues[1])
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val embedUrl = obj.optString("url")
                    if (embedUrl.isNotBlank()) {
                        serverUrls.add(embedUrl)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Extract from player list buttons
        document.select("a.famv-server-btn, #player-list a").forEach { btn ->
            val embedUrl = btn.attr("data-url").ifBlank { btn.attr("href") }
            if (embedUrl.isNotBlank() && embedUrl != "#" && !serverUrls.contains(embedUrl)) {
                serverUrls.add(embedUrl)
            }
        }

        // 3. Extract direct iframe src
        document.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank() && !src.startsWith("about:") && !serverUrls.contains(src)) {
                serverUrls.add(src)
            }
        }

        // Load extractors for each server URL found
        serverUrls.forEach { embedUrl ->
            loadExtractor(embedUrl, dataUrl, subtitleCallback, callback)
        }

        return serverUrls.isNotEmpty()
    }
}
