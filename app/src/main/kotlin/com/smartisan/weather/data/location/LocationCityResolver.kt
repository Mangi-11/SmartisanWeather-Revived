package com.smartisan.weather.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.model.SearchResultCity
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Resolves a device location to the city identifier used by Smartisan Weather. */
class LocationCityResolver(context: Context) {

    private val appContext = context.applicationContext
    private val cityRepository = CityRepository(appContext)

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
        if (!Geocoder.isPresent()) {
            throw LocationCityResolutionException(
                reason = LocationCityFailureReason.GEOCODER_UNAVAILABLE,
                message = "No reverse-geocoding service is available on this device",
            )
        }

        val addresses = withTimeoutOrNull(GEOCODER_TIMEOUT_MILLIS) {
            reverseGeocode(location)
        } ?: throw LocationCityResolutionException(
            reason = LocationCityFailureReason.GEOCODING_FAILED,
            message = "Reverse geocoding timed out",
        )
        val geocodedAddresses = addresses
            .map { address -> address.toGeocodedAddress() }
            .filterNot { address -> address.isEmpty() }

        if (geocodedAddresses.isEmpty()) {
            throw LocationCityResolutionException(
                reason = LocationCityFailureReason.ADDRESS_NOT_FOUND,
                message = "Reverse geocoding returned no usable administrative address",
            )
        }

        val queries = LocationCityMatcher.buildSearchQueries(geocodedAddresses)
        if (queries.isEmpty()) {
            throw LocationCityResolutionException(
                reason = LocationCityFailureReason.ADDRESS_NOT_FOUND,
                message = "The geocoded address does not contain a district, city, or province",
            )
        }

        val candidates = LinkedHashMap<String, SearchResultCity>()
        for (query in queries) {
            val searchResult = cityRepository.searchCitiesResult(query)
            val results = searchResult.getOrElse { cause ->
                throw LocationCityResolutionException(
                    reason = LocationCityFailureReason.CITY_SEARCH_FAILED,
                    message = "City search request failed during location resolution",
                    cause = cause,
                )
            }
            results.forEach { candidate ->
                if (candidate.cityId.isNotBlank()) candidates.putIfAbsent(candidate.cityId, candidate)
            }

            LocationCityMatcher.findBestMatch(geocodedAddresses, candidates.values.toList())
                ?.let { return it }
        }

        throw LocationCityResolutionException(
            reason = LocationCityFailureReason.CITY_NOT_FOUND,
            message = "No Smartisan weather city matches the reverse-geocoded address",
        )
    }

    private suspend fun reverseGeocode(location: Location): List<Address> {
        val geocoder = Geocoder(appContext, Locale.SIMPLIFIED_CHINESE)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                reverseGeocodeAsync(geocoder, location)
            } else {
                reverseGeocodeBlocking(geocoder, location)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw LocationCityResolutionException(
                reason = LocationCityFailureReason.GEOCODING_FAILED,
                message = "Reverse geocoding failed",
                cause = exception,
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun reverseGeocodeAsync(
        geocoder: Geocoder,
        location: Location,
    ): List<Address> = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(
            location.latitude,
            location.longitude,
            MAX_GEOCODER_RESULTS,
            object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: List<Address>) {
                    if (continuation.isActive) continuation.resume(addresses)
                }

                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IOException(errorMessage ?: "Unknown reverse-geocoding error"),
                        )
                    }
                }
            },
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeBlocking(
        geocoder: Geocoder,
        location: Location,
    ): List<Address> = withContext(Dispatchers.IO) {
        geocoder.getFromLocation(
            location.latitude,
            location.longitude,
            MAX_GEOCODER_RESULTS,
        ).orEmpty()
    }

    private fun validateLocation(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) {
            throw LocationCityResolutionException(
                reason = LocationCityFailureReason.INVALID_LOCATION,
                message = "Location coordinates are outside the valid latitude/longitude range",
            )
        }
    }

    private fun Address.toGeocodedAddress(): GeocodedAddress {
        val province = adminArea.orEmpty()
        val localityName = locality.orEmpty()
        val subAdminAreaName = subAdminArea.orEmpty()
        val city = localityName.ifBlank { subAdminAreaName }
            .ifBlank { province.takeIf(::isDirectControlledMunicipality).orEmpty() }
        val district = subLocality.orEmpty()
            .ifBlank { subAdminAreaName.takeUnless { equivalentPlaceName(it, city) }.orEmpty() }
            .takeUnless { equivalentPlaceName(it, city) }
            .orEmpty()

        return GeocodedAddress(
            country = countryName.orEmpty().ifBlank { countryCode.orEmpty() },
            province = province,
            city = city,
            district = district,
        )
    }

    private fun GeocodedAddress.isEmpty(): Boolean =
        country.isBlank() && province.isBlank() && city.isBlank() && district.isBlank()

    private fun isDirectControlledMunicipality(name: String): Boolean =
        normalizePlaceName(name) in DIRECT_CONTROLLED_MUNICIPALITIES

    private fun equivalentPlaceName(first: String, second: String): Boolean {
        val normalizedFirst = normalizePlaceName(first)
        return normalizedFirst.isNotEmpty() && normalizedFirst == normalizePlaceName(second)
    }

    private companion object {
        const val MAX_GEOCODER_RESULTS = 5
        const val GEOCODER_TIMEOUT_MILLIS = 15_000L

        val DIRECT_CONTROLLED_MUNICIPALITIES = setOf(
            "北京",
            "上海",
            "天津",
            "重庆",
            "香港",
            "澳门",
        ).mapTo(mutableSetOf(), ::normalizePlaceName)
    }
}

enum class LocationCityFailureReason {
    INVALID_LOCATION,
    GEOCODER_UNAVAILABLE,
    GEOCODING_FAILED,
    ADDRESS_NOT_FOUND,
    CITY_SEARCH_FAILED,
    CITY_NOT_FOUND,
}

class LocationCityResolutionException(
    val reason: LocationCityFailureReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
