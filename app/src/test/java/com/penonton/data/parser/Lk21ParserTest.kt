package com.penonton.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Lk21ParserTest {

    @Test
    fun testMovieMirrors_defaultContainsExpectedHosts() {
        val mirrors = Lk21Parser.MOVIE_MIRRORS
        assertTrue(mirrors.isNotEmpty())
        assertTrue(mirrors.contains("https://tv12.lk21official.cc"))
        assertTrue(mirrors.contains("https://tv14.lk21official.cc"))
    }

    @Test
    fun testSeriesMirrors_defaultContainsExpectedHosts() {
        val mirrors = Lk21Parser.SERIES_MIRRORS
        assertTrue(mirrors.isNotEmpty())
        assertTrue(mirrors.contains("https://tv.nontondrama.vip"))
        assertTrue(mirrors.contains("https://tv9.nontondrama.my"))
    }

    @Test
    fun testActiveMovieBase_isFirstInMirrorsList() {
        Lk21Parser.activeMovieBase = "https://tv14.lk21official.cc"
        val mirrors = Lk21Parser.getMovieMirrors()
        assertEquals("https://tv14.lk21official.cc", mirrors[0])
        assertTrue(mirrors.contains("https://tv12.lk21official.cc"))

        // Reset
        Lk21Parser.activeMovieBase = Lk21Parser.MOVIE_MIRRORS[0]
        assertEquals(Lk21Parser.MOVIE_MIRRORS[0], Lk21Parser.getMovieMirrors()[0])
    }

    @Test
    fun testActiveSeriesBase_isFirstInMirrorsList() {
        Lk21Parser.activeSeriesBase = "https://tv9.nontondrama.my"
        val mirrors = Lk21Parser.getSeriesMirrors()
        assertEquals("https://tv9.nontondrama.my", mirrors[0])
        assertTrue(mirrors.contains("https://tv.nontondrama.vip"))

        // Reset
        Lk21Parser.activeSeriesBase = Lk21Parser.SERIES_MIRRORS[0]
        assertEquals(Lk21Parser.SERIES_MIRRORS[0], Lk21Parser.getSeriesMirrors()[0])
    }
}
