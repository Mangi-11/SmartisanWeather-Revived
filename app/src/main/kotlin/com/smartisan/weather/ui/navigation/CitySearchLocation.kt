package com.smartisan.weather.ui.navigation

import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.data.model.SavedCity

/** Small Parcelable payload used to insert a search selection after the displayed city. */
internal fun SavedCity.toSearchLocation(): SmartisanLocation = SmartisanLocation().also { location ->
    location.id = id
    location.mLocationKey = locationKey
    location.mLocationName = locationName
    location.mLocationParentName = locationParentName
    location.mCountry = country
    location.mProvince = province
    location.sortOrder = sortOrder
}
