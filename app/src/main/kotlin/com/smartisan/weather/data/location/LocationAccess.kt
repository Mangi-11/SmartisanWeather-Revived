package com.smartisan.weather.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

enum class LocationAccess {
    NONE, APPROXIMATE, PRECISE;

    companion object {
        fun read(context: Context): LocationAccess = when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED -> PRECISE
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED -> APPROXIMATE
            else -> NONE
        }
    }
}
