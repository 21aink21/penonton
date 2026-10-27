package com.penonton.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.penonton.data.local.db.PenontonDatabaseHelper
import com.penonton.data.model.MovieItem
import com.penonton.data.model.WatchHistoryItem

class StorageManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("penonton_prefs", Context.MODE_PRIVATE)
    private val dbHelper = PenontonDatabaseHelper.getInstance(context)

    init {
        migrateLegacyDataIfNecessary()
    }

    private fun migrateLegacyDataIfNecessary() {
        val migrated = prefs.getBoolean(KEY_DB_MIGRATED, false)
        if (migrated) return

        try {
            val gson = Gson()

            // 1. Migrate Watch History
            val historyJson = prefs.getString(KEY_HISTORY, null)
            if (!historyJson.isNullOrBlank()) {
                val histType = object : TypeToken<List<WatchHistoryItem>>() {}.type
                val legacyHistory: List<WatchHistoryItem>? = gson.fromJson(historyJson, histType)
                legacyHistory?.reversed()?.forEach { item ->
                    dbHelper.saveHistory(
                        movieId = item.movieId,
                        title = item.title,
                        poster = item.poster,
                        episodeName = item.episodeName,
                        sid = item.sid,
                        nid = item.nid,
                        positionMs = item.positionMs,
                        durationMs = item.durationMs,
                        playUrl = item.playUrl,
                        movieUrl = item.movieUrl
                    )
                }
            }

            // 2. Migrate Favorites
            val favJson = prefs.getString(KEY_FAVORITES, null)
            if (!favJson.isNullOrBlank()) {
                val favType = object : TypeToken<List<MovieItem>>() {}.type
                val legacyFavs: List<MovieItem>? = gson.fromJson(favJson, favType)
                legacyFavs?.reversed()?.forEach { movie ->
                    if (!dbHelper.isFavorite(movie.id)) {
                        dbHelper.toggleFavorite(movie)
                    }
                }
            }

            prefs.edit { putBoolean(KEY_DB_MIGRATED, true) }
        } catch (_: Exception) {
            // Non-critical: fail silently if legacy data format is corrupted
        }
    }

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
        val oldDuration = getEpisodeDuration(movieId, nid)
        val finalDuration = if (durationMs > 0L) {
            durationMs
        } else if (oldDuration > 0L) {
            oldDuration
        } else {
            0L
        }

        dbHelper.saveHistory(
            movieId = movieId,
            title = title,
            poster = poster,
            episodeName = episodeName,
            sid = sid,
            nid = nid,
            positionMs = positionMs,
            durationMs = finalDuration,
            playUrl = playUrl,
            movieUrl = movieUrl
        )
    }

    fun getEpisodePosition(movieId: Int, nid: Int): Long {
        return dbHelper.getEpisodePosition(movieId, nid)
    }

    fun getEpisodeDuration(movieId: Int, nid: Int): Long {
        return dbHelper.getEpisodeDuration(movieId, nid)
    }

    fun getHistory(): List<WatchHistoryItem> {
        return dbHelper.getHistory()
    }

    fun getLastWatched(movieId: Int): WatchHistoryItem? {
        return dbHelper.getLastWatched(movieId)
    }

    fun clearHistory() {
        dbHelper.clearHistory()
    }

    // ==========================================
    // 2. Watched Episodes Tracker
    // ==========================================

    fun markEpisodeWatched(movieId: Int, episodeName: String) {
        dbHelper.markEpisodeWatched(movieId, episodeName)
    }

    fun getWatchedEpisodes(movieId: Int): Set<String> {
        return dbHelper.getWatchedEpisodes(movieId)
    }

    // ==========================================
    // 3. Favorites / Bookmarks
    // ==========================================

    fun toggleFavorite(movie: MovieItem): Boolean {
        return dbHelper.toggleFavorite(movie)
    }

    fun isFavorite(movieId: Int): Boolean {
        return dbHelper.isFavorite(movieId)
    }

    fun getFavorites(): List<MovieItem> {
        return dbHelper.getFavorites()
    }

    // ==========================================
    // 4. UI Preferences
    // ==========================================

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
        private const val KEY_DB_MIGRATED = "key_db_migrated_v1"

        @Volatile
        private var instance: StorageManager? = null

        fun getInstance(context: Context): StorageManager {
            return instance ?: synchronized(this) {
                instance ?: StorageManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
