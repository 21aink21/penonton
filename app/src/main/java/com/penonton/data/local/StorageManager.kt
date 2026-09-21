package com.penonton.data.local

import android.content.Context
import android.content.SharedPreferences
import com.penonton.data.model.MovieItem
import com.penonton.data.model.WatchHistoryItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

class StorageManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("penonton_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ==========================================
    // 1. Watch History & Resume Progress
    // ==========================================

    fun saveHistory(
        movieId: Int,
        title: String,
        poster: String,
        episodeName: String,
        sid: Int,
        nid: Int,
        positionMs: Long,
        durationMs: Long,
        playUrl: String = "",
        movieUrl: String = ""
    ) {
        val list = getHistory().toMutableList()
        val oldItem = list.firstOrNull { it.movieId == movieId }
        list.removeAll { it.movieId == movieId }

        val finalDuration = if (durationMs > 0L) {
            durationMs
        } else {
            oldItem?.takeIf { it.durationMs > 0L }?.durationMs
                ?: getEpisodeDuration(movieId, nid).takeIf { it > 0L }
                ?: 0L
        }

        val finalPlayUrl = playUrl.ifEmpty { (oldItem?.playUrl ?: "") }
        val finalMovieUrl = movieUrl.ifEmpty { (oldItem?.movieUrl ?: "") }

        val item = WatchHistoryItem(
            movieId = movieId,
            title = title,
            poster = poster,
            episodeName = episodeName,
            sid = sid,
            nid = nid,
            positionMs = positionMs,
            durationMs = finalDuration,
            timestamp = System.currentTimeMillis(),
            playUrl = finalPlayUrl,
            movieUrl = finalMovieUrl
        )
        list.add(0, item) // Add to top
        if (list.size > 50) list.removeAt(list.size - 1) // Keep latest 50

        prefs.edit {
            putString(KEY_HISTORY, gson.toJson(list))
                .putLong("ep_pos_${movieId}_$nid", positionMs)
                .putLong("ep_dur_${movieId}_$nid", finalDuration)
        }

        // Also mark episode as watched
        markEpisodeWatched(movieId, episodeName)
    }

    fun getEpisodePosition(movieId: Int, nid: Int): Long {
        return prefs.getLong("ep_pos_${movieId}_$nid", 0L)
    }

    fun getEpisodeDuration(movieId: Int, nid: Int): Long {
        return prefs.getLong("ep_dur_${movieId}_$nid", 0L)
    }

    fun getHistory(): List<WatchHistoryItem> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WatchHistoryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getLastWatched(movieId: Int): WatchHistoryItem? {
        return getHistory().firstOrNull { it.movieId == movieId }
    }

    fun clearHistory() {
        prefs.edit { remove(KEY_HISTORY) }
    }

    // ==========================================
    // 2. Watched Episodes Tracker
    // ==========================================

    fun markEpisodeWatched(movieId: Int, episodeName: String) {
        val key = "${KEY_WATCHED_EPS}_$movieId"
        val set = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(episodeName)
        prefs.edit { putStringSet(key, set) }
    }

    fun getWatchedEpisodes(movieId: Int): Set<String> {
        val key = "${KEY_WATCHED_EPS}_$movieId"
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    // ==========================================
    // 3. Favorites / Bookmarks
    // ==========================================

    fun toggleFavorite(anime: MovieItem): Boolean {
        val list = getFavorites().toMutableList()
        val exists = list.any { it.id == anime.id }
        if (exists) {
            list.removeAll { it.id == anime.id }
        } else {
            list.add(0, anime)
        }
        prefs.edit { putString(KEY_FAVORITES, gson.toJson(list)) }
        return !exists // Returns true if added, false if removed
    }

    fun isFavorite(movieId: Int): Boolean {
        return getFavorites().any { it.id == movieId }
    }

    fun getFavorites(): List<MovieItem> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<MovieItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getBubbleSize(): Int {
        return prefs.getInt(KEY_BUBBLE_SIZE, 60)
    }

    fun setBubbleSize(sizeDp: Int) {
        prefs.edit { putInt(KEY_BUBBLE_SIZE, sizeDp) }
    }

    companion object {
        private const val KEY_BUBBLE_SIZE = "key_bubble_size"
        private const val KEY_HISTORY = "key_watch_history"
        private const val KEY_FAVORITES = "key_favorites"
        private const val KEY_WATCHED_EPS = "key_watched_eps"

        @Volatile
        private var instance: StorageManager? = null

        fun getInstance(context: Context): StorageManager {
            return instance ?: synchronized(this) {
                instance ?: StorageManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
