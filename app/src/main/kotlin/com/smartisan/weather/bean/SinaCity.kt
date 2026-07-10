package com.smartisan.weather.bean

import java.io.Serializable

/**
 * 新浪城市数据。
 *
 * 完整复刻自原版 com.smartisan.weather.bean.SinaCity。
 */
class SinaCity : Serializable {

    @JvmField
    var cityChild: String? = null
    @JvmField
    var cityId: String? = null
    @JvmField
    var cityNameAB: String? = null
    @JvmField
    var cityParent: String? = null
    @JvmField
    var country: String? = null
    @JvmField
    var id: Int = 0
    @JvmField
    var isAdd: Boolean = false
    @JvmField
    var keyName: String? = null
    @JvmField
    var latitude: String? = null
    @JvmField
    var longitude: String? = null
    @JvmField
    var otherData: String? = null
    @JvmField
    var province: String? = null

    constructor()

    constructor(cityId: String?, cityChild: String?, cityParent: String?) {
        this.cityId = cityId
        this.cityChild = cityChild
        this.cityParent = cityParent
    }

    val name: String
        get() = (country ?: "") + (province ?: "") + (cityParent ?: "") + (cityChild ?: "")

    companion object {
        private const val serialVersionUID: Long = 6275744081050817364L
    }
}
