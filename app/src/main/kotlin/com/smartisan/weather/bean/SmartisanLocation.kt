package com.smartisan.weather.bean

import android.os.Parcel
import android.os.Parcelable

/** Activity navigation payload identifying a city and its position in the saved list. */
class SmartisanLocation : Parcelable {
    var id: Int = 0
    var mCountry: String? = null
    var mLocationKey: String? = null
    var mLocationName: String? = null
    var mLocationParentName: String? = null
    var mProvince: String? = null
    var sortOrder: Int = 0

    constructor()

    private constructor(parcel: Parcel) {
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
