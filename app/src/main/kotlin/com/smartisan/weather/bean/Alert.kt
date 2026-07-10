package com.smartisan.weather.bean

import android.os.Parcel
import android.os.Parcelable

/**
 * 天气预警集合，复刻自原版 com.smartisan.weather.bean.Alert。
 * 原版字段 private + 显式 getter/setter。
 */
class Alert : Parcelable {
    private var locationKey: String? = null
    private var getTime: String? = null
    private var mInfos: ArrayList<AlertInfo>? = null
    private var publishTime: String? = null

    constructor()

    constructor(parcel: Parcel) {
        locationKey = parcel.readString()
        getTime = parcel.readString()
        mInfos = parcel.createTypedArrayList(AlertInfo.CREATOR)
        publishTime = parcel.readString()
    }

    fun addInfo(alertInfo: AlertInfo) {
        if (mInfos == null) {
            mInfos = ArrayList()
        }
        mInfos!!.add(alertInfo)
    }

    fun getLocationKey(): String? = locationKey
    fun setLocationKey(value: String?) { locationKey = value }

    fun getGetTime(): String? = getTime
    fun setGetTime(value: String?) { getTime = value }

    fun getmInfos(): ArrayList<AlertInfo>? = mInfos
    fun setmInfos(value: ArrayList<AlertInfo>?) { mInfos = value }

    fun getPublishTime(): String? = publishTime
    fun setPublishTime(value: String?) { publishTime = value }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(locationKey)
        parcel.writeString(getTime)
        parcel.writeTypedList(mInfos)
        parcel.writeString(publishTime)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("getTime  $getTime")
        sb.append("publishTime  $publishTime")
        val list = mInfos
        if (list != null) {
            for (info in list) {
                sb.append("  info = $info")
            }
        }
        return sb.toString()
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Alert> =
            object : Parcelable.Creator<Alert> {
                override fun createFromParcel(parcel: Parcel): Alert = Alert(parcel)

                override fun newArray(size: Int): Array<Alert?> = arrayOfNulls(size)
            }
    }
}
