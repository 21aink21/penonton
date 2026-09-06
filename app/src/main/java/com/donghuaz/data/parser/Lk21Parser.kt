package com.donghuaz.data.parser

import android.util.LruCache
import com.donghuaz.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object Lk21Parser {

    const val MOVIE_BASE = "https://tv12.lk21official.cc"
    const val SERIES_BASE = "https://tv9.nontondrama.my"
    private const val SEARCH_API = "https://gudangvape.com/search.php"
    private const val POSTER_BASE = "https://poster.assetsy.de/wp-content/uploads/"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val gson = Gson()

    // Caches
    private var cachedBanners: Pair<Long, List<AnimeItem>>? = null
    private var cachedLatest: Pair<Long, List<AnimeItem>>? = null
    private var cachedSchedule: Pair<Long, List<WeekdaySchedule>>? = null
    private var cachedRankings: Pair<Long, List<AnimeItem>>? = null
    private val detailCache = LruCache<Int, AnimeDetail>(50)
    private val idToUrlMap = HashMap<Int, String>()

    private const val CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes

    private suspend fun fetchHtml(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        if (!referer.isNullOrEmpty()) {
            reqBuilder.header("Referer", referer)
        }
        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP Error ${response.code} on $url")
            response.body?.string() ?: ""
        }
    }

    private fun parseCard(article: Element, baseUrl: String): AnimeItem? {
        val a = article.selectFirst("figure a") ?: article.selectFirst("a[itemprop=url]") ?: return null
        val href = a.attr("href")
        if (href.isEmpty() || href == "#") return null

        val slug = href.removePrefix("/")
        val id = slug.hashCode()
        val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
        idToUrlMap[id] = fullUrl

        val title = article.selectFirst("figcaption h3")?.text()?.ifEmpty { null }
            ?: a.attr("title").replace(Regex("^(Nonton|Streaming|movie|series)\\s*", RegexOption.IGNORE_CASE), "").trim()

        val rating = article.selectFirst("span.rating span[itemprop=ratingValue]")?.text()
            ?: article.selectFirst("span.rating")?.text()?.replace("★", "")?.trim()
            ?: ""

        val year = article.selectFirst("span.year")?.text() ?: ""
        val eps = article.selectFirst("span.episode strong")?.text() ?: ""
        val quality = article.selectFirst("span.label")?.text() ?: ""

        val badge = when {
            eps.isNotEmpty() -> "EPS $eps"
            quality.isNotEmpty() -> quality
            year.isNotEmpty() -> year
            else -> "Movie"
        }

        var poster = article.selectFirst("picture img")?.let {
            val dSrc = it.attr("data-src")
            val src = it.attr("src")
            if (dSrc.isNotEmpty()) dSrc else src
        } ?: article.selectFirst("img")?.attr("src") ?: ""

        if (poster.startsWith("/")) {
            poster = "$POSTER_BASE${poster.removePrefix("/")}"
        }

        return AnimeItem(id, title, badge, rating, poster, fullUrl)
    }

    // =========================================================================
    // 1. SEARCH VIA GUDANGVAPE REST API
    // =========================================================================
    suspend fun search(query: String, page: Int = 1): List<AnimeItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$SEARCH_API?s=$encoded&page=$page"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$MOVIE_BASE/")
            .build()

        val items = mutableListOf<AnimeItem>()
        try {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use
                val jsonStr = res.body?.string() ?: return@use
                val obj = gson.fromJson(jsonStr, JsonObject::class.java)
                val dataArr = obj.getAsJsonArray("data") ?: return@use

                for (elem in dataArr) {
                    val itemObj = elem.asJsonObject
                    val slug = itemObj.get("slug")?.asString ?: continue
                    val title = itemObj.get("title")?.asString ?: slug
                    val ratingVal = itemObj.get("rating")?.let {
                        if (it.isJsonPrimitive) it.asString else "0.0"
                    } ?: "0.0"
                    val type = itemObj.get("type")?.asString ?: "movie"
                    val posterRel = itemObj.get("poster")?.asString ?: ""
                    val fullPoster = when {
                        posterRel.startsWith("http") -> posterRel
                        posterRel.isNotEmpty() -> "$POSTER_BASE$posterRel"
                        else -> ""
                    }
                    val baseUrl = if (type == "series") SERIES_BASE else MOVIE_BASE
                    val fullUrl = "$baseUrl/$slug"
                    val id = slug.hashCode()
                    idToUrlMap[id] = fullUrl

                    val ep = itemObj.get("episode")?.asString ?: ""
                    val badge = if (type == "series") {
                        if (ep.isNotEmpty() && ep != "0") "EPS $ep" else "Series"
                    } else {
                        itemObj.get("quality")?.asString ?: itemObj.get("year")?.asString ?: "Movie"
                    }

                    items.add(AnimeItem(id, title, badge, ratingVal, fullPoster, fullUrl))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    // =========================================================================
    // 2. LATEST MOVIES
    // =========================================================================
    suspend fun getLatest(page: Int = 1, forceRefresh: Boolean = false): List<AnimeItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && page == 1 && cachedLatest != null && (now - cachedLatest!!.first) < CACHE_EXPIRY_MS) {
            return@withContext cachedLatest!!.second
        }

        val url = "$MOVIE_BASE/latest/page/$page"
        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)
        val items = mutableListOf<AnimeItem>()

        for (card in doc.select("article")) {
            parseCard(card, MOVIE_BASE)?.let { items.add(it) }
        }

        if (page == 1 && items.isNotEmpty()) {
            cachedLatest = Pair(now, items)
        }
        items
    }

    // =========================================================================
    // 3. FEATURED BANNERS
    // =========================================================================
    suspend fun getFeaturedBanners(forceRefresh: Boolean = false): List<AnimeItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedBanners != null && (now - cachedBanners!!.first) < CACHE_EXPIRY_MS) {
            return@withContext cachedBanners!!.second
        }

        val html = fetchHtml(MOVIE_BASE)
        val doc = Jsoup.parse(html)
        val items = mutableListOf<AnimeItem>()

        for (card in doc.select("div.featured-slider article, div.sliders article, article").take(8)) {
            parseCard(card, MOVIE_BASE)?.let { items.add(it) }
        }

        if (items.isNotEmpty()) {
            cachedBanners = Pair(now, items)
        }
        items
    }

    // =========================================================================
    // 4. RANKINGS (TOP MOVIES & TOP SERIES)
    // =========================================================================
    suspend fun getRankings(forceRefresh: Boolean = false, type: String = "movie"): List<AnimeItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (type == "movie") {
            if (!forceRefresh && cachedRankings != null && (now - cachedRankings!!.first) < CACHE_EXPIRY_MS) {
                return@withContext cachedRankings!!.second
            }
            val url = "$MOVIE_BASE/populer/page/1"
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)
            val items = mutableListOf<AnimeItem>()

            for (card in doc.select("article")) {
                parseCard(card, MOVIE_BASE)?.let { items.add(it) }
            }

            if (items.isNotEmpty()) {
                cachedRankings = Pair(now, items)
            }
            items
        } else {
            // Series Popular / Top Series
            val url = "$SERIES_BASE/top-series-today/page/1"
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)
            val items = mutableListOf<AnimeItem>()

            for (card in doc.select("article")) {
                parseCard(card, SERIES_BASE)?.let { items.add(it) }
            }
            items
        }
    }

    // =========================================================================
    // 5. SERIES CATEGORIES (NONTONDRAMA)
    // =========================================================================
    suspend fun getWeeklySchedule(forceRefresh: Boolean = false): List<WeekdaySchedule> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedSchedule != null && (now - cachedSchedule!!.first) < CACHE_EXPIRY_MS) {
            return@withContext cachedSchedule!!.second
        }

        val categories = listOf(
            Triple(1, "Terbaru", "$SERIES_BASE/latest-series/page/1"),
            Triple(2, "Unggulan", "$SERIES_BASE/top-series-today/page/1"),
            Triple(3, "Ongoing", "$SERIES_BASE/series/ongoing/page/1"),
            Triple(4, "Complete", "$SERIES_BASE/series/complete/page/1"),
            Triple(5, "Asian Series", "$SERIES_BASE/series/asian/page/1"),
            Triple(6, "West Series", "$SERIES_BASE/series/west/page/1"),
            Triple(7, "Drakor", "$SERIES_BASE/maraton-drakor")
        )

        val schedules = mutableListOf<WeekdaySchedule>()
        for ((idx, title, catUrl) in categories) {
            val list = mutableListOf<AnimeItem>()
            try {
                val html = fetchHtml(catUrl)
                val doc = Jsoup.parse(html)
                for (card in doc.select("article").take(20)) {
                    parseCard(card, SERIES_BASE)?.let { list.add(it) }
                }
            } catch (_: Exception) {}
            schedules.add(WeekdaySchedule(idx, title, list))
        }

        if (schedules.isNotEmpty()) {
            cachedSchedule = Pair(now, schedules)
        }
        schedules
    }

    // =========================================================================
    // 6. DETAIL PAGE (MOVIE & SERIES)
    // =========================================================================
    suspend fun getDetails(itemId: Int, forceRefresh: Boolean = false): AnimeDetail = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = detailCache.get(itemId)
            if (cached != null) return@withContext cached
        }

        var pageUrl = idToUrlMap[itemId] ?: ""
        if (pageUrl.isEmpty()) {
            pageUrl = "$MOVIE_BASE/"
        }

        val html = fetchHtml(pageUrl)
        val doc = Jsoup.parse(html)
        val isSeries = pageUrl.contains("nontondrama") || doc.selectFirst("script#season-data") != null

        val title = doc.selectFirst("h1")?.text()?.ifEmpty { null }
            ?: doc.title().substringBefore(" - ").trim()

        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("picture img")?.attr("src")
            ?: ""

        val synopsis = doc.selectFirst("blockquote")?.text()?.ifEmpty { null }
            ?: doc.selectFirst("meta[name=description]")?.attr("content")
            ?: doc.selectFirst("div.content p")?.text()
            ?: ""

        val meta = mutableMapOf<String, String>()
        doc.select("div.content table tr, div.content p").forEach { el ->
            val text = el.text()
            if (text.contains(":")) {
                val k = text.substringBefore(":").trim().lowercase()
                val v = text.substringAfter(":").trim()
                if (k.isNotEmpty() && v.isNotEmpty()) meta[k] = v
            }
        }

        val servers = mutableListOf<ServerGroup>()

        if (isSeries) {
            val seasonScript = doc.selectFirst("script#season-data")?.data()
            if (!seasonScript.isNullOrEmpty()) {
                try {
                    val seasonJson = gson.fromJson(seasonScript, JsonObject::class.java)
                    for (seasonKey in seasonJson.keySet()) {
                        val epArr = seasonJson.getAsJsonArray(seasonKey) ?: continue
                        val episodeList = mutableListOf<EpisodeItem>()

                        for ((idx, epElem) in epArr.withIndex()) {
                            val epObj = epElem.asJsonObject
                            val epSlug = epObj.get("slug")?.asString ?: continue
                            val epNo = epObj.get("episode_no")?.asInt ?: (idx + 1)
                            val epTitle = epObj.get("title")?.asString ?: "Episode $epNo"
                            val epUrl = "$SERIES_BASE/$epSlug"
                            val epId = epSlug.hashCode()
                            idToUrlMap[epId] = epUrl

                            episodeList.add(
                                EpisodeItem(
                                    episode = "Eps $epNo",
                                    id = epId,
                                    sid = seasonKey.toIntOrNull() ?: 1,
                                    nid = epNo,
                                    playUrl = epUrl,
                                    path = epSlug
                                )
                            )
                        }
                        servers.add(ServerGroup("Season $seasonKey", episodeList.size, episodeList))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (servers.isEmpty()) {
            // Movie Single Episode
            val epItem = EpisodeItem(
                episode = "Full Movie",
                id = itemId,
                sid = 1,
                nid = 1,
                playUrl = pageUrl,
                path = pageUrl.substringAfterLast("/")
            )
            servers.add(ServerGroup("Server HD", 1, listOf(epItem)))
        }

        val detail = AnimeDetail(itemId, title, if (isSeries) "Series" else "Movie", poster, synopsis, meta, servers)
        detailCache.put(itemId, detail)
        detail
    }

    // =========================================================================
    // 7. STREAM EXTRACTION (VIDEONODE -> PLAYCDN -> DIRECT HLS)
    // =========================================================================
    suspend fun getStream(animeId: Int, sid: Int = 1, nid: Int = 1): StreamResult = withContext(Dispatchers.IO) {
        val playUrl = idToUrlMap[animeId] ?: ""
        if (playUrl.isEmpty()) {
            throw Exception("URL video tidak ditemukan")
        }

        val htmlPage = fetchHtml(playUrl)
        val doc = Jsoup.parse(htmlPage)
        val vnodeIframe = doc.selectFirst("iframe#main-player")?.attr("src")
            ?: throw Exception("Iframe player tidak ditemukan pada halaman ini")

        // 1. Fetch videonode iframe
        val vnodeHtml = fetchHtml(vnodeIframe, referer = playUrl)
        val vnodeDoc = Jsoup.parse(vnodeHtml)
        val playcdnIframe = vnodeDoc.selectFirst("iframe")?.attr("src")?.replace("&amp;", "&")
            ?: throw Exception("Iframe PlayCDN tidak ditemukan")

        // 2. Fetch playcdn video.php to get token
        val playcdnHtml = fetchHtml(playcdnIframe, referer = vnodeIframe)
        val pattern = Pattern.compile("var\\s+data\\s*=\\s*(\\{.+?\\});")
        val matcher = pattern.matcher(playcdnHtml)
        if (!matcher.find()) {
            throw Exception("Token streaming playcdn tidak ditemukan")
        }

        val dataJson = gson.fromJson(matcher.group(1), JsonObject::class.java)
        val token = dataJson.get("token")?.asString ?: throw Exception("Token video kosong")

        // 3. POST token to verify.php
        val verifyUrl = "https://playcdn.de/verify.php"
        val payload = JsonObject().apply {
            addProperty("token", token)
            addProperty("is_ios", false)
        }.toString()

        val postReq = Request.Builder()
            .url(verifyUrl)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Referer", playcdnIframe)
            .header("Origin", "https://playcdn.de")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        var directM3u8: String? = null
        client.newCall(postReq).execute().use { res ->
            val resBody = res.body?.string() ?: ""
            val resObj = gson.fromJson(resBody, JsonObject::class.java)
            if (resObj.get("status")?.asString == "success") {
                directM3u8 = resObj.get("fileUrl")?.asString
            }
        }

        if (directM3u8.isNullOrEmpty()) {
            throw Exception("Gagal memuat URL HLS PlayCDN")
        }

        StreamResult(
            id = animeId,
            sid = sid,
            nid = nid,
            provider = "playcdn",
            rawUrl = directM3u8!!,
            m3u8Url = directM3u8,
            embedUrl = null,
            qualities = mapOf("Auto" to directM3u8!!),
            linkNext = null,
            linkPre = null
        )
    }
}
