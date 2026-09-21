package com.penonton.data.model

import com.google.gson.annotations.SerializedName

data class TmdbSearchResponse(
    @SerializedName("results") val results: List<TmdbItem>
)

data class TmdbItem(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("overview") val overview: String,
    @SerializedName("vote_average") val voteAverage: Double,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?
)