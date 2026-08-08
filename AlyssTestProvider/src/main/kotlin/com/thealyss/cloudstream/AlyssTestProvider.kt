package com.thealyss.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

@Suppress("DEPRECATION")
class AlyssTestProvider : MainAPI() {
    override var mainUrl = "https://alyss-test-provider.example.com"
    override var name = "Alyss Test Provider"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override var lang = "en"
    override val hasMainPage = true

    override suspend fun search(query: String): List<SearchResponse> {
        return listOf(
            newMovieSearchResponse("Test Movie Result: $query", "$mainUrl/movie/test-123", TvType.Movie) {
                this.posterUrl = "https://via.placeholder.com/300x450"
            }
        )
    }

    override suspend fun load(url: String): LoadResponse {
        return newMovieLoadResponse("Alyss Test Movie", url, TvType.Movie, url) {
            this.posterUrl = "https://via.placeholder.com/300x450"
            this.plot = "This is a test movie description to verify the CloudStream plugin implementation."
            this.year = 2026
        }
    }

    override suspend fun loadLinks(
        dataUrl: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        subtitleCallback.invoke(
            SubtitleFile(
                lang = "English",
                url = "https://example.com/subtitles/english.srt"
            )
        )

        callback.invoke(
            newExtractorLink(
                source = name,
                name = "Test Stream 1080p",
                url = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_1MB.mp4"
            ) {
                this.referer = mainUrl
                this.quality = Qualities.P1080.value
            }
        )

        return true
    }
}
