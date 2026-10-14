package com.penonton.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.penonton.data.model.MovieDetail
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
    }

    private val _detail =
        MutableLiveData<MovieDetail?>()

    val detail: LiveData<MovieDetail?> =
        _detail

    private val _isLoading =
        MutableLiveData<Boolean>()

    val isLoading: LiveData<Boolean> =
        _isLoading

    private val _error =
        MutableLiveData<String?>()

    val error: LiveData<String?> =
        _error

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
}