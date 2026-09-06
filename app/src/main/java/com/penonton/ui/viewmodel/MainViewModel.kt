package com.penonton.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.penonton.data.model.MovieItem
import com.penonton.data.parser.Lk21Parser
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _animeList = MutableLiveData<List<MovieItem>>()
    val movieList: LiveData<List<MovieItem>> = _animeList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var currentQuery = ""

    fun loadLatest() {
        currentQuery = ""
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val items = Lk21Parser.getLatest(1)
                _animeList.value = items
            } catch (e: Exception) {
                _error.value = e.message ?: "Gagal memuat katalog"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            loadLatest()
            return
        }
        currentQuery = query
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val items = Lk21Parser.search(query, 1)
                _animeList.value = items
            } catch (e: Exception) {
                _error.value = e.message ?: "Gagal melakukan pencarian"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        if (currentQuery.isNotEmpty()) {
            search(currentQuery)
        } else {
            loadLatest()
        }
    }
}
