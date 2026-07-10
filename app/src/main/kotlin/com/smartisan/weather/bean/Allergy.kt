package com.smartisan.weather.bean

import java.io.Serializable

/**
 * 生活指数（过敏/紫外线），复刻自原版 com.smartisan.weather.bean.Allergy。
 * 原版字段 private + 显式 getter/setter。
 */
class Allergy : Serializable {
    private var agContent: String? = null
    private var agLevel: String? = null
    private var agTypeCn: String? = null
    private var getTime: String? = null
    private var locationKey: String? = null
    private var publishTime: String? = null
    private var uvContent: String? = null
    private var uvLevel: String? = null
    private var uvTypeCn: String? = null

    fun getAgContent(): String? = agContent
    fun setAgContent(value: String?) { agContent = value }
    fun getAgLevel(): String? = agLevel
    fun setAgLevel(value: String?) { agLevel = value }
    fun getAgTypeCn(): String? = agTypeCn
    fun setAgTypeCn(value: String?) { agTypeCn = value }
    fun getGetTime(): String? = getTime
    fun setGetTime(value: String?) { getTime = value }
    fun getLocationKey(): String? = locationKey
    fun setLocationKey(value: String?) { locationKey = value }
    fun getPublishTime(): String? = publishTime
    fun setPublishTime(value: String?) { publishTime = value }
    fun getUvContent(): String? = uvContent
    fun setUvContent(value: String?) { uvContent = value }
    fun getUvLevel(): String? = uvLevel
    fun setUvLevel(value: String?) { uvLevel = value }
    fun getUvTypeCn(): String? = uvTypeCn
    fun setUvTypeCn(value: String?) { uvTypeCn = value }

    /** 原版 isEmpty() 固定返回 false。 */
    fun isEmpty(): Boolean = false

    override fun toString(): String = "agContent - $agContent\n uvContent - $uvContent"
}
