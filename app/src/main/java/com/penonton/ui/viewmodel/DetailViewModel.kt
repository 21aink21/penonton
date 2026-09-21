package com.penonton.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.penonton.data.model.MovieDetail
import com.penonton.data.model.TmdbItem
import com.penonton.data.network.ApiClient
import com.penonton.data.parser.Lk21Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DetailViewModel : ViewModel() {

    private val _detail = MutableLiveData<MovieDetail?>()
    val detail: LiveData<MovieDetail?> = _detail

    private val _tmdbMetadata = MutableLiveData<TmdbItem?>()
    val tmdbMetadata: LiveData<TmdbItem?> = _tmdbMetadata

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Ganti string ini dengan TMDb API Key Anda
    private val tmdbApiKey = "8e9f7ba399e95b2d3b7b8d3ba6bc9de1"

    fun loadDetail(movieId: Int, url: String? = null) {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val data = Lk21Parser.getDetails(movieId, url)
                _detail.value = data
            } catch (e: Exception) {
                _error.value = e.message ?: "Gagal memuat detail"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchExternalMetadata(rawTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Bersihkan judul dari kata kunci tambahan agar hasil pencarian akurat
                val cleanTitle = rawTitle.replace(Regex("(?i)(Sub Indo|Season \\d+|Batch|Movie|HD)"), "").trim()

                val response = ApiClient.tmdbService.searchMedia(tmdbApiKey, cleanTitle)
                val firstResult = response.results.firstOrNull()
                _tmdbMetadata.postValue(firstResult)
            } catch (e: Exception) {
                e.printStackTrace()
                _tmdbMetadata.postValue(null)
            }
        }
    }
}