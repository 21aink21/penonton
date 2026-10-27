package com.penonton.data.local.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.penonton.data.model.MovieItem
import com.penonton.data.model.WatchHistoryItem

class PenontonDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "penonton.db"
        const val DATABASE_VERSION = 1

        // Table Watch History
        const val TABLE_HISTORY = "watch_history"
        const val COL_HIST_MOVIE_ID = "movie_id"
        const val COL_HIST_TITLE = "title"
        const val COL_HIST_POSTER = "poster"
        const val COL_HIST_EPISODE_NAME = "episode_name"
        const val COL_HIST_SID = "sid"
        const val COL_HIST_NID = "nid"
        const val COL_HIST_POSITION_MS = "position_ms"
        const val COL_HIST_DURATION_MS = "duration_ms"
        const val COL_HIST_TIMESTAMP = "timestamp"
        const val COL_HIST_PLAY_URL = "play_url"
        const val COL_HIST_MOVIE_URL = "movie_url"

        // Table Favorites
        const val TABLE_FAVORITES = "favorites"
        const val COL_FAV_ID = "id"
        const val COL_FAV_TITLE = "title"
        const val COL_FAV_LATEST_EP = "latest_ep"
        const val COL_FAV_RATING = "rating"
        const val COL_FAV_POSTER = "poster"
        const val COL_FAV_URL = "url"
        const val COL_FAV_YEAR = "year"
        const val COL_FAV_COUNTRY = "country"
        const val COL_FAV_TIMESTAMP = "timestamp"

        // Table Episode Progress
        const val TABLE_EPISODE_PROGRESS = "episode_progress"
        const val COL_PROG_MOVIE_ID = "movie_id"
        const val COL_PROG_NID = "nid"
        const val COL_PROG_POSITION_MS = "position_ms"
        const val COL_PROG_DURATION_MS = "duration_ms"

        // Table Watched Episodes
        const val TABLE_WATCHED_EPISODES = "watched_episodes"
        const val COL_WATCHED_MOVIE_ID = "movie_id"
        const val COL_WATCHED_EPISODE_NAME = "episode_name"

        @Volatile
        private var instance: PenontonDatabaseHelper? = null

        fun getInstance(context: Context): PenontonDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: PenontonDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_HISTORY (
                $COL_HIST_MOVIE_ID INTEGER PRIMARY KEY,
                $COL_HIST_TITLE TEXT NOT NULL,
                $COL_HIST_POSTER TEXT NOT NULL,
                $COL_HIST_EPISODE_NAME TEXT NOT NULL,
                $COL_HIST_SID INTEGER NOT NULL,
                $COL_HIST_NID INTEGER NOT NULL,
                $COL_HIST_POSITION_MS INTEGER NOT NULL,
                $COL_HIST_DURATION_MS INTEGER NOT NULL,
                $COL_HIST_TIMESTAMP INTEGER NOT NULL,
                $COL_HIST_PLAY_URL TEXT NOT NULL DEFAULT '',
                $COL_HIST_MOVIE_URL TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_hist_time ON $TABLE_HISTORY ($COL_HIST_TIMESTAMP DESC)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_FAVORITES (
                $COL_FAV_ID INTEGER PRIMARY KEY,
                $COL_FAV_TITLE TEXT NOT NULL,
                $COL_FAV_LATEST_EP TEXT NOT NULL DEFAULT '',
                $COL_FAV_RATING TEXT NOT NULL DEFAULT '',
                $COL_FAV_POSTER TEXT NOT NULL DEFAULT '',
                $COL_FAV_URL TEXT NOT NULL DEFAULT '',
                $COL_FAV_YEAR TEXT NOT NULL DEFAULT '',
                $COL_FAV_COUNTRY TEXT NOT NULL DEFAULT '',
                $COL_FAV_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_fav_time ON $TABLE_FAVORITES ($COL_FAV_TIMESTAMP DESC)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_EPISODE_PROGRESS (
                $COL_PROG_MOVIE_ID INTEGER NOT NULL,
                $COL_PROG_NID INTEGER NOT NULL,
                $COL_PROG_POSITION_MS INTEGER NOT NULL,
                $COL_PROG_DURATION_MS INTEGER NOT NULL,
                PRIMARY KEY ($COL_PROG_MOVIE_ID, $COL_PROG_NID)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WATCHED_EPISODES (
                $COL_WATCHED_MOVIE_ID INTEGER NOT NULL,
                $COL_WATCHED_EPISODE_NAME TEXT NOT NULL,
                PRIMARY KEY ($COL_WATCHED_MOVIE_ID, $COL_WATCHED_EPISODE_NAME)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle migrations in future versions
    }

    // ==========================================
    // 1. Watch History
    // ==========================================

    @Synchronized
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
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put(COL_HIST_MOVIE_ID, movieId)
                put(COL_HIST_TITLE, title)
                put(COL_HIST_POSTER, poster)
                put(COL_HIST_EPISODE_NAME, episodeName)
                put(COL_HIST_SID, sid)
                put(COL_HIST_NID, nid)
                put(COL_HIST_POSITION_MS, positionMs)
                put(COL_HIST_DURATION_MS, durationMs)
                put(COL_HIST_TIMESTAMP, System.currentTimeMillis())
                put(COL_HIST_PLAY_URL, playUrl)
                put(COL_HIST_MOVIE_URL, movieUrl)
            }
            db.insertWithOnConflict(TABLE_HISTORY, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

            // Save episode progress
            saveEpisodeProgressInternal(db, movieId, nid, positionMs, durationMs)

            // Mark episode watched
            if (episodeName.isNotEmpty()) {
                markEpisodeWatchedInternal(db, movieId, episodeName)
            }

            // Keep max 50 latest history items
            db.execSQL(
                """
                DELETE FROM $TABLE_HISTORY WHERE $COL_HIST_MOVIE_ID NOT IN (
                    SELECT $COL_HIST_MOVIE_ID FROM $TABLE_HISTORY ORDER BY $COL_HIST_TIMESTAMP DESC LIMIT 50
                )
                """.trimIndent()
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getHistory(limit: Int = 50): List<WatchHistoryItem> {
        val list = mutableListOf<WatchHistoryItem>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COL_HIST_TIMESTAMP DESC",
            limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    WatchHistoryItem(
                        movieId = it.getInt(it.getColumnIndexOrThrow(COL_HIST_MOVIE_ID)),
                        title = it.getString(it.getColumnIndexOrThrow(COL_HIST_TITLE)),
                        poster = it.getString(it.getColumnIndexOrThrow(COL_HIST_POSTER)),
                        episodeName = it.getString(it.getColumnIndexOrThrow(COL_HIST_EPISODE_NAME)),
                        sid = it.getInt(it.getColumnIndexOrThrow(COL_HIST_SID)),
                        nid = it.getInt(it.getColumnIndexOrThrow(COL_HIST_NID)),
                        positionMs = it.getLong(it.getColumnIndexOrThrow(COL_HIST_POSITION_MS)),
                        durationMs = it.getLong(it.getColumnIndexOrThrow(COL_HIST_DURATION_MS)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_HIST_TIMESTAMP)),
                        playUrl = it.getString(it.getColumnIndexOrThrow(COL_HIST_PLAY_URL)),
                        movieUrl = it.getString(it.getColumnIndexOrThrow(COL_HIST_MOVIE_URL))
                    )
                )
            }
        }
        return list
    }

    fun getLastWatched(movieId: Int): WatchHistoryItem? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            "$COL_HIST_MOVIE_ID = ?",
            arrayOf(movieId.toString()),
            null,
            null,
            null,
            "1"
        )
        cursor.use {
            if (it.moveToFirst()) {
                return WatchHistoryItem(
                    movieId = it.getInt(it.getColumnIndexOrThrow(COL_HIST_MOVIE_ID)),
                    title = it.getString(it.getColumnIndexOrThrow(COL_HIST_TITLE)),
                    poster = it.getString(it.getColumnIndexOrThrow(COL_HIST_POSTER)),
                    episodeName = it.getString(it.getColumnIndexOrThrow(COL_HIST_EPISODE_NAME)),
                    sid = it.getInt(it.getColumnIndexOrThrow(COL_HIST_SID)),
                    nid = it.getInt(it.getColumnIndexOrThrow(COL_HIST_NID)),
                    positionMs = it.getLong(it.getColumnIndexOrThrow(COL_HIST_POSITION_MS)),
                    durationMs = it.getLong(it.getColumnIndexOrThrow(COL_HIST_DURATION_MS)),
                    timestamp = it.getLong(it.getColumnIndexOrThrow(COL_HIST_TIMESTAMP)),
                    playUrl = it.getString(it.getColumnIndexOrThrow(COL_HIST_PLAY_URL)),
                    movieUrl = it.getString(it.getColumnIndexOrThrow(COL_HIST_MOVIE_URL))
                )
            }
        }
        return null
    }

    @Synchronized
    fun clearHistory() {
        writableDatabase.delete(TABLE_HISTORY, null, null)
    }

    // ==========================================
    // 2. Episode Progress
    // ==========================================

    private fun saveEpisodeProgressInternal(
        db: SQLiteDatabase,
        movieId: Int,
        nid: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        val cv = ContentValues().apply {
            put(COL_PROG_MOVIE_ID, movieId)
            put(COL_PROG_NID, nid)
            put(COL_PROG_POSITION_MS, positionMs)
            put(COL_PROG_DURATION_MS, durationMs)
        }
        db.insertWithOnConflict(TABLE_EPISODE_PROGRESS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getEpisodePosition(movieId: Int, nid: Int): Long {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_EPISODE_PROGRESS,
            arrayOf(COL_PROG_POSITION_MS),
            "$COL_PROG_MOVIE_ID = ? AND $COL_PROG_NID = ?",
            arrayOf(movieId.toString(), nid.toString()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        return 0L
    }

    fun getEpisodeDuration(movieId: Int, nid: Int): Long {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_EPISODE_PROGRESS,
            arrayOf(COL_PROG_DURATION_MS),
            "$COL_PROG_MOVIE_ID = ? AND $COL_PROG_NID = ?",
            arrayOf(movieId.toString(), nid.toString()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        return 0L
    }

    // ==========================================
    // 3. Watched Episodes
    // ==========================================

    private fun markEpisodeWatchedInternal(db: SQLiteDatabase, movieId: Int, episodeName: String) {
        val cv = ContentValues().apply {
            put(COL_WATCHED_MOVIE_ID, movieId)
            put(COL_WATCHED_EPISODE_NAME, episodeName)
        }
        db.insertWithOnConflict(TABLE_WATCHED_EPISODES, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized
    fun markEpisodeWatched(movieId: Int, episodeName: String) {
        markEpisodeWatchedInternal(writableDatabase, movieId, episodeName)
    }

    fun getWatchedEpisodes(movieId: Int): Set<String> {
        val set = mutableSetOf<String>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_WATCHED_EPISODES,
            arrayOf(COL_WATCHED_EPISODE_NAME),
            "$COL_WATCHED_MOVIE_ID = ?",
            arrayOf(movieId.toString()),
            null,
            null,
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                set.add(it.getString(0))
            }
        }
        return set
    }

    // ==========================================
    // 4. Favorites
    // ==========================================

    @Synchronized
    fun toggleFavorite(movie: MovieItem): Boolean {
        val db = writableDatabase
        val exists = isFavorite(movie.id)
        if (exists) {
            db.delete(TABLE_FAVORITES, "$COL_FAV_ID = ?", arrayOf(movie.id.toString()))
            return false
        } else {
            val cv = ContentValues().apply {
                put(COL_FAV_ID, movie.id)
                put(COL_FAV_TITLE, movie.title)
                put(COL_FAV_LATEST_EP, movie.latestEp)
                put(COL_FAV_RATING, movie.rating)
                put(COL_FAV_POSTER, movie.poster)
                put(COL_FAV_URL, movie.url)
                put(COL_FAV_YEAR, movie.year)
                put(COL_FAV_COUNTRY, movie.country)
                put(COL_FAV_TIMESTAMP, System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_FAVORITES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            return true
        }
    }

    fun isFavorite(movieId: Int): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_FAVORITES,
            arrayOf(COL_FAV_ID),
            "$COL_FAV_ID = ?",
            arrayOf(movieId.toString()),
            null,
            null,
            null
        )
        cursor.use {
            return it.count > 0
        }
    }

    fun getFavorites(): List<MovieItem> {
        val list = mutableListOf<MovieItem>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_FAVORITES,
            null,
            null,
            null,
            null,
            null,
            "$COL_FAV_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    MovieItem(
                        id = it.getInt(it.getColumnIndexOrThrow(COL_FAV_ID)),
                        title = it.getString(it.getColumnIndexOrThrow(COL_FAV_TITLE)),
                        latestEp = it.getString(it.getColumnIndexOrThrow(COL_FAV_LATEST_EP)),
                        rating = it.getString(it.getColumnIndexOrThrow(COL_FAV_RATING)),
                        poster = it.getString(it.getColumnIndexOrThrow(COL_FAV_POSTER)),
                        url = it.getString(it.getColumnIndexOrThrow(COL_FAV_URL)),
                        year = it.getString(it.getColumnIndexOrThrow(COL_FAV_YEAR)),
                        country = it.getString(it.getColumnIndexOrThrow(COL_FAV_COUNTRY))
                    )
                )
            }
        }
        return list
    }
}
