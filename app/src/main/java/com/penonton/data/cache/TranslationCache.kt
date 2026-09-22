package com.penonton.data.cache

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest

/**
 * Cache lokal untuk hasil terjemahan synopsis.
 *
 * Key cache dibuat berdasarkan:
 *   movieId + hash(synopsis asli)
 *
 * Keuntungannya:
 * - Film yang sama tidak perlu diterjemahkan berulang kali.
 * - Jika synopsis dari sumber berubah, cache lama otomatis tidak digunakan.
 * - Cache tetap tersimpan setelah aplikasi ditutup.
 * - Tidak membutuhkan database tambahan.
 */
class TranslationCache(context: Context) {

    companion object {
        private const val TAG = "TranslationCache"

        private const val PREF_NAME =
            "translation_cache"

        private const val CACHE_KEY =
            "translations"

        private const val MAX_CACHE_ITEMS =
            500
    }

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

    private val gson =
        Gson()

    /**
     * Struktur data internal cache.
     */
    private data class CacheEntry(
        val movieId: Int,
        val sourceText: String,
        val translatedText: String,
        val sourceLanguage: String,
        val timestamp: Long
    )

    /**
     * Mengambil seluruh cache dari SharedPreferences.
     */
    private fun loadCache(): MutableList<CacheEntry> {

        return try {

            val json =
                preferences.getString(
                    CACHE_KEY,
                    null
                )

            if (json.isNullOrBlank()) {
                mutableListOf()
            } else {

                val type =
                    object : TypeToken<List<CacheEntry>>() {}.type

                val entries: List<CacheEntry> =
                    gson.fromJson(
                        json,
                        type
                    )

                entries.toMutableList()
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Gagal membaca translation cache",
                e
            )

            mutableListOf()
        }
    }

    /**
     * Menyimpan seluruh cache ke SharedPreferences.
     */
    private fun saveCache(
        entries: List<CacheEntry>
    ) {

        try {

            preferences
                .edit()
                .putString(
                    CACHE_KEY,
                    gson.toJson(entries)
                )
                .apply()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Gagal menyimpan translation cache",
                e
            )
        }
    }

    /**
     * Menghasilkan hash SHA-256 dari synopsis asli.
     *
     * Hash digunakan supaya apabila synopsis berubah,
     * hasil translation lama tidak digunakan.
     */
    private fun createTextHash(
        text: String
    ): String {

        return try {

            val digest =
                MessageDigest.getInstance(
                    "SHA-256"
                )

            val bytes =
                digest.digest(
                    text
                        .trim()
                        .toByteArray(Charsets.UTF_8)
                )

            bytes.joinToString("") {
                "%02x".format(it)
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Gagal membuat hash synopsis",
                e
            )

            /*
             * Fallback.
             *
             * SHA-256 hampir selalu tersedia di Android,
             * tetapi jika gagal kita tetap menghasilkan key
             * yang relatif stabil.
             */
            text
                .trim()
                .hashCode()
                .toString()
        }
    }

    /**
     * Membuat key unik berdasarkan movieId dan synopsis.
     */
    private fun createCacheKey(
        movieId: Int,
        sourceText: String
    ): String {

        return "$movieId:${createTextHash(sourceText)}"
    }

    /**
     * Mengambil hasil translation dari cache.
     *
     * Return:
     * - String hasil translation jika tersedia.
     * - null jika belum ada.
     */
    fun get(
        movieId: Int,
        sourceText: String
    ): String? {

        val cleanSource =
            sourceText.trim()

        if (movieId <= 0 || cleanSource.isBlank()) {
            return null
        }

        val targetKey =
            createCacheKey(
                movieId,
                cleanSource
            )

        val entries =
            loadCache()

        val entry =
            entries.firstOrNull {
                createCacheKey(
                    it.movieId,
                    it.sourceText
                ) == targetKey
            }

        if (entry == null) {

            Log.d(
                TAG,
                "CACHE MISS: movieId=$movieId"
            )

            return null
        }

        Log.d(
            TAG,
            "CACHE HIT: movieId=$movieId, " +
                    "language=${entry.sourceLanguage}"
        )

        return entry.translatedText
            .trim()
            .takeIf {
                it.isNotBlank()
            }
    }

    /**
     * Menyimpan hasil translation ke cache.
     */
    fun put(
        movieId: Int,
        sourceText: String,
        translatedText: String,
        sourceLanguage: String
    ) {

        val cleanSource =
            sourceText.trim()

        val cleanTranslation =
            translatedText.trim()

        val cleanLanguage =
            sourceLanguage
                .trim()
                .lowercase()
                .ifBlank {
                    "unknown"
                }

        if (movieId <= 0) {
            Log.w(
                TAG,
                "Cache diabaikan: movieId tidak valid"
            )
            return
        }

        if (cleanSource.isBlank()) {
            Log.w(
                TAG,
                "Cache diabaikan: source synopsis kosong"
            )
            return
        }

        if (cleanTranslation.isBlank()) {
            Log.w(
                TAG,
                "Cache diabaikan: hasil translation kosong"
            )
            return
        }

        val newEntry =
            CacheEntry(
                movieId = movieId,
                sourceText = cleanSource,
                translatedText = cleanTranslation,
                sourceLanguage = cleanLanguage,
                timestamp = System.currentTimeMillis()
            )

        val entries =
            loadCache()

        /*
         * Hapus entry lama dengan key yang sama.
         */
        val targetKey =
            createCacheKey(
                movieId,
                cleanSource
            )

        entries.removeAll {
            createCacheKey(
                it.movieId,
                it.sourceText
            ) == targetKey
        }

        /*
         * Entry terbaru ditempatkan di depan.
         */
        entries.add(
            0,
            newEntry
        )

        /*
         * Batasi ukuran cache agar SharedPreferences
         * tidak terus membesar.
         */
        val limitedEntries =
            if (entries.size > MAX_CACHE_ITEMS) {
                entries
                    .take(MAX_CACHE_ITEMS)
            } else {
                entries
            }

        saveCache(
            limitedEntries
        )

        Log.d(
            TAG,
            "CACHE SAVED: movieId=$movieId, " +
                    "language=$cleanLanguage"
        )
    }

    /**
     * Mengecek apakah translation tersedia di cache.
     */
    fun contains(
        movieId: Int,
        sourceText: String
    ): Boolean {

        return get(
            movieId,
            sourceText
        ) != null
    }

    /**
     * Menghapus cache untuk satu film dan synopsis tertentu.
     */
    fun remove(
        movieId: Int,
        sourceText: String
    ) {

        val cleanSource =
            sourceText.trim()

        if (movieId <= 0 || cleanSource.isBlank()) {
            return
        }

        val targetKey =
            createCacheKey(
                movieId,
                cleanSource
            )

        val entries =
            loadCache()

        val removed =
            entries.removeAll {
                createCacheKey(
                    it.movieId,
                    it.sourceText
                ) == targetKey
            }

        if (removed) {

            saveCache(
                entries
            )

            Log.d(
                TAG,
                "CACHE REMOVED: movieId=$movieId"
            )
        }
    }

    /**
     * Menghapus seluruh translation cache.
     */
    fun clear() {

        try {

            preferences
                .edit()
                .remove(CACHE_KEY)
                .apply()

            Log.d(
                TAG,
                "SEMUA TRANSLATION CACHE DIHAPUS"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Gagal menghapus translation cache",
                e
            )
        }
    }

    /**
     * Jumlah entry yang tersimpan di cache.
     *
     * Berguna untuk debugging.
     */
    fun size(): Int {

        return loadCache().size
    }
}