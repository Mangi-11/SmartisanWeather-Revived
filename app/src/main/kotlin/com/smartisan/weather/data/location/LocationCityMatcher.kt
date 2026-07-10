package com.smartisan.weather.data.location

import com.smartisan.weather.data.model.SearchResultCity
import java.text.Normalizer
import java.util.Locale

/**
 * Reverse-geocoded administrative names, kept independent from [android.location.Address]
 * so matching behavior can be covered by local JVM tests.
 */
internal data class GeocodedAddress(
    val country: String = "",
    val province: String = "",
    val city: String = "",
    val district: String = "",
)

/** Pure Kotlin matching rules between a geocoded address and Smartisan city-search data. */
internal object LocationCityMatcher {

    private const val MIN_MATCH_SCORE = 40

    /**
     * Prefer the smallest useful administrative unit. A broader query is retained as a
     * fallback because some geocoders return a district name unknown to the weather API.
     */
    fun buildSearchQueries(addresses: List<GeocodedAddress>): List<String> = buildList {
        listOf(
            addresses.map(GeocodedAddress::district),
            addresses.map(GeocodedAddress::city),
            addresses.map(GeocodedAddress::province),
        ).forEach { names ->
            names.forEach { name ->
                val original = name.trim()
                if (original.isNotEmpty()) {
                    add(original)
                    // The production API commonly returns no result for administrative
                    // suffixes such as “东城区”/“北京市”, while “东城”/“北京” succeeds.
                    normalizePlaceName(original)
                        .takeIf { it.isNotEmpty() && it != original.lowercase(Locale.ROOT) }
                        ?.let(::add)
                }
            }
        }
    }.distinctBy { it.lowercase(Locale.ROOT) }

    fun findBestMatch(
        addresses: List<GeocodedAddress>,
        candidates: List<SearchResultCity>,
    ): SearchResultCity? {
        val best = candidates.asSequence()
            .filter { it.cityId.isNotBlank() }
            .map { candidate ->
                val score = addresses.maxOfOrNull { address -> score(address, candidate) }
                    ?: Int.MIN_VALUE
                candidate to score
            }
            .maxByOrNull { (_, score) -> score }

        return best?.takeIf { (_, score) -> score >= MIN_MATCH_SCORE }?.first
    }

    fun score(address: GeocodedAddress, candidate: SearchResultCity): Int {
        var score = 0

        score += scoreLevel(
            expected = address.district,
            actual = listOf(candidate.county, candidate.countyEn, candidate.countyPinyin),
            exactScore = 80,
            partialScore = 55,
            mismatchPenalty = 10,
        )
        score += scoreLevel(
            expected = address.city,
            actual = candidateCityNames(candidate),
            exactScore = 45,
            partialScore = 28,
            mismatchPenalty = 35,
        )
        score += scoreLevel(
            expected = address.province,
            actual = listOf(candidate.province),
            exactScore = 25,
            partialScore = 14,
            mismatchPenalty = 35,
        )
        score += scoreLevel(
            expected = address.country,
            actual = listOf(candidate.country),
            exactScore = 10,
            partialScore = 5,
            mismatchPenalty = 60,
        )

        return score
    }

    private fun candidateCityNames(candidate: SearchResultCity): List<String> = buildList {
        add(candidate.city)
        // The API puts the romanized name in county fields. Those names describe the city
        // only for city-root rows where county and city are the same administrative unit.
        if (normalizePlaceName(candidate.county) == normalizePlaceName(candidate.city)) {
            add(candidate.county)
            add(candidate.countyEn)
            add(candidate.countyPinyin)
        }
    }

    private fun scoreLevel(
        expected: String,
        actual: List<String>,
        exactScore: Int,
        partialScore: Int,
        mismatchPenalty: Int,
    ): Int {
        val normalizedExpected = normalizePlaceName(expected)
        if (normalizedExpected.isEmpty()) return 0

        val normalizedActual = actual.map(::normalizePlaceName).filter(String::isNotEmpty)
        if (normalizedActual.isEmpty()) return 0
        if (normalizedActual.any { it == normalizedExpected }) return exactScore

        val isPartialMatch = normalizedActual.any { name ->
            normalizedExpected.length >= 2 &&
                name.length >= 2 &&
                (name.contains(normalizedExpected) || normalizedExpected.contains(name))
        }
        if (isPartialMatch) return partialScore

        // A localized geocoder may return Latin names while the weather API only supplies
        // Chinese names for the parent levels. Different scripts are not evidence of a
        // hierarchy conflict, so only penalize values that can actually be compared.
        val hasComparableName = normalizedActual.any { name -> sameWritingSystem(normalizedExpected, name) }
        return if (hasComparableName) -mismatchPenalty else 0
    }

    private fun sameWritingSystem(first: String, second: String): Boolean {
        val bothContainHan = first.any { isHanCharacter(it) } && second.any { isHanCharacter(it) }
        val bothContainLatin = first.any { it.isLatinLetter() } && second.any { it.isLatinLetter() }
        return bothContainHan || bothContainLatin
    }

    private fun isHanCharacter(character: Char): Boolean =
        Character.UnicodeScript.of(character.code) == Character.UnicodeScript.HAN

    private fun Char.isLatinLetter(): Boolean =
        isLetter() && Character.UnicodeScript.of(code) == Character.UnicodeScript.LATIN
}

/**
 * Normalizes only for comparison; the original geocoder value is still sent to city search.
 */
internal fun normalizePlaceName(value: String): String {
    var normalized = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        .replace(COMBINING_MARKS, "")
        .replace(SEPARATORS, "")

    normalized = when (normalized) {
        "中国", "中华人民共和国", "china", "peoplesrepublicofchina", "prc", "cn" -> "china"
        else -> normalized
    }

    var suffixRemoved: Boolean
    do {
        suffixRemoved = false
        for (suffix in ADMINISTRATIVE_SUFFIXES) {
            if (normalized.endsWith(suffix) && normalized.length > suffix.length + 1) {
                normalized = normalized.dropLast(suffix.length)
                suffixRemoved = true
                break
            }
        }
    } while (suffixRemoved)

    return normalized
}

private val COMBINING_MARKS = Regex("\\p{M}+")
private val SEPARATORS = Regex("[\\s\\p{Punct}，。；：、·・（）【】]+")

private val ADMINISTRATIVE_SUFFIXES = listOf(
    "特别行政区",
    "维吾尔自治区",
    "壮族自治区",
    "回族自治区",
    "autonomousregion",
    "autonomousprefecture",
    "municipality",
    "prefecture",
    "province",
    "district",
    "county",
    "自治州",
    "自治区",
    "地区",
    "sheng",
    "xian",
    "shi",
    "qu",
    "盟",
    "省",
    "市",
    "区",
    "县",
    "旗",
)
