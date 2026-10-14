package com.penonton.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val TRANSLATION_BASE_URL = "https://api.mymemory.translated.net/"

    val translationService: TranslationApiService by lazy {
        Retrofit.Builder()
            .baseUrl(TRANSLATION_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TranslationApiService::class.java)
    }
}