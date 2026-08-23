package com.thealyss.cloudstream.moviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class MovieboxProvider : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    private val mainAPIUrl = "https://h5-api.aoneroom.com"
    private val secondAPIUrl = "https://filmboom.top"
    override val instantLinkLoading = true
    override var name = "MovieBox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "en-US,en;q=0.9,id;q=0.8",
        "Origin" to "https://filmboom.top",
        "Referer" to "https://filmboom.top/"
    )

    override val mainPage: List<MainPageData> = mainPageOf(
        "872031290915189720" to "Trending Now",
        "997144265920760504" to "Popular Movie",
        "5283462032510044280" to "Latest Indonesian Drama",
        "6528093688173053896" to "Trending Indonesian Movies",
        "4380734070238626200" to "K-Drama",
        "7736026911486755336" to "Western TV",
        "8624142774394406504" to "Most Popular C-Drama",
        "5404290953194750296" to "Trending Anime",
        "5848753831881965888" to "Indonesian Horror Stories",
        "1164329479448281992" to "Thai-Drama",
        "7132534597631837112" to "Animated Film",
        "1,ForYou" to "Movie ForYou",
        "1,Hottest" to "Movie Hottest",
        "1,Latest" to "Movie Latest",
        "1,Rating" to "Movie Rating",
        "2,ForYou" to "TVShow ForYou",
        "2,Hottest" to "TVShow Hottest",
        "2,Latest" to "TVShow Latest",
        "2,Rating" to "TVShow Rating",
        "1006,ForYou" to "Animation ForYou",
        "1006,Hottest" to "Animation Hottest",
        "1006,Latest" to "Animation Latest",
        "1006,Rating" to "Animation Rating",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest,
    ): HomePageResponse {
        val home = mutableListOf<SearchResponse>()

        if (!request.data.contains(",")) {
            val url = "$mainAPIUrl/wefeed-h5api-bff/ranking-list/content?id=${request.data}&page=$page&perPage=12"
            val index = app.get(url, headers = defaultHeaders).parsedSafe<Media>()?.data?.subjectList?.map {
                it.toSearchResponse(this)
            } ?: throw ErrorLoadingException("No Data Found")

            home.addAll(index)
        } else {
            val params = request.data.split(",")
            val body = mapOf(
                "channelId" to params.first(),
                "page" to page,
                "perPage" to "28",
                "sort" to params.last()
            ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

            val index = app.post(
                "$mainAPIUrl/wefeed-h5api-bff/subject/filter",
                headers = defaultHeaders,
                requestBody = body
            ).parsedSafe<Media>()?.data?.items?.map {
                it.toSearchResponse(this)
            } ?: throw ErrorLoadingException("No Data Found")

            home.addAll(index)
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val searchHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Accept" to "application/json, text/plain, */*",
            "Origin" to secondAPIUrl,
            "Referer" to "$secondAPIUrl/"
        )

        val requestBody = mapOf(
            "keyword" to query,
            "page" to "1",
            "perPage" to "20",
            "subjectType" to "0"
        ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        return try {
            val res = app.post(
                "$secondAPIUrl/wefeed-h5-bff/web/subject/search",
                headers = searchHeaders,
                requestBody = requestBody
            ).parsedSafe<Media>()

            res?.data?.items?.map {
                it.toSearchResponse(this)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfter("id=").substringBefore("&")

        val document = app.get(
            "$mainAPIUrl/wefeed-h5api-bff/subject/detail?id=$id",
            headers = defaultHeaders
        ).parsedSafe<MediaDetail>()?.data

        val subject = document?.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }

        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val rating = subject?.imdbRatingValue?.toIntOrNull()
        val actors = document?.stars?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: return@mapNotNull null,
                    cast.avatarUrl
                ),
                roleString = cast.character
            )
        }?.distinctBy { it.actor }

        val recommendations =
            app.get(
                "$mainUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12",
                headers = defaultHeaders
            ).parsedSafe<Media>()?.data?.items?.map {
                it.toSearchResponse(this)
            }

        return if (tvType == TvType.TvSeries) {
            val episode = document?.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..(seasons.maxEp ?: 1)) else seasons.allEp.split(",")
                    .mapNotNull { it.trim().toIntOrNull() })
                    .map { ep ->
                        newEpisode(
                            LoadData(
                                id,
                                seasons.se,
                                ep,
                                subject?.detailPath
                            ).toJson()
                        ) {
                            this.season = seasons.se
                            this.episode = ep
                        }
                    }
            }?.flatten() ?: emptyList()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episode) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(id, detailPath = subject?.detailPath).toJson()
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer, addRaw = true)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val media = parseJson<LoadData>(data)
        val referer = "$secondAPIUrl/spa/videoPlayPage/movies/${media.detailPath}?id=${media.id}&type=/movie/detail&lang=en"

        val playHeaders = defaultHeaders.toMutableMap().apply {
            put("Referer", referer)
        }

        val streams = app.get(
            "$secondAPIUrl/wefeed-h5-bff/web/subject/play?subjectId=${media.id}&se=${media.season ?: 0}&ep=${media.episode ?: 0}",
            headers = playHeaders,
            referer = referer
        ).parsedSafe<Media>()?.data?.streams

        streams?.reversed()?.distinctBy { it.url }?.forEach { source ->
            val streamUrl = source.url ?: return@forEach
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    streamUrl,
                    INFER_TYPE
                ) {
                    this.referer = "$secondAPIUrl/"
                    this.quality = getQualityFromName(source.resolutions)
                    this.headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                        "Referer" to "$secondAPIUrl/"
                    )
                }
            )
        }

        val firstStream = streams?.firstOrNull()
        if (firstStream != null) {
            val id = firstStream.id
            val format = firstStream.format

            app.get(
                "$secondAPIUrl/wefeed-h5-bff/web/subject/caption?format=$format&id=$id&subjectId=${media.id}",
                headers = playHeaders,
                referer = referer
            ).parsedSafe<Media>()?.data?.captions?.forEach { subtitle ->
                val subUrl = subtitle.url ?: return@forEach
                subtitleCallback.invoke(
                    newSubtitleFile(
                        subtitle.lanName ?: subtitle.lan ?: "Subtitle",
                        subUrl
                    )
                )
            }
        }

        return true
    }

    data class LoadData(
        val id: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val detailPath: String? = null,
    )

    data class Media(
        @JsonProperty("data") val data: Data? = null,
    ) {
        data class Data(
            @JsonProperty("subjectList") val subjectList: ArrayList<Items>? = arrayListOf(),
            @JsonProperty("items") val items: ArrayList<Items>? = arrayListOf(),
            @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
            @JsonProperty("captions") val captions: ArrayList<Captions>? = arrayListOf(),
        ) {
            data class Streams(
                @JsonProperty("id") val id: String? = null,
                @JsonProperty("format") val format: String? = null,
                @JsonProperty("url") val url: String? = null,
                @JsonProperty("resolutions") val resolutions: String? = null,
            )

            data class Captions(
                @JsonProperty("lan") val lan: String? = null,
                @JsonProperty("lanName") val lanName: String? = null,
                @JsonProperty("url") val url: String? = null,
            )
        }
    }

    data class MediaDetail(
        @JsonProperty("data") val data: Data? = null,
    ) {
        data class Data(
            @JsonProperty("subject") val subject: Items? = null,
            @JsonProperty("stars") val stars: ArrayList<Stars>? = arrayListOf(),
            @JsonProperty("resource") val resource: Resource? = null,
        ) {
            data class Stars(
                @JsonProperty("name") val name: String? = null,
                @JsonProperty("character") val character: String? = null,
                @JsonProperty("avatarUrl") val avatarUrl: String? = null,
            )

            data class Resource(
                @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
            ) {
                data class Seasons(
                    @JsonProperty("se") val se: Int? = null,
                    @JsonProperty("maxEp") val maxEp: Int? = null,
                    @JsonProperty("allEp") val allEp: String? = null,
                )
            }
        }
    }

    data class Items(
        @JsonProperty("subjectId") val subjectId: String? = null,
        @JsonProperty("subjectType") val subjectType: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("releaseDate") val releaseDate: String? = null,
        @JsonProperty("duration") val duration: Long? = null,
        @JsonProperty("genre") val genre: String? = null,
        @JsonProperty("cover") val cover: Cover? = null,
        @JsonProperty("imdbRatingValue") val imdbRatingValue: String? = null,
        @JsonProperty("countryName") val countryName: String? = null,
        @JsonProperty("trailer") val trailer: Trailer? = null,
        @JsonProperty("detailPath") val detailPath: String? = null,
    ) {

        fun toSearchResponse(provider: MovieboxProvider): SearchResponse {
            return provider.newMovieSearchResponse(
                title ?: "",
                subjectId ?: "",
                if (subjectType == 1) TvType.Movie else TvType.TvSeries,
                false
            ) {
                this.posterUrl = cover?.url
            }
        }

        data class Cover(
            @JsonProperty("url") val url: String? = null,
        )

        data class Trailer(
            @JsonProperty("videoAddress") val videoAddress: VideoAddress? = null,
        ) {
            data class VideoAddress(
                @JsonProperty("url") val url: String? = null,
            )
        }
    }
}
