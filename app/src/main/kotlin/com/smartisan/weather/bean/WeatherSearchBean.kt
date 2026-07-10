package com.smartisan.weather.bean

import java.io.Serializable

/**
 * 天气城市搜索结果 Bean，复刻自原版 com.smartisan.weather.bean.WeatherSearchBean。
 *
 * 字段结构与原版一致；移除 Gson 注解（复刻版使用 org.json 解析）。
 */
class WeatherSearchBean : Serializable {

    @JvmField var city: String? = null
    @JvmField var cityId: String? = null
    @JvmField var country: String? = null
    @JvmField var countryPinyin: String? = null
    @JvmField var county: String? = null
    @JvmField var countyEn: String? = null
    @JvmField var id: String? = null
    @JvmField var province: String? = null

    override fun toString(): String =
        "id  - $id\ncityId  - $cityId\ncountry  - $country\nprovince  - $province" +
            "\ncity  - $city\ncountyEn - $countyEn\ncountryPinyin  - $countryPinyin"
}
