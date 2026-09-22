package com.penonton.data.repository

import android.content.Context
import android.text.Html
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.penonton.data.cache.TranslationCache
import com.penonton.data.network.ApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.util.Locale

class TranslationRepository(
    context: Context
) {

    companion object {
        private const val TAG = "TranslationRepository"

        private const val INDONESIAN_LANGUAGE = "id"
        private const val UNKNOWN_LANGUAGE = "unknown"

        /**
         * MyMemory memiliki batas query sekitar 500 karakter.
         * Gunakan margin keamanan 450 karakter.
         */
        private const val MAX_CHUNK_LENGTH = 450

        /**
         * Jeda antar request translation.
         */
        private const val REQUEST_DELAY_MS = 250L
    }

    private val appContext =
        context.applicationContext

    private val translationCache =
        TranslationCache(appContext)

    private val languageIdentifier =
        LanguageIdentification.getClient()

    /**
     * Membersihkan synopsis, mendeteksi bahasa,
     * mengambil cache, kemudian menerjemahkan jika diperlukan.
     */
    suspend fun translateSynopsis(
        movieId: Int,
        synopsis: String
    ): String {

        val cleanedSynopsis =
            cleanSynopsis(synopsis)

        if (cleanedSynopsis.isBlank()) {
            return ""
        }

        try {

            /*
             * ====================================================
             * 1. DETEKSI BAHASA
             * ====================================================
             */
            val detectedLanguage =
                detectLanguage(cleanedSynopsis)

            Log.d(
                TAG,
                "movieId=$movieId, detectedLanguage=$detectedLanguage"
            )

            /*
             * ====================================================
             * 2. JIKA SUDAH BAHASA INDONESIA
             * ====================================================
             */
            if (detectedLanguage == INDONESIAN_LANGUAGE) {

                Log.d(
                    TAG,
                    "Synopsis sudah Bahasa Indonesia. " +
                            "Translation dilewati."
                )

                return cleanedSynopsis
            }

            /*
             * ====================================================
             * 3. CEK CACHE
             * ====================================================
             */
            val cachedTranslation =
                translationCache.get(
                    movieId = movieId,
                    sourceText = cleanedSynopsis
                )

            if (!cachedTranslation.isNullOrBlank()) {

                Log.d(
                    TAG,
                    "Translation ditemukan di cache. " +
                            "movieId=$movieId"
                )

                return cachedTranslation
            }

            /*
             * ====================================================
             * 4. TRANSLATE SYNOPSIS
             * ====================================================
             */
            val translated =
                translateLongText(
                    text = cleanedSynopsis,
                    sourceLanguage = detectedLanguage
                )

            if (translated.isNullOrBlank()) {

                Log.w(
                    TAG,
                    "Translation gagal. " +
                            "Synopsis asli digunakan."
                )

                return cleanedSynopsis
            }

            /*
             * ====================================================
             * 5. CLEAN HASIL TRANSLATION
             * ====================================================
             */
            val cleanedTranslation =
                cleanTranslatedText(translated)

            if (cleanedTranslation.isBlank()) {

                Log.w(
                    TAG,
                    "Hasil translation kosong."
                )

                return cleanedSynopsis
            }

            /*
             * ====================================================
             * 6. SIMPAN KE CACHE
             * ====================================================
             */
            translationCache.put(
                movieId = movieId,
                sourceText = cleanedSynopsis,
                translatedText = cleanedTranslation,
                sourceLanguage =
                    detectedLanguage
                        .ifBlank {
                            UNKNOWN_LANGUAGE
                        }
            )

            Log.d(
                TAG,
                "Translation berhasil dan disimpan ke cache. " +
                        "movieId=$movieId"
            )

            return cleanedTranslation

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Gagal memproses translation movieId=$movieId",
                e
            )

            /*
             * Jangan pernah menampilkan pesan error API
             * sebagai synopsis.
             */
            return cleanedSynopsis
        }
    }

    /**
     * ============================================================
     * TRANSLATE LONG TEXT
     * ============================================================
     *
     * Synopsis panjang dipecah menjadi beberapa bagian.
     *
     * Contoh:
     *
     * 1200 karakter
     *      ↓
     * 450 + 450 + 300
     *      ↓
     * diterjemahkan satu per satu
     *      ↓
     * digabung kembali
     */
    private suspend fun translateLongText(
        text: String,
        sourceLanguage: String
    ): String? {

        if (text.isBlank()) {
            return null
        }

        val chunks =
            splitTextIntoChunks(text)

        if (chunks.isEmpty()) {
            return null
        }

        Log.d(
            TAG,
            "Synopsis dipecah menjadi ${chunks.size} chunk"
        )

        val translatedChunks =
            mutableListOf<String>()

        for ((index, chunk) in chunks.withIndex()) {

            Log.d(
                TAG,
                "Translating chunk ${index + 1}/${chunks.size}, " +
                        "length=${chunk.length}"
            )

            val translated =
                requestTranslation(
                    sourceText = chunk,
                    sourceLanguage = sourceLanguage
                )

            if (translated.isNullOrBlank()) {

                Log.w(
                    TAG,
                    "Chunk ${index + 1} gagal diterjemahkan"
                )

                /*
                 * Jika satu chunk gagal,
                 * tetap gunakan teks aslinya.
                 */
                translatedChunks.add(chunk)

            } else {

                translatedChunks.add(
                    cleanTranslatedText(translated)
                )
            }

            /*
             * Jeda antar request.
             *
             * Menggunakan Duration agar sesuai dengan
             * API Kotlin terbaru.
             */
            if (index < chunks.lastIndex) {
                delay(REQUEST_DELAY_MS)
            }
        }

        if (translatedChunks.isEmpty()) {
            return null
        }

        return translatedChunks.joinToString(" ")
    }

    /**
     * ============================================================
     * SPLIT TEXT
     * ============================================================
     *
     * Memecah synopsis berdasarkan batas kalimat
     * agar translation tetap natural.
     */
    private fun splitTextIntoChunks(
        text: String
    ): List<String> {

        if (text.length <= MAX_CHUNK_LENGTH) {
            return listOf(text.trim())
        }

        val chunks =
            mutableListOf<String>()

        var remaining =
            text.trim()

        while (remaining.isNotBlank()) {

            if (remaining.length <= MAX_CHUNK_LENGTH) {

                chunks.add(
                    remaining.trim()
                )

                break
            }

            var splitPosition =
                findBestSplitPosition(
                    text = remaining
                )

            /*
             * Safety guard.
             */
            if (
                splitPosition !in 1..MAX_CHUNK_LENGTH
            ) {
                splitPosition =
                    MAX_CHUNK_LENGTH
            }

            val chunk =
                remaining
                    .substring(
                        0,
                        splitPosition
                    )
                    .trim()

            if (chunk.isNotBlank()) {
                chunks.add(chunk)
            }

            remaining =
                remaining
                    .substring(splitPosition)
                    .trim()
        }

        return chunks
    }

    /**
     * ============================================================
     * FIND BEST SPLIT POSITION
     * ============================================================
     */
    private fun findBestSplitPosition(
        text: String
    ): Int {

        val limit =
            minOf(
                MAX_CHUNK_LENGTH,
                text.length
            )

        val section =
            text.substring(
                0,
                limit
            )

        /*
         * Cari akhir kalimat.
         */
        val sentenceEnd =
            maxOf(
                section.lastIndexOf(". "),
                section.lastIndexOf("? "),
                section.lastIndexOf("! "),
                section.lastIndexOf(".\n"),
                section.lastIndexOf("?\n"),
                section.lastIndexOf("!\n")
            )

        /*
         * Jika posisi pemisah berada di paruh kedua chunk,
         * gunakan sebagai titik pemotongan.
         */
        if (sentenceEnd >= MAX_CHUNK_LENGTH / 2) {
            return sentenceEnd + 1
        }

        /*
         * Cari newline.
         */
        val newline =
            section.lastIndexOf("\n")

        if (newline >= MAX_CHUNK_LENGTH / 2) {
            return newline
        }

        /*
         * Cari koma.
         */
        val comma =
            section.lastIndexOf(", ")

        if (comma >= MAX_CHUNK_LENGTH / 2) {
            return comma + 1
        }

        /*
         * Terakhir, cari spasi.
         */
        val space =
            section.lastIndexOf(" ")

        if (space >= MAX_CHUNK_LENGTH / 2) {
            return space
        }

        /*
         * Jika tidak ditemukan titik pemisah,
         * potong tepat pada batas maksimum.
         */
        return limit
    }

    /**
     * ============================================================
     * CLEAN SYNOPSIS
     * ============================================================
     */
    fun cleanSynopsis(
        input: String
    ): String {

        if (input.isBlank()) {
            return ""
        }

        var text = input

        /*
         * minSdk aplikasi adalah 24,
         * sehingga Html.fromHtml(String, flags) tersedia.
         */
        text =
            Html.fromHtml(
                text,
                Html.FROM_HTML_MODE_LEGACY
            ).toString()

        /*
         * Hapus zero-width characters.
         */
        text =
            text.replace(
                Regex(
                    "[\\u200B-\\u200D\\uFEFF]"
                ),
                ""
            )

        /*
         * Hapus control characters,
         * tetapi pertahankan newline dan tab.
         */
        text =
            text.replace(
                Regex(
                    "[\\p{Cc}&&[^\\r\\n\\t]]"
                ),
                ""
            )

        /*
         * Non-breaking space.
         */
        text =
            text.replace(
                '\u00A0',
                ' '
            )

        /*
         * Rapikan tab dan spasi.
         */
        text =
            text.replace(
                Regex("[\\t ]+"),
                " "
            )

        /*
         * Maksimal dua newline.
         */
        text =
            text.replace(
                Regex("\\n{3,}"),
                "\n\n"
            )

        /*
         * Trim setiap baris.
         */
        text =
            text
                .lines()
                .joinToString("\n") {
                    it.trim()
                }

        return text.trim()
    }

    /**
     * ============================================================
     * LANGUAGE DETECTION
     * ============================================================
     */
    private suspend fun detectLanguage(
        text: String
    ): String {

        if (text.isBlank()) {
            return UNKNOWN_LANGUAGE
        }

        return try {

            val language =
                languageIdentifier
                    .identifyLanguage(text)
                    .await()

            if (
                language.isNullOrBlank() ||
                language == "und"
            ) {

                UNKNOWN_LANGUAGE

            } else {

                language.lowercase(Locale.US)
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Gagal mendeteksi bahasa",
                e
            )

            UNKNOWN_LANGUAGE
        }
    }

    /**
     * ============================================================
     * MYMEMORY TRANSLATION API
     * ============================================================
     */
    private suspend fun requestTranslation(
        sourceText: String,
        sourceLanguage: String
    ): String? {

        if (sourceText.isBlank()) {
            return null
        }

        /*
         * Safety guard.
         *
         * Request tidak boleh lebih dari batas chunk.
         */
        val safeText =
            sourceText
                .take(MAX_CHUNK_LENGTH)
                .trim()

        if (safeText.isBlank()) {
            return null
        }

        return try {

            val languagePair =
                if (
                    sourceLanguage.isBlank() ||
                    sourceLanguage == UNKNOWN_LANGUAGE
                ) {

                    "autodetect|id"

                } else {

                    "$sourceLanguage|id"
                }

            Log.d(
                TAG,
                "Request translation: " +
                        "$languagePair, " +
                        "length=${safeText.length}"
            )

            val response =
                ApiClient
                    .translationService
                    .translate(
                        text = safeText,
                        langPair = languagePair,
                        machineTranslation = true
                    )

            val translatedText =
                response
                    .responseData
                    ?.translatedText
                    ?.trim()

            if (translatedText.isNullOrBlank()) {

                Log.w(
                    TAG,
                    "Translation API tidak " +
                            "mengembalikan hasil."
                )

                return null
            }

            /*
             * Proteksi jika API mengembalikan pesan error
             * sebagai translatedText.
             */
            if (
                translatedText.contains(
                    "QUERY LENGTH LIMIT EXCEEDED",
                    ignoreCase = true
                )
            ) {

                Log.e(
                    TAG,
                    "MyMemory menolak query karena " +
                            "terlalu panjang."
                )

                return null
            }

            translatedText

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Translation API gagal",
                e
            )

            null
        }
    }

    /**
     * ============================================================
     * CLEAN TRANSLATED TEXT
     * ============================================================
     */
    private fun cleanTranslatedText(
        input: String
    ): String {

        var text =
            cleanSynopsis(input)

        /*
         * Hapus marker machine translation.
         */
        text =
            text.replace(
                Regex(
                    "(?i)\\[\\s*MACHINE\\s+TRANSLATION\\s*]"
                ),
                ""
            )

        /*
         * Hapus pesan error API jika sampai lolos.
         */
        text =
            text.replace(
                Regex(
                    "(?i)QUERY LENGTH LIMIT EXCEEDED\\.?\\s*"
                ),
                ""
            )

        /*
         * Rapikan whitespace.
         */
        text =
            text.replace(
                Regex("[ \\t]{2,}"),
                " "
            )

        text =
            text.replace(
                Regex("\\n{3,}"),
                "\n\n"
            )

        return text.trim()
    }
}