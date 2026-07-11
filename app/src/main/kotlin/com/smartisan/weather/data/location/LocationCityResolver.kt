package com.smartisan.weather.data.location

import android.content.Context
import android.location.Location
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.model.SearchResultCity
import kotlinx.coroutines.CancellationException

/** Resolves a device coordinate directly to Xiaomi Weather's canonical China city key. */
class LocationCityResolver(context: Context) {

    private val cityRepository = CityRepository(context.applicationContext)

    suspend fun resolve(location: Location): Result<SearchResultCity> {
        return try {
            Result.success(resolveOrThrow(location))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private suspend fun resolveOrThrow(location: Location): SearchResultCity {
        validateLocation(location)
        val city = cityRepository.resolveCityByCoordinates(
            latitude = location.latitude,
            longitude = location.longitude,
        ).getOrElse { cause ->
            throw LocationCityResolutionException(
                reason = LocationCityFailureReason.CITY_SEARCH_FAILED,
                message = "Xiaomi weather coordinate lookup failed",
                cause = cause,
            )
        }
        return city ?: throw LocationCityResolutionException(
            reason = LocationCityFailureReason.CITY_NOT_FOUND,
            message = "No Xiaomi weather China city matches this coordinate",
        )
    }

    private fun validateLocation(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude
        if (
            !latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) {
            throw LocationCityResolutionException(
                reason = LocationCityFailureReason.INVALID_LOCATION,
                message = "Location coordinates are outside the valid latitude/longitude range",
            )
        }
    }
}

enum class LocationCityFailureReason {
    INVALID_LOCATION,
    CITY_SEARCH_FAILED,
    CITY_NOT_FOUND,
}

class LocationCityResolutionException(
    val reason: LocationCityFailureReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
