package com.donghuaz.data.parser

import android.util.LruCache
import com.donghuaz.data.model.*
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

    suspend fun getFeaturedBanners(forceRefresh: Boolean = false): List<AnimeItem> = withContext(Dispatchers.IO) {
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

    suspend fun getWeeklySchedule(forceRefresh: Boolean = false): List<WeekdaySchedule> = withContext(Dispatchers.IO) {
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

    suspend fun getRankings(forceRefresh: Boolean = false): List<AnimeItem> = withContext(Dispatchers.IO) {
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

    suspend fun getDetails(animeId: Int, forceRefresh: Boolean = false): AnimeDetail = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = detailCache.get(animeId)
            if (cached != null) return@withContext cached
        }

        val url = "$BASE_URL/index.php/vod/detail/id/$animeId.html"
        val html = fetchHtml(url)
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
                    lower.contains("4k indo") || lower.contains("indo 4k") || lower.contains("indorumble") -> 0
                    lower.contains("4k") || lower.contains("vip") -> 1
                    lower.contains("indo") || lower.contains("indonesia") -> 2
                    lower.contains("ganjing") || lower.contains("gang") -> 3
                    lower.contains("eng") || lower.contains("english") -> 4
                    else -> 5
                }
            }.thenByDescending { it.count }
        )

        val detail = AnimeDetail(animeId, title, status, poster, synopsis, meta, sortedServers)
        detailCache.put(animeId, detail)
        detail
    }

    suspend fun extractRumbleStream(rawUrl: String): Triple<String?, Map<String, String>, String?> = withContext(Dispatchers.IO) {
        var m3u8Url: String? = null
        val qualities = mutableMapOf<String, String>()
        var embedUrl: String? = null

        val vid = when {
            rawUrl.startsWith("http") -> {
                val m = Pattern.compile("rumble\\.com/embed/([^/?#]+)").matcher(rawUrl)
                if (m.find()) m.group(1) else ""
            }
            rawUrl.length in 4..20 && !rawUrl.contains("/") -> rawUrl
            else -> ""
        }

        val targetEmbedUrl = if (rawUrl.startsWith("http")) rawUrl else if (vid.isNotEmpty()) "https://rumble.com/embed/$vid/" else ""
        if (targetEmbedUrl.isNotEmpty()) embedUrl = targetEmbedUrl

        if (rawUrl.contains("rumble.com/hls-vod/") || (rawUrl.startsWith("http") && (rawUrl.contains(".m3u8") || rawUrl.contains(".mp4")))) {
            m3u8Url = rawUrl
            qualities["auto"] = rawUrl
            return@withContext Triple(m3u8Url, qualities, rawUrl)
        }

        // Stage 1: Mengurai (Scraping) HTML dari URL Embed Rumble menggunakan Jsoup & membaca tag <script>
        if (targetEmbedUrl.isNotEmpty()) {
            try {
                val htmlReq = Request.Builder()
                    .url(targetEmbedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                client.newCall(htmlReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val htmlContent = res.body?.string() ?: ""
                        val doc = Jsoup.parse(htmlContent)

                        for (script in doc.select("script")) {
                            val scriptData = script.data().ifEmpty { script.html() }
                            if (scriptData.isEmpty()) continue

                            // Pola A: Rumble("play", { ... });
                            val playMatcher = Pattern.compile("Rumble\\s*\\(\\s*[\"']play[\"']\\s*,\\s*(\\{.+?\\})\\s*\\);", Pattern.DOTALL).matcher(scriptData)
                            if (playMatcher.find()) {
                                try {
                                    val playJson = gson.fromJson(playMatcher.group(1), JsonObject::class.java)
                                    parseRumbleManifestJson(playJson, qualities)?.let { m3u8Url = it }
                                } catch (_: Exception) {}
                            }

                            // Pola B: Objek JSON dengan properti "mp4", "hls", atau "tar"
                            if (m3u8Url == null && (scriptData.contains("\"mp4\"") || scriptData.contains("\"hls\"") || scriptData.contains("\"tar\""))) {
                                val jsonMatcher = Pattern.compile("(\\{[^{}]*?\"(?:mp4|hls|tar)\"[^{}]*?\\})", Pattern.DOTALL).matcher(scriptData)
                                while (jsonMatcher.find()) {
                                    try {
                                        val obj = gson.fromJson(jsonMatcher.group(1), JsonObject::class.java)
                                        parseRumbleManifestJson(obj, qualities)?.let { m3u8Url = it }
                                        if (m3u8Url != null) break
                                    } catch (_: Exception) {}
                                }
                            }

                            // Pola C: Ekstraksi URL langsung .m3u8 atau .mp4 di dalam script
                            if (m3u8Url == null) {
                                val urlMatcher = Pattern.compile("https?://[^\\s\"'<>]+\\.(?:m3u8|mp4)[^\\s\"'<>]*").matcher(scriptData)
                                if (urlMatcher.find()) {
                                    val directUrl = urlMatcher.group() ?: ""
                                    if (directUrl.isNotEmpty()) {
                                        m3u8Url = directUrl
                                        qualities["auto"] = directUrl
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Stage 2: Ekstraksi melalui Manifest Video Endpoint Rumble EmbedJS jika belum ditemukan
        if (m3u8Url == null && vid.isNotEmpty()) {
            val jsUrl = "https://rumble.com/embedJS/u3/?request=video&ver=2&v=$vid"
            try {
                val req = Request.Builder()
                    .url(jsUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val jsonObj = gson.fromJson(body, JsonObject::class.java)
                        parseRumbleManifestJson(jsonObj, qualities)?.let { m3u8Url = it }
                    }
                }
            } catch (_: Exception) {}
        }

        if (m3u8Url == null && rawUrl.startsWith("http")) {
            embedUrl = rawUrl
        }

        Triple(m3u8Url, qualities, embedUrl)
    }

    private fun parseRumbleManifestJson(jsonObj: JsonObject, qualities: MutableMap<String, String>): String? {
        var selectedStreamUrl: String? = null

        // 1. Periksa HLS Master Playlist ("u" -> "hls" -> "url")
        val uObj = jsonObj.getAsJsonObject("u") ?: jsonObj
        val hlsObj = uObj.getAsJsonObject("hls")
        val hlsUrl = hlsObj?.get("url")?.asString
        if (!hlsUrl.isNullOrEmpty()) {
            selectedStreamUrl = hlsUrl
            qualities["auto"] = hlsUrl
        }

        // 2. Periksa varian chunklist resolusi ("ua" / "u" -> "tar" -> 2160, 1440, 1080, 720, dst)
        val uaObj = jsonObj.getAsJsonObject("ua")
        val tarObj = uaObj?.getAsJsonObject("tar") ?: uObj.getAsJsonObject("tar")
        if (tarObj != null) {
            for (qKey in listOf("2160", "1440", "1080", "720", "480", "360", "240")) {
                val qData = tarObj.get(qKey)
                val qUrl = when {
                    qData?.isJsonObject == true -> qData.asJsonObject.get("url")?.asString
                    qData?.isJsonPrimitive == true -> qData.asString
                    else -> null
                }
                if (!qUrl.isNullOrEmpty()) {
                    val label = when (qKey) {
                        "2160" -> "4K Ultra HD (2160p)"
                        "1440" -> "2K QHD (1440p)"
                        "1080" -> "Full HD (1080p)"
                        "720" -> "HD (720p)"
                        "480" -> "SD (480p)"
                        "360" -> "Low (360p)"
                        else -> "${qKey}p"
                    }
                    qualities[label] = qUrl
                    if (selectedStreamUrl == null) selectedStreamUrl = qUrl
                }
            }
        }

        // 3. Periksa tautan MP4 langsung ("mp4" -> 1080, 720, dst)
        val mp4Obj = uaObj?.getAsJsonObject("mp4") ?: uObj.getAsJsonObject("mp4")
        if (mp4Obj != null) {
            for (qKey in listOf("1080", "720", "480", "360", "240")) {
                val qData = mp4Obj.get(qKey)
                val qUrl = when {
                    qData?.isJsonObject == true -> qData.asJsonObject.get("url")?.asString
                    qData?.isJsonPrimitive == true -> qData.asString
                    else -> null
                }
                if (!qUrl.isNullOrEmpty()) {
                    qualities["MP4 ${qKey}p"] = qUrl
                    if (selectedStreamUrl == null) selectedStreamUrl = qUrl
                }
            }
        }

        return selectedStreamUrl
    }

    suspend fun getStream(animeId: Int, sid: Int = 1, nid: Int = 1): StreamResult = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/index.php/vod/play/id/$animeId/sid/$sid/nid/$nid.html"
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

        if (provider.contains("rumble") || provider.contains("rum") || provider.contains("4k") || provider.contains("vip") || rawUrl.contains("rumble.com")) {
            val (extractedM3u8, extractedQualities, fallbackEmbed) = extractRumbleStream(rawUrl)
            m3u8Url = extractedM3u8
            qualities.putAll(extractedQualities)
            embedUrl = fallbackEmbed
        } else if (rawUrl.startsWith("http") && (rawUrl.contains(".m3u8") || rawUrl.contains(".mp4"))) {
            m3u8Url = rawUrl
            qualities["auto"] = rawUrl
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
