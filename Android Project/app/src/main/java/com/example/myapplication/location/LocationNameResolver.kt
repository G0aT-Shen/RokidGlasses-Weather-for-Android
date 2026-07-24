package com.example.myapplication.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LocationNameResolver(context: Context) {
    private val geocoder = Geocoder(context.applicationContext, Locale.SIMPLIFIED_CHINESE)

    suspend fun resolve(latitude: Double, longitude: Double): String? =
        withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                }
            }
            address
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
                ?.trim()
                ?.removeSuffix("市")
                ?.takeIf(String::isNotBlank)
        }

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 5_000L
    }
}
