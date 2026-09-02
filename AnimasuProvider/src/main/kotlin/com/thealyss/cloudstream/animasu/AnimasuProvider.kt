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
        "$mainUrl/anime/?status=ongoing&order=update" to "Sedang Tayang (Ongoing)",
        "$mainUrl/anime/?status=&order=popular" to "Anime Paling Popular",
        "$mainUrl/anime/?status=&order=latest" to "Baru Ditambah & Diperbarui",
        "$mainUrl/anime/?status=completed&order=latest" to "Anime Tamat (Completed)",
        "$mainUrl/anime/?status=&type=movie&order=latest" to "Filem Anime (Anime Movies)",
        "$mainUrl/genre/isekai/" to "Anime Isekai",
        "$mainUrl/genre/aksi/" to "Anime Aksi",
        "$mainUrl/genre/donghua/" to "Donghua (Anime China)",
        "$mainUrl/genre/fantasi/" to "Anime Fantasi",
        "$mainUrl/genre/komedi/" to "Anime Komedi",
        "$mainUrl/genre/romansa/" to "Anime Romansa"
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

        val latestEp = this.selectFirst(".epx, .bt .epx")?.text()?.trim()
        val isTypeMovie = this.selectFirst(".typez")?.text()?.contains("Movie", ignoreCase = true) == true

        return newAnimeSearchResponse(cleanTitle, href, if (isTypeMovie) TvType.AnimeMovie else TvType.Anime) {
            this.posterUrl = fixUrlNull(posterUrl)
            addSub(latestEp)
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
        val home = document.select("div.bs, article.bs, div.bsx, div.animepost")
            .mapNotNull { it.toSearchResponse() }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.trim().replace(" ", "+")}"
        val document = app.get(url, referer = "$mainUrl/").document
        return document.select("div.bs, article.bs, div.bsx, div.animepost")
            .mapNotNull { it.toSearchResponse() }
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
        
        val ratingText = document.selectFirst("div.rating strong, .rtg strong")?.text()
        val rating = ratingText?.replace("Rating", "", ignoreCase = true)?.trim()?.toRatingInt()

        val isOngoing = document.select("div.spe span:contains(Status)").text().contains("Sedang Tayang", ignoreCase = true)
        val showStatus = if (isOngoing) ShowStatus.Ongoing else ShowStatus.Completed

        val yearText = document.select("div.spe span:contains(Rilis), div.spe span:contains(Musim)").text()
        val year = Regex("""\b(19\d\d|20\d\d)\b""").find(yearText)?.value?.toIntOrNull()

        val isMovie = document.select("div.spe span:contains(Jenis)").text().contains("Movie", ignoreCase = true)
        val tvType = if (isMovie) TvType.AnimeMovie else TvType.Anime

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

        episodes.sortBy { it.episode ?: 0 }

        return newAnimeLoadResponse(cleanTitle, url, tvType) {
            this.posterUrl = poster
            this.plot = synopsis.ifBlank { null }
            this.tags = if (genres.isNotEmpty()) genres else null
            this.rating = rating
            this.year = year
            this.showStatus = showStatus
            addEpisodes(DubStatus.Subbed, episodes)
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
                            loadExtractor(fixedSrc, data, subtitleCallback, callback)
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
                    loadExtractor(fixedSrc, data, subtitleCallback, callback)
                }
            }
        }

        return seenUrls.isNotEmpty()
    }
}
