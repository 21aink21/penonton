package com.penonton.data.model

import com.google.gson.annotations.SerializedName

data class TranslationResponse(
    @SerializedName("responseData")
    val responseData: TranslationData? = null
)

data class TranslationData(
    @SerializedName("translatedText")
    val translatedText: String? = null,
    @SerializedName("match")
    val match: Double? = null
)