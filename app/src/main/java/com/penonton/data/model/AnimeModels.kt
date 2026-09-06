package com.penonton.data.model

import java.io.Serializable

data class AnimeItem(
    val id: Int,
    val title: String,
    val latestEp: String,
    val rating: String,
    val poster: String,
    val url: String,
    val year: String = ""
) : Serializable

data class AnimeDetail(
    val id: Int,
    val title: String,
    val status: String,
    val poster: String,
    val synopsis: String,
    val meta: Map<String, String>,
    val servers: List<ServerGroup>
) : Serializable

data class ServerGroup(
    val serverName: String,
    val count: Int,
    val episodes: List<EpisodeItem>
) : Serializable

data class EpisodeItem(
    val episode: String,
    val id: Int,
    val sid: Int,
    val nid: Int,
    val playUrl: String,
    val path: String
) : Serializable

data class StreamResult(
    val id: Int,
    val sid: Int,
    val nid: Int,
    val provider: String,
    val rawUrl: String,
    val m3u8Url: String?,
    val embedUrl: String?,
    val qualities: Map<String, String>,
    val linkNext: String?,
    val linkPre: String?
) : Serializable

data class WatchHistoryItem(
    val animeId: Int,
    val title: String,
    val poster: String,
    val episodeName: String,
    val sid: Int,
    val nid: Int,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long
) : Serializable

data class WeekdaySchedule(
    val dayIndex: Int, // 1=Mon, 2=Tue, ..., 7=Sun
    val dayName: String, // Senin, Selasa, etc.
    val animeList: List<AnimeItem>
) : Serializable
