package com.smartisan.weather.bean

import android.os.Parcel
import android.os.Parcelable
import java.util.Objects

/**
 * 单条天气预警，复刻自原版 com.smartisan.weather.bean.AlertInfo。
 * 原版字段 private（混淆名 a-f）+ 显式 getter/setter，此处以语义方法名等价表达。
 */
class AlertInfo : Parcelable {
    private var typeNumber: String? = null
    private var level: String? = null
    private var content: String? = null
    private var publishTime: Long = 0L
    private var levelNumber: String? = null
    private var type: String? = null

    constructor()

    constructor(parcel: Parcel) {
        typeNumber = parcel.readString()
        level = parcel.readString()
        content = parcel.readString()
        publishTime = parcel.readLong()
        levelNumber = parcel.readString()
        type = parcel.readString()
    }

    fun getTypeNumber(): String? = typeNumber
    fun setTypeNumber(value: String?) { typeNumber = value }

    fun getLevel(): String? = level
    fun setLevel(value: String?) { level = value }

    fun getContent(): String? = content
    fun setContent(value: String?) { content = value }

    fun getPublishTime(): Long = publishTime
    fun setPublishTime(value: Long) { publishTime = value }

    fun getLevelNumber(): String? = levelNumber
    fun setLevelNumber(value: String?) { levelNumber = value }

    fun getType(): String? = type
    fun setType(value: String?) { type = value }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(typeNumber)
        parcel.writeString(level)
        parcel.writeString(content)
        parcel.writeLong(publishTime)
        parcel.writeString(levelNumber)
        parcel.writeString(type)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AlertInfo) return false
        return publishTime == other.publishTime &&
            typeNumber == other.typeNumber && level == other.level &&
            content == other.content && levelNumber == other.levelNumber && type == other.type
    }

    override fun hashCode(): Int =
        Objects.hash(typeNumber, level, content, publishTime, levelNumber, type)

    override fun toString(): String =
        "\ntypeNumber - $typeNumber\nlevel - $level content - $content\n" +
            "publishTime - $publishTime\nlevelNumber - $levelNumber\ntype - $type"

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<AlertInfo> =
            object : Parcelable.Creator<AlertInfo> {
                override fun createFromParcel(parcel: Parcel): AlertInfo = AlertInfo(parcel)

                override fun newArray(size: Int): Array<AlertInfo?> = arrayOfNulls(size)
            }
    }
}
