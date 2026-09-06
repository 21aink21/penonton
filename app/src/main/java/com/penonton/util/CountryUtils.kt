package com.penonton.util

import com.penonton.data.model.MovieItem

object CountryUtils {

    private val countryMap = mapOf(
        // United States & West
        "usa" to Pair("🇺🇸", "US"),
        "united states" to Pair("🇺🇸", "US"),
        "amerika" to Pair("🇺🇸", "US"),
        "us" to Pair("🇺🇸", "US"),
        "west" to Pair("🇺🇸", "US"),
        "western" to Pair("🇺🇸", "US"),
        "hollywood" to Pair("🇺🇸", "US"),

        // South Korea
        "korea" to Pair("🇰🇷", "KR"),
        "south korea" to Pair("🇰🇷", "KR"),
        "south-korea" to Pair("🇰🇷", "KR"),
        "drakor" to Pair("🇰🇷", "KR"),
        "kr" to Pair("🇰🇷", "KR"),
        "korean" to Pair("🇰🇷", "KR"),

        // Japan
        "japan" to Pair("🇯🇵", "JP"),
        "jepang" to Pair("🇯🇵", "JP"),
        "jp" to Pair("🇯🇵", "JP"),
        "anime" to Pair("🇯🇵", "JP"),

        // China & Taiwan & Hong Kong
        "china" to Pair("🇨🇳", "CN"),
        "cina" to Pair("🇨🇳", "CN"),
        "cn" to Pair("🇨🇳", "CN"),
        "donghua" to Pair("🇨🇳", "CN"),
        "hong kong" to Pair("🇭🇰", "HK"),
        "hong-kong" to Pair("🇭🇰", "HK"),
        "hongkong" to Pair("🇭🇰", "HK"),
        "hk" to Pair("🇭🇰", "HK"),
        "taiwan" to Pair("🇹🇼", "TW"),
        "tw" to Pair("🇹🇼", "TW"),

        // Indonesia
        "indonesia" to Pair("🇮🇩", "ID"),
        "id" to Pair("🇮🇩", "ID"),
        "indo" to Pair("🇮🇩", "ID"),

        // Thailand
        "thailand" to Pair("🇹🇭", "TH"),
        "tailand" to Pair("🇹🇭", "TH"),
        "th" to Pair("🇹🇭", "TH"),
        "thai" to Pair("🇹🇭", "TH"),

        // United Kingdom
        "united kingdom" to Pair("🇬🇧", "UK"),
        "uk" to Pair("🇬🇧", "UK"),
        "gb" to Pair("🇬🇧", "UK"),
        "inggris" to Pair("🇬🇧", "UK"),
        "britain" to Pair("🇬🇧", "UK"),
        "england" to Pair("🇬🇧", "UK"),

        // India
        "india" to Pair("🇮🇳", "IN"),
        "in" to Pair("🇮🇳", "IN"),
        "bollywood" to Pair("🇮🇳", "IN"),

        // Spain & European
        "spain" to Pair("🇪🇸", "ES"),
        "spanyol" to Pair("🇪🇸", "ES"),
        "es" to Pair("🇪🇸", "ES"),
        "france" to Pair("🇫🇷", "FR"),
        "perancis" to Pair("🇫🇷", "FR"),
        "fr" to Pair("🇫🇷", "FR"),
        "germany" to Pair("🇩🇪", "DE"),
        "jerman" to Pair("🇩🇪", "DE"),
        "de" to Pair("🇩🇪", "DE"),
        "italy" to Pair("🇮🇹", "IT"),
        "itali" to Pair("🇮🇹", "IT"),
        "it" to Pair("🇮🇹", "IT"),
        "russia" to Pair("🇷🇺", "RU"),
        "rusia" to Pair("🇷🇺", "RU"),
        "ru" to Pair("🇷🇺", "RU"),
        "canada" to Pair("🇨🇦", "CA"),
        "kanada" to Pair("🇨🇦", "CA"),
        "ca" to Pair("🇨🇦", "CA"),
        "australia" to Pair("🇦🇺", "AU"),
        "au" to Pair("🇦🇺", "AU"),
        "new zealand" to Pair("🇳🇿", "NZ"),
        "new-zealand" to Pair("🇳🇿", "NZ"),
        "selandia baru" to Pair("🇳🇿", "NZ"),
        "nz" to Pair("🇳🇿", "NZ"),
        "philippines" to Pair("🇵🇭", "PH"),
        "pilipina" to Pair("🇵🇭", "PH"),
        "ph" to Pair("🇵🇭", "PH"),
        "mexico" to Pair("🇲🇽", "MX"),
        "meksiko" to Pair("🇲🇽", "MX"),
        "mx" to Pair("🇲🇽", "MX"),
        "malaysia" to Pair("🇲🇾", "MY"),
        "my" to Pair("🇲🇾", "MY"),
        "singapore" to Pair("🇸🇬", "SG"),
        "singapura" to Pair("🇸🇬", "SG"),
        "sg" to Pair("🇸🇬", "SG"),
        "turkey" to Pair("🇹🇷", "TR"),
        "turki" to Pair("🇹🇷", "TR"),
        "tr" to Pair("🇹🇷", "TR"),
        "brazil" to Pair("🇧🇷", "BR"),
        "br" to Pair("🇧🇷", "BR"),
        "argentina" to Pair("🇦🇷", "AR"),
        "ar" to Pair("🇦🇷", "AR"),
        "vietnam" to Pair("🇻🇳", "VN"),
        "vn" to Pair("🇻🇳", "VN"),
        "sweden" to Pair("🇸🇪", "SE"),
        "swedia" to Pair("🇸🇪", "SE"),
        "se" to Pair("🇸🇪", "SE"),
        "norway" to Pair("🇳🇴", "NO"),
        "norwegia" to Pair("🇳🇴", "NO"),
        "no" to Pair("🇳🇴", "NO"),
        "denmark" to Pair("🇩🇰", "DK"),
        "dk" to Pair("🇩🇰", "DK"),
        "netherlands" to Pair("🇳🇱", "NL"),
        "belanda" to Pair("🇳🇱", "NL"),
        "nl" to Pair("🇳🇱", "NL"),
        "belgium" to Pair("🇧🇪", "BE"),
        "belgia" to Pair("🇧🇪", "BE"),
        "be" to Pair("🇧🇪", "BE"),
        "poland" to Pair("🇵🇱", "PL"),
        "polandia" to Pair("🇵🇱", "PL"),
        "pl" to Pair("🇵🇱", "PL"),
        "ireland" to Pair("🇮🇪", "IE"),
        "irlandia" to Pair("🇮🇪", "IE"),
        "ie" to Pair("🇮🇪", "IE"),
        "south africa" to Pair("🇿🇦", "ZA"),
        "afrika selatan" to Pair("🇿🇦", "ZA"),
        "za" to Pair("🇿🇦", "ZA"),
        "egypt" to Pair("🇪🇬", "EG"),
        "mesir" to Pair("🇪🇬", "EG"),
        "eg" to Pair("🇪🇬", "EG"),
        "saudi arabia" to Pair("🇸🇦", "SA"),
        "arab saudi" to Pair("🇸🇦", "SA"),
        "sa" to Pair("🇸🇦", "SA")
    )

    fun formatCountry(countryRaw: String): String {
        val trimmed = countryRaw.trim().lowercase()
        if (trimmed.isEmpty()) return ""

        // Exact match
        countryMap[trimmed]?.let {
            return "${it.first} ${it.second}"
        }

        // Substring match
        for ((key, pair) in countryMap) {
            if (key.length >= 3 && trimmed.contains(key)) {
                return "${pair.first} ${pair.second}"
            }
        }

        return countryRaw.trim().take(6).uppercase()
    }

    fun getCountryBadge(item: MovieItem): String {
        if (item.country.isNotEmpty()) {
            return formatCountry(item.country)
        }

        val text = "${item.url} ${item.title}".lowercase()

        // 1. Check heuristics from URL / title
        when {
            text.contains("drakor") || text.contains("korean") || text.contains("south-korea") || text.contains("korea") -> return "🇰🇷 KR"
            text.contains("doraemon") || text.contains("anime") || text.contains("japan") || text.contains("jepang") || text.contains("gundam") || text.contains("naruto") || text.contains("piece") -> return "🇯🇵 JP"
            text.contains("donghua") || text.contains("china") || text.contains("cina") || text.contains("soul-land") || text.contains("btth") || text.contains("tang-clan") || text.contains("white-snake") -> return "🇨🇳 CN"
            text.contains("thailand") || text.contains("tailand") || text.contains("thai") -> return "🇹🇭 TH"
            text.contains("indonesia") || text.contains("indo") || text.contains("warkop") || text.contains("kkn") -> return "🇮🇩 ID"
            text.contains("india") || text.contains("bollywood") -> return "🇮🇳 IN"
            text.contains("west") || text.contains("usa") || text.contains("hollywood") || text.contains("united-states") -> return "🇺🇸 US"
        }

        // 2. Default for series / movies if not detected
        return if (item.url.contains("nontondrama") || item.latestEp.contains("EPS", ignoreCase = true)) {
            "" // Empty if not certain for series
        } else {
            "" // Leave empty if not certain
        }
    }
}
