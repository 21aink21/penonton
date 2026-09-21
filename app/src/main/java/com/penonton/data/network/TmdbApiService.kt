package com.penonton.data.network

import com.penonton.data.model.TmdbSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApiService {
    @GET("3/search/multi")
    suspend fun searchMedia(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "id-ID"
    ): TmdbSearchResponse
}