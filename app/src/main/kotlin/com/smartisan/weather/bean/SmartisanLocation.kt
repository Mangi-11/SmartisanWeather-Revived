package com.smartisan.weather.bean

import android.os.Parcel
import android.os.Parcelable

/**
 * 城市位置信息，复刻自原版 com.smartisan.weather.bean.SmartisanLocation。
 *
 * 原版实现 Parcelable 且带 Gson @SerializedName 注解；此处保留 Parcelable 与字段结构，
 * 移除 Gson 注解（复刻版使用 org.json 解析，不依赖 Gson）。
 * 原版另有 SmartisanLocation(SinaCity) 构造器，因 SinaCity 不在本次移植范围而省略。
 */
class SmartisanLocation : Parcelable {
    @JvmField var id: Int = 0
    @JvmField var mCountry: String? = null
    @JvmField var mLocationKey: String? = null
    @JvmField var mLocationName: String? = null
    @JvmField var mLocationParentName: String? = null
    @JvmField var mProvince: String? = null
    @JvmField var sortOrder: Int = 0

    constructor()

    constructor(parcel: Parcel) {
        id = parcel.readInt()
        mLocationKey = parcel.readString()
        mLocationName = parcel.readString()
        mLocationParentName = parcel.readString()
        mCountry = parcel.readString()
        mProvince = parcel.readString()
        sortOrder = parcel.readInt()
    }

    constructor(
        locationKey: String?,
        locationName: String?,
        locationParentName: String?,
        province: String?,
        country: String?,
    ) {
        this.mLocationKey = locationKey
        this.mLocationName = locationName
        this.mLocationParentName = locationParentName
        this.mCountry = country
        this.mProvince = province
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(mLocationKey)
        parcel.writeString(mLocationName)
        parcel.writeString(mLocationParentName)
        parcel.writeString(mCountry)
        parcel.writeString(mProvince)
        parcel.writeInt(sortOrder)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<SmartisanLocation> =
            object : Parcelable.Creator<SmartisanLocation> {
                override fun createFromParcel(parcel: Parcel): SmartisanLocation =
                    SmartisanLocation(parcel)

                override fun newArray(size: Int): Array<SmartisanLocation?> = arrayOfNulls(size)
            }
    }
}
