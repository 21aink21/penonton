package com.penonton.util

import com.penonton.data.model.MovieItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryUtilsTest {

    @Test
    fun testFormatCountry_exactMatches() {
        assertEquals("🇰🇷 KR", CountryUtils.formatCountry("korea"))
        assertEquals("🇰🇷 KR", CountryUtils.formatCountry("south korea"))
        assertEquals("🇯🇵 JP", CountryUtils.formatCountry("japan"))
        assertEquals("🇯🇵 JP", CountryUtils.formatCountry("jepang"))
        assertEquals("🇮🇩 ID", CountryUtils.formatCountry("indonesia"))
        assertEquals("🇺🇸 US", CountryUtils.formatCountry("usa"))
        assertEquals("🇺🇸 US", CountryUtils.formatCountry("united states"))
        assertEquals("🇨🇳 CN", CountryUtils.formatCountry("china"))
        assertEquals("🇹🇭 TH", CountryUtils.formatCountry("thailand"))
        assertEquals("🇬🇧 UK", CountryUtils.formatCountry("united kingdom"))
    }

    @Test
    fun testFormatCountry_substringMatches() {
        assertEquals("🇰🇷 KR", CountryUtils.formatCountry("South Korea Drama"))
        assertEquals("🇺🇸 US", CountryUtils.formatCountry("Western Hollywood"))
        assertEquals("🇮🇳 IN", CountryUtils.formatCountry("Bollywood Cinema"))
    }

    @Test
    fun testFormatCountry_emptyAndUnknown() {
        assertEquals("", CountryUtils.formatCountry(""))
        assertEquals("XYZ", CountryUtils.formatCountry("xyz"))
    }

    @Test
    fun testGetCountryBadge_withExplicitCountry() {
        val item = MovieItem(
            id = 1,
            title = "Sample Movie",
            latestEp = "",
            rating = "8.5",
            poster = "https://example.com/poster.jpg",
            url = "https://example.com/sample",
            year = "2024",
            country = "indonesia"
        )
        assertEquals("🇮🇩 ID", CountryUtils.getCountryBadge(item))
    }

    @Test
    fun testGetCountryBadge_heuristicsFromTitle() {
        val drakorItem = MovieItem(
            id = 2,
            title = "Drakor Romance 2024",
            latestEp = "EPS 16",
            rating = "9.0",
            poster = "https://example.com/poster.jpg",
            url = "https://example.com/drakor-sample",
            year = "2024",
            country = ""
        )
        assertEquals("🇰🇷 KR", CountryUtils.getCountryBadge(drakorItem))

        val animeItem = MovieItem(
            id = 3,
            title = "Naruto Shippuden",
            latestEp = "EPS 500",
            rating = "9.2",
            poster = "https://example.com/poster.jpg",
            url = "https://example.com/naruto",
            year = "2017",
            country = ""
        )
        assertEquals("🇯🇵 JP", CountryUtils.getCountryBadge(animeItem))
    }
}
