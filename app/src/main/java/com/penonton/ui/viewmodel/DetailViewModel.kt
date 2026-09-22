package com.penonton.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.penonton.data.model.MovieDetail
import com.penonton.data.model.TmdbItem
import com.penonton.data.network.ApiClient
import com.penonton.data.parser.Lk21Parser
import com.penonton.data.repository.TranslationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailViewModel(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DetailViewModel"
        private const val TMDB_DEBUG_TAG = "TMDB_DEBUG"
    }

    private val _detail =
        MutableLiveData<MovieDetail?>()

    val detail: LiveData<MovieDetail?> =
        _detail

    private val _tmdbMetadata =
        MutableLiveData<TmdbItem?>()

    val tmdbMetadata: LiveData<TmdbItem?> =
        _tmdbMetadata

    private val _isLoading =
        MutableLiveData<Boolean>()

    val isLoading: LiveData<Boolean> =
        _isLoading

    private val _error =
        MutableLiveData<String?>()

    val error: LiveData<String?> =
        _error

    private val tmdbApiKey =
        "8e9f7ba399e95b2d3b7b8d3ba6bc9de1"

    private val translationRepository =
        TranslationRepository(
            application.applicationContext
        )

    /**
     * Memuat detail film dari LK21.
     *
     * Synopsis akan otomatis:
     *
     * 1. Dibersihkan
     * 2. Dideteksi bahasanya
     * 3. Jika Bahasa Indonesia -> langsung digunakan
     * 4. Jika bahasa lain -> cek cache
     * 5. Jika belum ada cache -> diterjemahkan
     * 6. Hasil translation disimpan ke cache
     * 7. Jika translation gagal -> synopsis asli digunakan
     */
    fun loadDetail(
        movieId: Int,
        url: String? = null
    ) {

        _isLoading.value = true
        _error.value = null

        /*
         * Reset detail lama agar film sebelumnya tidak
         * tampil ketika film baru sedang dimuat.
         */
        _detail.value = null

        viewModelScope.launch {

            try {

                /*
                 * =====================================================
                 * 1. Ambil detail asli dari LK21
                 *
                 * PENTING:
                 * Gunakan positional parameter seperti kode asli.
                 *
                 * Jangan menggunakan:
                 *
                 * getDetails(movieId = ..., url = ...)
                 *
                 * karena signature Lk21Parser kamu tidak menggunakan
                 * nama parameter tersebut.
                 * =====================================================
                 */
                val data =
                    withContext(Dispatchers.IO) {
                        Lk21Parser.getDetails(
                            movieId,
                            url
                        )
                    }

                /*
                 * =====================================================
                 * 2. Proses synopsis
                 * =====================================================
                 */
                val translatedSynopsis =
                    withContext(Dispatchers.IO) {

                        translationRepository
                            .translateSynopsis(
                                movieId,
                                data.synopsis
                            )
                    }

                /*
                 * =====================================================
                 * 3. Buat MovieDetail baru.
                 *
                 * Semua data asli dipertahankan.
                 * Hanya synopsis yang diganti dengan hasil processing.
                 * =====================================================
                 */
                val finalData =
                    data.copy(
                        synopsis =
                            translatedSynopsis
                    )

                /*
                 * =====================================================
                 * 4. Kirim hasil ke Activity
                 * =====================================================
                 */
                _detail.value =
                    finalData

                Log.d(
                    TAG,
                    "Detail berhasil dimuat: movieId=$movieId"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Gagal memuat detail: movieId=$movieId",
                    e
                )

                _error.value =
                    e.message
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "Gagal memuat detail"

            } finally {

                _isLoading.value = false
            }
        }
    }

    /**
     * Mengambil metadata tambahan dari TMDb.
     */
    fun fetchExternalMetadata(
        rawTitle: String
    ) {

        /*
         * Hapus metadata film sebelumnya agar tidak tertukar
         * dengan film yang sedang dibuka.
         */
        _tmdbMetadata.value = null

        viewModelScope.launch(
            Dispatchers.IO
        ) {

            try {

                val cleanTitle =
                    rawTitle
                        .replace(
                            Regex(
                                "(?i)(sub indo|season \\d+|batch|movie|hd|complete|\\[.*?]|\\(\\d{4}\\))"
                            ),
                            ""
                        )
                        .replace(
                            Regex("\\s+"),
                            " "
                        )
                        .trim()

                if (cleanTitle.isBlank()) {

                    Log.d(
                        TMDB_DEBUG_TAG,
                        "Judul kosong, TMDb dilewati"
                    )

                    _tmdbMetadata.postValue(
                        null
                    )

                    return@launch
                }

                val response =
                    ApiClient
                        .tmdbService
                        .searchMedia(
                            tmdbApiKey,
                            cleanTitle
                        )

                /*
                 * Buang hasil berupa person.
                 *
                 * Tetap mempertahankan perilaku kode lama:
                 * hanya menerima judul yang benar-benar cocok.
                 */
                val bestMatch =
                    response.results
                        .filter {
                            it.mediaType != "person"
                        }
                        .firstOrNull { item ->

                            val candidate =
                                (
                                        item.title
                                            ?: item.name
                                            ?: ""
                                        ).trim()

                            candidate.equals(
                                cleanTitle,
                                ignoreCase = true
                            )
                        }

                Log.d(
                    TMDB_DEBUG_TAG,
                    "cleanTitle='$cleanTitle', " +
                            "matched=${
                                bestMatch?.title
                                    ?: bestMatch?.name
                                    ?: "TIDAK ADA"
                            }"
                )

                _tmdbMetadata.postValue(
                    bestMatch
                )

            } catch (e: Exception) {

                Log.e(
                    TMDB_DEBUG_TAG,
                    "TMDb fetch failed",
                    e
                )

                _tmdbMetadata.postValue(
                    null
                )
            }
        }
    }
}