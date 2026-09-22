package com.penonton.data.network

import com.penonton.data.model.TranslationResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TranslationApiService {

    @GET("get")
    suspend fun translate(
        @Query("q") text: String,
        @Query("langpair") langPair: String,
        @Query("mt") machineTranslation: Boolean = true
    ): TranslationResponse
}