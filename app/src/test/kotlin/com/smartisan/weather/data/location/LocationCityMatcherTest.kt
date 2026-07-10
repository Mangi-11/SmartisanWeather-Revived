package com.smartisan.weather.data.location

import com.smartisan.weather.data.model.SearchResultCity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationCityMatcherTest {

    @Test
    fun `normalization removes administrative suffixes and country aliases`() {
        assertEquals("北京", normalizePlaceName("北京市"))
        assertEquals("广西", normalizePlaceName("广西壮族自治区"))
        assertEquals("beijing", normalizePlaceName("Beijing Shi"))
        assertEquals("china", normalizePlaceName("中华人民共和国"))
        assertEquals("sao paulo".replace(" ", ""), normalizePlaceName("São Paulo"))
    }

    @Test
    fun `queries prefer district and remove normalized duplicates`() {
        val queries = LocationCityMatcher.buildSearchQueries(
            listOf(
                GeocodedAddress(
                    country = "中国",
                    province = "北京市",
                    city = "北京市",
                ),
                GeocodedAddress(
                    country = "中国",
                    province = "北京市",
                    city = "北京",
                    district = "海淀区",
                ),
            ),
        )

        assertEquals(listOf("海淀区", "海淀", "北京市", "北京"), queries)
    }

    @Test
    fun `exact district beats municipality root result`() {
        val address = GeocodedAddress(
            country = "中国",
            province = "北京市",
            city = "北京市",
            district = "海淀区",
        )
        val beijing = city("101010100", county = "北京", city = "北京", province = "北京")
        val haidian = city("101010200", county = "海淀", city = "北京", province = "北京")

        val best = LocationCityMatcher.findBestMatch(listOf(address), listOf(beijing, haidian))

        assertEquals("101010200", best?.cityId)
        assertTrue(LocationCityMatcher.score(address, haidian) > LocationCityMatcher.score(address, beijing))
    }

    @Test
    fun `english geocoder names match api pinyin fields`() {
        val address = GeocodedAddress(
            country = "China",
            province = "Beijing Municipality",
            city = "Beijing",
            district = "Haidian District",
        )
        val haidian = city(
            id = "101010200",
            county = "海淀",
            city = "北京",
            province = "北京",
            countyEn = "Haidian",
            countyPinyin = "haidian",
        )

        assertEquals(haidian, LocationCityMatcher.findBestMatch(listOf(address), listOf(haidian)))
    }

    @Test
    fun `province alone is insufficient to select an arbitrary city`() {
        val address = GeocodedAddress(country = "中国", province = "广东省")
        val guangzhou = city("101280101", county = "广州", city = "广州", province = "广东")

        assertNull(LocationCityMatcher.findBestMatch(listOf(address), listOf(guangzhou)))
    }

    @Test
    fun `province and city hierarchy disambiguate equal county names`() {
        val address = GeocodedAddress(
            country = "中国",
            province = "陕西省",
            city = "榆林市",
            district = "府谷县",
        )
        val wrongProvince = city("wrong", county = "府谷", city = "其他", province = "山西")
        val correct = city("correct", county = "府谷", city = "榆林", province = "陕西")

        assertEquals(correct, LocationCityMatcher.findBestMatch(listOf(address), listOf(wrongProvince, correct)))
    }

    @Test
    fun `candidates without weather city id are ignored`() {
        val address = GeocodedAddress(city = "北京")
        val invalid = city("", county = "北京", city = "北京", province = "北京")

        assertNull(LocationCityMatcher.findBestMatch(listOf(address), listOf(invalid)))
    }

    private fun city(
        id: String,
        county: String,
        city: String,
        province: String,
        country: String = "中国",
        countyEn: String = "",
        countyPinyin: String = "",
    ) = SearchResultCity(
        cityId = id,
        county = county,
        city = city,
        province = province,
        country = country,
        countyEn = countyEn,
        countyPinyin = countyPinyin,
    )
}
