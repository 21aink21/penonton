package com.penonton.data.parser

import android.util.LruCache
import com.penonton.data.model.*
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
    private var cachedBanners: Pair<Long, List<MovieItem>>? = null
    private var cachedLatest: Pair<Long, List<MovieItem>>? = null
    private var cachedSchedule: Pair<Long, List<WeekdaySchedule>>? = null
    private var cachedRankings: Pair<Long, List<MovieItem>>? = null
    private val detailCache = LruCache<Int, MovieDetail>(50)
    private val idToUrlMap = HashMap<Int, String>()
    private val idToCountryMap = HashMap<Int, String>()

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

    private fun parseCard(article: Element, baseUrl: String, explicitCountry: String = ""): MovieItem? {
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

        var year = article.selectFirst("span.year")?.text()?.trim() ?: ""
        if (year.isEmpty()) {
            val ym = Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b").matcher("$slug $title")
            if (ym.find()) year = ym.group(1) ?: ""
        }
        val eps = article.selectFirst("span.episode strong")?.text() ?: ""
        val quality = article.selectFirst("span.label")?.text() ?: ""

        val badge = when {
            eps.isNotEmpty() -> "EPS $eps"
            quality.isNotEmpty() -> quality
            year.isNotEmpty() -> year
            else -> "Movie"
        }

        val country = when {
            explicitCountry.isNotEmpty() -> {
                idToCountryMap[id] = explicitCountry
                explicitCountry
            }
            idToCountryMap.containsKey(id) -> idToCountryMap[id] ?: ""
            else -> {
                val found = article.selectFirst("a[href*=/country/]")?.text() ?: ""
                if (found.isNotEmpty()) {
                    idToCountryMap[id] = found
                    found
                } else ""
            }
        }

        var poster = article.selectFirst("picture img")?.let {
            val dSrc = it.attr("data-src")
            val src = it.attr("src")
            if (dSrc.isNotEmpty()) dSrc else src
        } ?: article.selectFirst("img")?.attr("src") ?: ""

        if (poster.startsWith("/")) {
            poster = "$POSTER_BASE${poster.removePrefix("/")}"
        }

        return MovieItem(id, title, badge, rating, poster, fullUrl, year, country)
    }

    // =========================================================================
    // 1. SEARCH VIA GUDANGVAPE REST API
    // =========================================================================
    suspend fun search(query: String, page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$SEARCH_API?s=$encoded&page=$page"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$MOVIE_BASE/")
            .build()

        val items = mutableListOf<MovieItem>()
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

                    var yearVal = itemObj.get("year")?.asString ?: ""
                    if (yearVal.isEmpty()) {
                        val ym = Pattern.compile("\\b(19\\d\\d|20\\d\\d)\\b").matcher("$slug $title")
                        if (ym.find()) yearVal = ym.group(1) ?: ""
                    }

                    val ep = itemObj.get("episode")?.asString ?: ""
                    val badge = if (type == "series") {
                        if (ep.isNotEmpty() && ep != "0") "EPS $ep" else "Series"
                    } else {
                        itemObj.get("quality")?.asString ?: if (yearVal.isNotEmpty()) yearVal else "Movie"
                    }

                    val country = idToCountryMap[id] ?: ""
                    items.add(MovieItem(id, title, badge, ratingVal, fullPoster, fullUrl, yearVal, country))
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
    suspend fun getLatest(page: Int = 1, forceRefresh: Boolean = false): List<MovieItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && page == 1 && cachedLatest != null && (now - cachedLatest!!.first) < CACHE_EXPIRY_MS) {
            return@withContext cachedLatest!!.second
        }

        val url = "$MOVIE_BASE/latest/page/$page"
        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MovieItem>()

        for (card in doc.select("article")) {
            parseCard(card, MOVIE_BASE)?.let { items.add(it) }
        }

        if (page == 1 && items.isNotEmpty()) {
            cachedLatest = Pair(now, items)
        }
        items
    }

    // =========================================================================
    // 2.1 LATEST SERIES (NONTONDRAMA)
    // =========================================================================
    private var cachedLatestSeries: Pair<Long, List<MovieItem>>? = null

    suspend fun getLatestSeries(page: Int = 1, forceRefresh: Boolean = false): List<MovieItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && page == 1 && cachedLatestSeries != null && (now - cachedLatestSeries!!.first) < CACHE_EXPIRY_MS) {
            return@withContext cachedLatestSeries!!.second
        }

        val url = "$SERIES_BASE/latest-series/page/$page"
        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MovieItem>()

        for (card in doc.select("article")) {
            parseCard(card, SERIES_BASE)?.let { items.add(it) }
        }

        if (page == 1 && items.isNotEmpty()) {
            cachedLatestSeries = Pair(now, items)
        }
        items
    }

    // =========================================================================
    // 3. FEATURED BANNERS (MOVIES & SERIES)
    // =========================================================================
    suspend fun getFeaturedBanners(forceRefresh: Boolean = false): List<MovieItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedBanners != null && (now - cachedBanners!!.first) < CACHE_EXPIRY_MS) {
            return@withContext cachedBanners!!.second
        }

        val html = fetchHtml(MOVIE_BASE)
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MovieItem>()

        for (card in doc.select("div.featured-slider article, div.sliders article, article").take(8)) {
            parseCard(card, MOVIE_BASE)?.let { items.add(it) }
        }

        if (items.isNotEmpty()) {
            cachedBanners = Pair(now, items)
        }
        items
    }

    private var cachedSeriesBanners: Pair<Long, List<MovieItem>>? = null

    suspend fun getFeaturedSeriesBanners(forceRefresh: Boolean = false): List<MovieItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedSeriesBanners != null && (now - cachedSeriesBanners!!.first) < CACHE_EXPIRY_MS) {
            return@withContext cachedSeriesBanners!!.second
        }

        val url = "$SERIES_BASE/top-series-today/page/1"
        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MovieItem>()

        for (card in doc.select("div.featured-slider article, div.sliders article, article").take(8)) {
            parseCard(card, SERIES_BASE)?.let { items.add(it) }
        }

        if (items.isNotEmpty()) {
            cachedSeriesBanners = Pair(now, items)
        }
        items
    }

    // =========================================================================
    // 4. RANKINGS (TOP MOVIES & TOP SERIES)
    // =========================================================================
    suspend fun getRankings(forceRefresh: Boolean = false, type: String = "movie"): List<MovieItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (type == "movie") {
            if (!forceRefresh && cachedRankings != null && (now - cachedRankings!!.first) < CACHE_EXPIRY_MS) {
                return@withContext cachedRankings!!.second
            }
            val url = "$MOVIE_BASE/populer/page/1"
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)
            val items = mutableListOf<MovieItem>()

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
            val items = mutableListOf<MovieItem>()

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
            val list = mutableListOf<MovieItem>()
            val defaultCountry = when (idx) {
                5 -> "Asian"
                6 -> "United States"
                7 -> "South Korea"
                else -> ""
            }
            try {
                val html = fetchHtml(catUrl)
                val doc = Jsoup.parse(html)
                for (card in doc.select("article").take(20)) {
                    parseCard(card, SERIES_BASE, defaultCountry)?.let { list.add(it) }
                }
            } catch (_: Exception) {}
            schedules.add(WeekdaySchedule(idx, title, list))
        }

        if (schedules.isNotEmpty()) {
            cachedSchedule = Pair(now, schedules)
        }
        schedules
    }

    fun registerUrl(id: Int, url: String) {
        if (url.isNotEmpty()) {
            idToUrlMap[id] = url
        }
    }

    // =========================================================================
    // 6. DETAIL PAGE (MOVIE & SERIES)
    // =========================================================================
    suspend fun getDetails(itemId: Int, fallbackUrl: String? = null, forceRefresh: Boolean = false): MovieDetail = withContext(Dispatchers.IO) {
        if (!fallbackUrl.isNullOrEmpty()) {
            idToUrlMap[itemId] = fallbackUrl
        }
        if (!forceRefresh) {
            val cached = detailCache.get(itemId)
            if (cached != null) return@withContext cached
        }

        var pageUrl = fallbackUrl?.takeIf { it.isNotEmpty() } ?: idToUrlMap[itemId] ?: ""
        if (pageUrl.isEmpty()) {
            pageUrl = "$MOVIE_BASE/"
        }
        if (pageUrl.startsWith("/")) {
            pageUrl = if (pageUrl.contains("series") || pageUrl.contains("season") || pageUrl.contains("episode")) "$SERIES_BASE$pageUrl" else "$MOVIE_BASE$pageUrl"
        }

        val html = fetchHtml(pageUrl)
        val doc = Jsoup.parse(html)
        val isSeries = pageUrl.contains("nontondrama") || doc.selectFirst("script#season-data") != null || doc.selectFirst("a[href*=-episode-]") != null

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

        val countryFromLinks = doc.select("div.content a[href*=/country/]").firstOrNull()?.text()
            ?: doc.select("a[href*=/country/]").lastOrNull()?.text()
            ?: meta["negara"] ?: meta["country"] ?: ""

        if (countryFromLinks.isNotEmpty()) {
            meta["country"] = countryFromLinks
            meta["negara"] = countryFromLinks
            idToCountryMap[itemId] = countryFromLinks
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
                            val epUrl = if (epSlug.startsWith("http")) epSlug else "$SERIES_BASE/$epSlug"
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
                        if (episodeList.isNotEmpty()) {
                            servers.add(ServerGroup("Season $seasonKey", episodeList.size, episodeList))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fallback: parse direct episode links in page if season-data JSON was empty
            if (servers.isEmpty()) {
                val epLinks = doc.select("a[href*=-episode-], a[href*=-season-]")
                val episodeList = mutableListOf<EpisodeItem>()
                val seenUrls = HashSet<String>()

                for ((idx, a) in epLinks.withIndex()) {
                    val href = a.attr("href")
                    if (href.isEmpty() || href == "#" || seenUrls.contains(href)) continue
                    seenUrls.add(href)

                    val epUrl = if (href.startsWith("http")) href else "$SERIES_BASE/$href"
                    val epSlug = href.removePrefix("/")
                    val epId = epSlug.hashCode()
                    idToUrlMap[epId] = epUrl

                    val numMatcher = Pattern.compile("episode-(\\d+)", Pattern.CASE_INSENSITIVE).matcher(href)
                    val epNo = if (numMatcher.find()) numMatcher.group(1)?.toIntOrNull() ?: (idx + 1) else (idx + 1)

                    episodeList.add(
                        EpisodeItem(
                            episode = "Eps $epNo",
                            id = epId,
                            sid = 1,
                            nid = epNo,
                            playUrl = epUrl,
                            path = epSlug
                        )
                    )
                }

                if (episodeList.isNotEmpty()) {
                    servers.add(ServerGroup("Season 1", episodeList.size, episodeList.sortedBy { it.nid }))
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
            idToUrlMap[itemId] = pageUrl
        }

        val detail = MovieDetail(itemId, title, if (isSeries) "Series" else "Movie", poster, synopsis, meta, servers)
        detailCache.put(itemId, detail)
        detail
    }

    // =========================================================================
    // 7. STREAM EXTRACTION (VIDEONODE -> PLAYCDN -> DIRECT HLS)
    // =========================================================================
    suspend fun getStream(movieId: Int, sid: Int = 1, nid: Int = 1, fallbackUrl: String? = null): StreamResult = withContext(Dispatchers.IO) {
        var playUrl = fallbackUrl?.takeIf { it.isNotEmpty() } ?: idToUrlMap[movieId] ?: ""
        if (playUrl.isEmpty()) {
            val cachedDetail = detailCache.get(movieId)
            if (cachedDetail != null) {
                val ep = cachedDetail.servers.flatMap { it.episodes }.firstOrNull { it.nid == nid && it.sid == sid }
                    ?: cachedDetail.servers.firstOrNull()?.episodes?.firstOrNull()
                if (ep != null && ep.playUrl.isNotEmpty()) {
                    playUrl = ep.playUrl
                }
            }
        }
        if (playUrl.isEmpty()) {
            throw Exception("URL video tidak ditemukan")
        }
        if (playUrl.startsWith("/")) {
            playUrl = if (playUrl.contains("episode") || playUrl.contains("season")) "$SERIES_BASE$playUrl" else "$MOVIE_BASE$playUrl"
        }

        val htmlPage = fetchHtml(playUrl)
        val doc = Jsoup.parse(htmlPage)
        var vnodeIframe = doc.selectFirst("iframe#main-player")?.attr("src")
            ?: doc.selectFirst("iframe[src*=videonode]")?.attr("src")
            ?: doc.selectFirst("iframe[src*=playcdn]")?.attr("src")
            ?: doc.selectFirst("iframe[src*=/iframe/]")?.attr("src")
            ?: doc.selectFirst("iframe[src*=/iframe3/]")?.attr("src")

        if (vnodeIframe.isNullOrEmpty()) {
            val iframeM = Pattern.compile("<iframe[^>]+src=[\"']([^\"']*(?:videonode|playcdn|player|embed)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(htmlPage)
            if (iframeM.find()) {
                vnodeIframe = iframeM.group(1)
            }
        }

        if (vnodeIframe.isNullOrEmpty()) {
            throw Exception("Iframe player video tidak ditemukan pada halaman ini")
        }

        if (vnodeIframe.startsWith("/")) {
            vnodeIframe = "https://videonode.de$vnodeIframe"
        }

        // 1. Fetch videonode iframe
        val vnodeHtml = fetchHtml(vnodeIframe, referer = playUrl)
        val vnodeDoc = Jsoup.parse(vnodeHtml)
        var playcdnIframe = vnodeDoc.selectFirst("iframe")?.attr("src")?.replace("&amp;", "&")

        if (playcdnIframe.isNullOrEmpty()) {
            val playM = Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(vnodeHtml)
            if (playM.find()) {
                playcdnIframe = playM.group(1)?.replace("&amp;", "&")
            }
        }

        if (playcdnIframe.isNullOrEmpty()) {
            throw Exception("Iframe PlayCDN tidak ditemukan")
        }

        if (playcdnIframe.startsWith("/")) {
            playcdnIframe = "https://playcdn.de$playcdnIframe"
        }

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
            id = movieId,
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
