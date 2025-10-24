package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Moviebox : MainAPI() {
    override var mainUrl = "https://moviebox.ph"
    override var name = "Moviebox"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val iconUrl = "https://moviebox.ph/favicon.ico"

    override val mainPage = mainPageOf(
        "$mainUrl/wefeed-h5-bff/web/home/subject?page=1&perPage=24" to "Home",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = app.get(request.data.replace("page=1", "page=$page"))
        val media = res.parsedSafe<Media>()?.data?.items ?: emptyList()
        return newHomePageResponse(
            HomePageList(request.name, media.map { it.toSearchResponse(this) })
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$mainUrl/wefeed-h5-bff/web/subject/search?keyword=$query&page=1&perPage=20")
        val results = res.parsedSafe<Media>()?.data?.items ?: emptyList()
        return results.map { it.toSearchResponse(this) }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast("/")
        val document = app.get("$mainUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id")
            .parsedSafe<MediaDetail>()?.data
        val subject = document?.subject
        val title = subject?.title ?: ""
        val poster = subject?.cover?.url
        val tags = subject?.genre?.split(",")?.map { it.trim() }
        val year = subject?.releaseDate?.substringBefore("-")?.toIntOrNull()
        val tvType = if (subject?.subjectType == 2) TvType.TvSeries else TvType.Movie
        val description = subject?.description
        val trailer = subject?.trailer?.videoAddress?.url
        val score = subject?.imdbRatingValue?.toFloatOrNull()

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
            app.get("$mainUrl/wefeed-h5-bff/web/subject/detail-rec?subjectId=$id&page=1&perPage=12")
                .parsedSafe<Media>()?.data?.items?.map {
                    it.toSearchResponse(this)
                }

        return if (tvType == TvType.TvSeries) {
            val episodes = document?.resource?.seasons?.map { seasons ->
                (if (seasons.allEp.isNullOrEmpty()) (1..(seasons.maxEp ?: 1)) else seasons.allEp.split(",")
                    .mapNotNull { it.toIntOrNull() })
                    .map { episode ->
                        newEpisode(
                            LoadData(id, seasons.se, episode, subject?.detailPath).toJson()
                        ) {
                            this.season = seasons.se
                            this.episode = episode
                        }
                    }
            }?.flatten() ?: emptyList()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = score
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
                this.score = score
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
        val loadData = parseJson<LoadData>(data)
        val id = loadData.id
        val episode = loadData.episode
        val season = loadData.season

        val res = app.get("$mainUrl/wefeed-h5-bff/web/subject/play-info?subjectId=$id&season=$season&episode=$episode")
            .parsedSafe<StreamData>()?.data?.videos ?: return false

        res.forEach {
            callback(
                ExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = it.videoAddress.url,
                    referer = "$mainUrl/",
                    quality = getQualityFromName(it.videoAddress.quality),
                    isM3u8 = it.videoAddress.url.contains(".m3u8")
                )
            )
        }
        return true
    }
}

data class LoadData(
    val id: String,
    val season: Int? = null,
    val episode: Int? = null,
    val detailPath: String? = null
)

data class Media(val data: MediaData?)
data class MediaData(val items: List<Item>?)
data class Item(
    val id: String?,
    val title: String?,
    val cover: Cover?,
    val subjectType: Int?,
    val releaseDate: String?,
) {
    fun toSearchResponse(api: MainAPI): SearchResponse {
        val url = "${api.mainUrl}/subject/${id ?: ""}"
        val posterUrl = cover?.url
        val tvType = if (subjectType == 2) TvType.TvSeries else TvType.Movie
        return newTvSeriesSearchResponse(title ?: "", url, tvType) {
            this.posterUrl = posterUrl
        }
    }
}

data class MediaDetail(val data: MediaDetailData?)
data class MediaDetailData(
    val subject: Subject?,
    val stars: List<Cast>?,
    val resource: Resource?
)

data class Subject(
    val title: String?,
    val cover: Cover?,
    val description: String?,
    val releaseDate: String?,
    val genre: String?,
    val imdbRatingValue: String?,
    val subjectType: Int?,
    val trailer: Trailer?,
    val detailPath: String?
)

data class Cover(val url: String?)
data class Trailer(val videoAddress: VideoAddress?)
data class VideoAddress(val url: String?, val quality: String?)
data class Cast(val name: String?, val avatarUrl: String?, val character: String?)
data class Resource(val seasons: List<Season>?)
data class Season(val se: Int?, val allEp: String?, val maxEp: Int?)
data class StreamData(val data: StreamVideo?)
data class StreamVideo(val videos: List<Video>?)
data class Video(val videoAddress: VideoAddress)
