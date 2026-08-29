package com.donghuaz.data.local

import android.content.Context
import android.content.SharedPreferences
import com.donghuaz.data.model.AnimeItem
import com.donghuaz.data.model.WatchHistoryItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StorageManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("donghua_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ==========================================
    // 1. Watch History & Resume Progress
    // ==========================================

    fun saveHistory(
        animeId: Int,
        title: String,
        poster: String,
        episodeName: String,
        sid: Int,
        nid: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        val list = getHistory().toMutableList()
        list.removeAll { it.animeId == animeId }
        val item = WatchHistoryItem(
            animeId,
            title,
            poster,
            episodeName,
            sid,
            nid,
            positionMs,
            durationMs,
            System.currentTimeMillis()
        )
        list.add(0, item) // Add to top
        if (list.size > 50) list.removeAt(list.size - 1) // Keep latest 50

        prefs.edit()
            .putString(KEY_HISTORY, gson.toJson(list))
            .putLong("ep_pos_${animeId}_$nid", positionMs)
            .putLong("ep_dur_${animeId}_$nid", durationMs)
            .apply()

        // Also mark episode as watched
        markEpisodeWatched(animeId, episodeName)
    }

    fun getEpisodePosition(animeId: Int, nid: Int): Long {
        return prefs.getLong("ep_pos_${animeId}_$nid", 0L)
    }

    fun getEpisodeDuration(animeId: Int, nid: Int): Long {
        return prefs.getLong("ep_dur_${animeId}_$nid", 0L)
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

    fun getLastWatched(animeId: Int): WatchHistoryItem? {
        return getHistory().firstOrNull { it.animeId == animeId }
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    // ==========================================
    // 2. Watched Episodes Tracker
    // ==========================================

    fun markEpisodeWatched(animeId: Int, episodeName: String) {
        val key = "${KEY_WATCHED_EPS}_$animeId"
        val set = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(episodeName)
        prefs.edit().putStringSet(key, set).apply()
    }

    fun getWatchedEpisodes(animeId: Int): Set<String> {
        val key = "${KEY_WATCHED_EPS}_$animeId"
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    // ==========================================
    // 3. Favorites / Bookmarks
    // ==========================================

    fun toggleFavorite(anime: AnimeItem): Boolean {
        val list = getFavorites().toMutableList()
        val exists = list.any { it.id == anime.id }
        if (exists) {
            list.removeAll { it.id == anime.id }
        } else {
            list.add(0, anime)
        }
        prefs.edit().putString(KEY_FAVORITES, gson.toJson(list)).apply()
        return !exists // Returns true if added, false if removed
    }

    fun isFavorite(animeId: Int): Boolean {
        return getFavorites().any { it.id == animeId }
    }

    fun getFavorites(): List<AnimeItem> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AnimeItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getBubbleSize(): Int {
        return prefs.getInt(KEY_BUBBLE_SIZE, 60)
    }

    fun setBubbleSize(sizeDp: Int) {
        prefs.edit().putInt(KEY_BUBBLE_SIZE, sizeDp).apply()
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
