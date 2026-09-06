package com.penonton.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.penonton.data.model.MovieDetail
import com.penonton.data.parser.Lk21Parser
import kotlinx.coroutines.launch

class DetailViewModel : ViewModel() {

    private val _detail = MutableLiveData<MovieDetail?>()
    val detail: LiveData<MovieDetail?> = _detail

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

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
}
