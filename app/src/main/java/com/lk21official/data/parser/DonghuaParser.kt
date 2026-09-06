package com.lk21official.data.parser

import android.util.LruCache
import com.lk21official.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object DonghuaParser {

    private const val BASE_URL = "https://donghuafun.com"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val gson = Gson()

    // ==========================================
    // In-Memory Fast Cache (Instant Page Loads)
    // ==========================================
    private var cachedBanners: Pair<Long, List<AnimeItem>>? = null
    private var cachedLatest: Pair<Long, List<AnimeItem>>? = null
    private var cachedSchedule: Pair<Long, List<WeekdaySchedule>>? = null
    private var cachedRankings: Pair<Long, List<AnimeItem>>? = null
    private val detailCache = LruCache<Int, AnimeDetail>(50)

    private const val CACHE_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes

    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP Error ${response.code}")
            response.body?.string() ?: ""
        }
    }

    private fun parseCard(card: Element): AnimeItem? {
        val linkElem = card.selectFirst("a.public-list-exp") ?: card.selectFirst("a[href*=/vod/detail/id/]")
        val href = linkElem?.attr("href") ?: ""
        val idMatch = Pattern.compile("/id/(\\d+)\\.html").matcher(href)
        if (!idMatch.find()) return null
        val animeId = idMatch.group(1).toInt()

        val title = linkElem?.attr("title")?.takeIf { it.isNotEmpty() }
            ?: card.selectFirst("a.time-title")?.text()
            ?: ""

        val imgElem = card.selectFirst("img")
        var poster = imgElem?.let {
            val dSrc = it.attr("data-src")
            val src = it.attr("src")
            if (dSrc.isNotEmpty()) dSrc else if (!src.startsWith("data:")) src else ""
        } ?: ""
        if (poster.startsWith("/img.php?url=")) {
            poster = poster.substringAfter("/img.php?url=")
        } else if (poster.startsWith("/")) {
            poster = "$BASE_URL$poster"
        }

        val latestEp = card.selectFirst("span.public-list-prb")?.text()?.trim() ?: ""
        val rating = card.selectFirst("span.public-prt")?.text()?.trim() ?: ""

        return AnimeItem(animeId, title, latestEp, rating, poster, "$BASE_URL$href")
    }

    suspend fun search(query: String, page: Int = 1): List<AnimeItem> = withContext(Dispatchers.IO) {
        try {
            val results = Lk21Parser.search(query, page)
            if (results.isNotEmpty()) return@withContext results
        } catch (_: Exception) {}

        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$BASE_URL/index.php/vod/search/page/$page/wd/$encoded.html"
        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)
        val items = mutableListOf<AnimeItem>()

        for (card in doc.select("div.public-list-box")) {
            parseCard(card)?.let { items.add(it) }
        }
        items
    }

    suspend fun getLatest(page: Int = 1, forceRefresh: Boolean = false): List<AnimeItem> = withContext(Dispatchers.IO) {
        try {
            val results = Lk21Parser.getLatest(page, forceRefresh)
            if (results.isNotEmpty()) return@withContext results
        } catch (_: Exception) {}

        val now = System.currentTimeMillis()
        if (!forceRefresh && page == 1 && cachedLatest != null && (now - cachedLatest!!.first) < CACHE_EXPIRY_MS) {
            return@withContext cachedLatest!!.second
        }

        val url = "$BASE_URL/index.php/vod/type/id/20/page/$page.html"
        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)
        val items = mutableListOf<AnimeItem>()

        for (card in doc.select("div.public-list-box")) {
            parseCard(card)?.let { items.add(it) }
        }

        if (page == 1 && items.isNotEmpty()) {
            cachedLatest = Pair(now, items)
        }
        items
    }

    suspend fun getLatestSeries(page: Int = 1, forceRefresh: Boolean = false): List<AnimeItem> = withContext(Dispatchers.IO) {
        try {
            val results = Lk21Parser.getLatestSeries(page, forceRefresh)
            if (results.isNotEmpty()) return@withContext results
        } catch (_: Exception) {}
        emptyList()
    }

    suspend fun getFeaturedBanners(forceRefresh: Boolean = false): List<AnimeItem> = withContext(Dispatchers.IO) {
        try {
            val results = Lk21Parser.getFeaturedBanners(forceRefresh)
            if (results.isNotEmpty()) return@withContext results
        } catch (_: Exception) {}

        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedBanners != null && (now - cachedBanners!!.first) < CACHE_EXPIRY_MS) {
            return@withContext cachedBanners!!.second
        }

        val html = fetchHtml(BASE_URL)
        val doc = Jsoup.parse(html)
        val items = mutableListOf<AnimeItem>()

        val cards = doc.select("div.public-list-box")
        for (card in cards.take(6)) {
            parseCard(card)?.let { items.add(it) }
        }

        if (items.isNotEmpty()) {
            cachedBanners = Pair(now, items)
        }
        items
    }

    suspend fun getFeaturedSeriesBanners(forceRefresh: Boolean = false): List<AnimeItem> = withContext(Dispatchers.IO) {
        try {
            val results = Lk21Parser.getFeaturedSeriesBanners(forceRefresh)
            if (results.isNotEmpty()) return@withContext results
        } catch (_: Exception) {}
        getRankings(forceRefresh, type = "series")
    }

    suspend fun getWeeklySchedule(forceRefresh: Boolean = false): List<WeekdaySchedule> = withContext(Dispatchers.IO) {
        try {
            val results = Lk21Parser.getWeeklySchedule(forceRefresh)
            if (results.isNotEmpty()) return@withContext results
        } catch (_: Exception) {}

        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedSchedule != null && (now - cachedSchedule!!.first) < CACHE_EXPIRY_MS) {
            return@withContext cachedSchedule!!.second
        }

        val url = "$BASE_URL/index.php/label/weekday.html"
        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)

        val dayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val schedules = mutableListOf<WeekdaySchedule>()

        for (i in 1..7) {
            val listElem = doc.selectFirst("#week-list$i")
            val animeList = mutableListOf<AnimeItem>()
            if (listElem != null) {
                for (card in listElem.select("div.public-list-box")) {
                    parseCard(card)?.let { animeList.add(it) }
                }
            }
            schedules.add(WeekdaySchedule(i, dayNames[i - 1], animeList))
        }

        if (schedules.isNotEmpty()) {
            cachedSchedule = Pair(now, schedules)
        }
        schedules
    }

    suspend fun getRankings(forceRefresh: Boolean = false, type: String = "movie"): List<AnimeItem> = withContext(Dispatchers.IO) {
        try {
            val results = Lk21Parser.getRankings(forceRefresh, type)
            if (results.isNotEmpty()) return@withContext results
        } catch (_: Exception) {}

        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedRankings != null && (now - cachedRankings!!.first) < CACHE_EXPIRY_MS) {
            return@withContext cachedRankings!!.second
        }

        val url = "$BASE_URL/index.php/label/rb.html"
        val html = try { fetchHtml(url) } catch (_: Exception) { fetchHtml("$BASE_URL/index.php/vod/type/id/20/page/1.html") }
        val doc = Jsoup.parse(html)
        val items = mutableListOf<AnimeItem>()

        for (card in doc.select("div.public-list-box")) {
            parseCard(card)?.let { items.add(it) }
        }
        val results = if (items.isEmpty()) getLatest(1, true).take(20) else items.take(20)

        if (results.isNotEmpty()) {
            cachedRankings = Pair(now, results)
        }
        results
    }

    fun registerUrl(id: Int, url: String) {
        Lk21Parser.registerUrl(id, url)
    }

    suspend fun getDetails(animeId: Int, url: String? = null, forceRefresh: Boolean = false): AnimeDetail = withContext(Dispatchers.IO) {
        try {
            return@withContext Lk21Parser.getDetails(animeId, url, forceRefresh)
        } catch (_: Exception) {}

        if (!forceRefresh) {
            val cached = detailCache.get(animeId)
            if (cached != null) return@withContext cached
        }

        val pageUrl = url ?: "$BASE_URL/index.php/vod/detail/id/$animeId.html"
        val html = fetchHtml(pageUrl)
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("li:has(em:contains(Title)) span")?.text()
            ?: doc.title().substringBefore(" - Donghua").trim()

        val status = doc.selectFirst("li:has(em:contains(Status)) span")?.text() ?: ""
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val synopsis = doc.selectFirst("li.top26")?.text()?.replace("Introduction:", "")?.trim() ?: ""

        val meta = mutableMapOf<String, String>()
        for (li in doc.select("div.gen-search-form li")) {
            val key = li.selectFirst("em")?.text()?.replace(":", "")?.trim()?.lowercase() ?: continue
            val value = li.selectFirst("span")?.text()?.trim() ?: li.text().substringAfter(":").trim()
            if (key !in listOf("title", "status", "introduction")) {
                meta[key] = value
            }
        }

        val serverNames = mutableListOf<String>()
        for (tab in doc.select("a.swiper-slide")) {
            tab.select("span.badge").remove()
            val name = tab.text().replace("&nbsp;", " ").trim()
            if (name.isNotEmpty()) serverNames.add(name)
        }

        val servers = mutableListOf<ServerGroup>()
        val playlistBoxes = doc.select("ul.anthology-list-play")
        for ((idx, box) in playlistBoxes.withIndex()) {
            val serverName = if (idx < serverNames.size) serverNames[idx] else "Server ${idx + 1}"
            val episodes = mutableListOf<EpisodeItem>()

            for (a in box.select("a[href*=/vod/play/]")) {
                val epText = a.text().trim()
                val href = a.attr("href")
                val epMatcher = Pattern.compile("/id/(\\d+)/sid/(\\d+)/nid/(\\d+)\\.html").matcher(href)
                if (epMatcher.find()) {
                    val vid = epMatcher.group(1).toInt()
                    val sid = epMatcher.group(2).toInt()
                    val nid = epMatcher.group(3).toInt()
                    episodes.add(EpisodeItem(epText, vid, sid, nid, "$BASE_URL$href", href))
                }
            }
            servers.add(ServerGroup(serverName, episodes.size, episodes))
        }

        val sortedServers = servers.sortedWith(
            compareBy<ServerGroup> { s ->
                val lower = s.serverName.lowercase()
                when {
                    (lower.contains("ganjing") || lower.contains("ganjian") || lower.contains("gang")) && (lower.contains("indo") || lower.contains("indonesia")) -> 0
                    lower.contains("ganjing") || lower.contains("ganjian") || lower.contains("gang") -> 1
                    lower.contains("indo") || lower.contains("indonesia") -> 2
                    lower.contains("eng") || lower.contains("english") -> 3
                    else -> 4
                }
            }.thenByDescending { it.count }
        )

        val detail = AnimeDetail(animeId, title, status, poster, synopsis, meta, sortedServers)
        detailCache.put(animeId, detail)
        detail
    }

    suspend fun getStream(animeId: Int, sid: Int = 1, nid: Int = 1, fallbackUrl: String? = null): StreamResult = withContext(Dispatchers.IO) {
        try {
            return@withContext Lk21Parser.getStream(animeId, sid, nid, fallbackUrl)
        } catch (_: Exception) {}

        val url = fallbackUrl ?: "$BASE_URL/index.php/vod/play/id/$animeId/sid/$sid/nid/$nid.html"
        val html = fetchHtml(url)

        val matcher = Pattern.compile("player_aaaa\\s*=\\s*(\\{.+?\\})<", Pattern.DOTALL).matcher(html)
        if (!matcher.find()) {
            throw Exception("Player configuration not found")
        }

        val rawJson = matcher.group(1).trim().removeSuffix(";")
        val jsonObj = gson.fromJson(rawJson, JsonObject::class.java)

        val rawUrl = jsonObj.get("url")?.asString ?: ""
        val provider = jsonObj.get("from")?.asString?.lowercase() ?: ""
        val linkNext = jsonObj.get("link_next")?.asString
        val linkPre = jsonObj.get("link_pre")?.asString

        var m3u8Url: String? = null
        var embedUrl: String? = null
        val qualities = mutableMapOf<String, String>()

        if (rawUrl.startsWith("http") && (rawUrl.contains(".m3u8") || rawUrl.contains(".mp4"))) {
            if (rawUrl.contains("rumble.com/hls-vod/")) {
                embedUrl = rawUrl
            } else {
                m3u8Url = rawUrl
                qualities["auto"] = rawUrl
            }
        } else if (provider.contains("rumble") || provider.contains("rum")) {
            embedUrl = if (rawUrl.startsWith("http")) rawUrl else "https://rumble.com/embed/$rawUrl/"
        } else if (provider.contains("ganjing") || provider.contains("gang")) {
            embedUrl = if (rawUrl.startsWith("http")) rawUrl else "https://www.ganjingworld.com/embed/$rawUrl"
        } else if (provider == "dailymotion" || (rawUrl.length <= 25 && !rawUrl.startsWith("http"))) {
            embedUrl = "https://geo.dailymotion.com/player/x1l1ry.html?video=$rawUrl"
            try {
                val dmApi = "https://www.dailymotion.com/player/metadata/video/$rawUrl"
                val dmReq = Request.Builder()
                    .url(dmApi)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://geo.dailymotion.com/")
                    .build()
                client.newCall(dmReq).execute().use { dmRes ->
                    if (dmRes.isSuccessful) {
                        val dmJson = gson.fromJson(dmRes.body?.string(), JsonObject::class.java)
                        val qObj = dmJson.getAsJsonObject("qualities")
                        if (qObj != null) {
                            for (qKey in qObj.keySet()) {
                                val arr = qObj.getAsJsonArray(qKey)
                                if (arr != null && arr.size() > 0) {
                                    val itemUrl = arr.get(0).asJsonObject.get("url")?.asString
                                    if (itemUrl != null) qualities[qKey] = itemUrl
                                }
                            }
                            m3u8Url = qualities["auto"] ?: qualities.values.firstOrNull()
                        }
                    }
                }
            } catch (_: Exception) {}
        } else if (provider in listOf("1080eng", "1080indo") || provider.contains("1080")) {
            embedUrl = "https://ksrteam.org/index.php?url=$rawUrl"
        } else {
            if (rawUrl.startsWith("http")) embedUrl = rawUrl
        }

        StreamResult(animeId, sid, nid, provider, rawUrl, m3u8Url, embedUrl, qualities, linkNext, linkPre)
    }
}
