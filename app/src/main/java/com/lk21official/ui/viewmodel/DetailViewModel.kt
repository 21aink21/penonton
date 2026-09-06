package com.lk21official.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lk21official.data.model.AnimeDetail
import com.lk21official.data.parser.DonghuaParser
import kotlinx.coroutines.launch

class DetailViewModel : ViewModel() {

    private val _detail = MutableLiveData<AnimeDetail?>()
    val detail: LiveData<AnimeDetail?> = _detail

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadDetail(animeId: Int) {
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val data = DonghuaParser.getDetails(animeId)
                _detail.value = data
            } catch (e: Exception) {
                _error.value = e.message ?: "Gagal memuat detail"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
