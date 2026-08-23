package com.thealyss.cloudstream.filmapik

import android.util.Log
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

        val playUrl = if (url.endsWith("/play/")) url else "${url.removeSuffix("/")}/play/"

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
                            val epPlayUrl = if (epHref.endsWith("/play/")) epHref else "${epHref.removeSuffix("/")}/play/"
                            val rawEpText = epLink.text().trim()

                            val seasonFromText = Regex("""(?i)S(\d+)\s*[:\sE]*\s*E?(\d+)""").find(rawEpText)
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
                                .replace(Regex("(?i)^Nonton\\s+(?:Film|Series|Drama)?\\s*"), "")
                                .replace(Regex("(?i)\\s+Subtitle\\s+Indonesia.*$"), "")
                                .replace(Regex("(?i)^S\\d+\\s*[:\\sE]*\\s*E?\\d+\\s*[-:]?\\s*"), "")
                                .replace(Regex("(?i)^(?:EP|Episode)\\s*\\d+\\s*[-:]?\\s*"), "")
                                .trim()

                            val displayName = if (cleanTitle.isNotBlank() && !cleanTitle.equals(epNum.toString(), ignoreCase = true)) {
                                "Episode $epNum: $cleanTitle"
                            } else {
                                "Episode $epNum"
                            }

                            episodes.add(
                                newEpisode(epPlayUrl) {
                                    this.name = displayName
                                    this.season = seasonNum
                                    this.episode = epNum
                                }
                            )
                        }
                    }
                }
            } else {
                document.select("a.famv-episode-btn, a[href*='/episodes/'], a[href*='/episode/']").forEach { epLink ->
                    val epHref = epLink.attr("href")
                    if (epHref.isNotBlank() && epHref != "#") {
                        val epPlayUrl = if (epHref.endsWith("/play/")) epHref else "${epHref.removeSuffix("/")}/play/"
                        val rawEpText = epLink.text().trim()

                        val seasonFromText = Regex("""(?i)S(\d+)\s*[:\sE]*\s*E?(\d+)""").find(rawEpText)
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
                            .replace(Regex("(?i)^Nonton\\s+(?:Film|Series|Drama)?\\s*"), "")
                            .replace(Regex("(?i)\\s+Subtitle\\s+Indonesia.*$"), "")
                            .replace(Regex("(?i)^S\\d+\\s*[:\\sE]*\\s*E?\\d+\\s*[-:]?\\s*"), "")
                            .replace(Regex("(?i)^(?:EP|Episode)\\s*\\d+\\s*[-:]?\\s*"), "")
                            .trim()

                        val displayName = if (cleanTitle.isNotBlank() && !cleanTitle.equals(epNum.toString(), ignoreCase = true)) {
                            "Episode $epNum: $cleanTitle"
                        } else {
                            "Episode $epNum"
                        }

                        episodes.add(
                            newEpisode(epPlayUrl) {
                                this.name = displayName
                                this.season = seasonNum
                                this.episode = epNum
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
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, playUrl) {
            this.posterUrl = posterUrl
            this.backgroundPosterUrl = backdropUrl ?: posterUrl
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
        val playPageUrl = if (dataUrl.endsWith("/play/")) dataUrl else "${dataUrl.removeSuffix("/")}/play/"
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
                if (loadExtractor(embedUrl, playPageUrl, subtitleCallback, callback)) {
                    linksFound = true
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        return linksFound
    }
}
