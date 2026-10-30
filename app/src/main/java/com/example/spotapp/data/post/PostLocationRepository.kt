package com.example.spotapp.data.post

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class PostLocationSuggestion(val title: String, val subtitle: String, val label: String)

/** Searches only the place name the user types. No device coordinates or location permission needed. */
internal class PostLocationRepository(context: Context) {
    private val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
    private val cache = linkedMapOf<String, List<PostLocationSuggestion>>()

    suspend fun search(text: String): List<PostLocationSuggestion> {
        val query = text.trim().take(160)
        if (query.length < 3) return emptyList()
        val key = query.lowercase(Locale.ROOT)
        synchronized(cache) { cache[key] }?.let { return it }
        if (!Geocoder.isPresent()) throw IOException("Place search is unavailable on this device.")
        val addresses = withTimeout(10_000) {
            if (Build.VERSION.SDK_INT >= 33) lookupAsync(query) else lookupLegacy(query)
        }
        val results = addresses.mapNotNull(::toSuggestion).distinctBy { it.label.lowercase(Locale.ROOT) }.take(5)
        synchronized(cache) {
            if (cache.size >= 20) cache.remove(cache.keys.first())
            cache[key] = results
        }
        return results
    }

    @androidx.annotation.RequiresApi(33)
    private suspend fun lookupAsync(query: String): List<Address> = suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (continuation.isActive) continuation.resume(addresses)
            }
            override fun onError(errorMessage: String?) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Place search couldn't connect."))
            }
        })
    }

    @Suppress("DEPRECATION")
    private suspend fun lookupLegacy(query: String): List<Address> = suspendCancellableCoroutine { continuation ->
        // Older Android has a blocking API. Keep it off the UI and let the search timeout/cancel
        // without waiting for that call. Remove canceled work instead of accumulating typed queries.
        val task = legacyWorker.submit {
            try {
                val result = geocoder.getFromLocationName(query, 5).orEmpty()
                if (continuation.isActive) continuation.resume(result)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
        continuation.invokeOnCancellation {
            task.cancel(true)
            (task as? Runnable)?.let(legacyWorker::remove)
        }
    }

    private fun toSuggestion(address: Address): PostLocationSuggestion? {
        val feature = address.featureName?.trim()?.takeIf { it.isNotBlank() && it.any(Char::isLetter) }
        val title = listOf(feature, address.locality, address.subLocality, address.thoroughfare, address.adminArea, address.countryName)
            .firstOrNull { !it.isNullOrBlank() }?.trim() ?: return null
        val area = listOf(address.subLocality, address.locality, address.subAdminArea, address.adminArea, address.countryName)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .filterNot { it.equals(title, ignoreCase = true) }.distinctBy { it.lowercase(Locale.ROOT) }
        val subtitle = area.joinToString(", ")
        return PostLocationSuggestion(title, subtitle, (listOf(title) + area).joinToString(", ").take(160))
    }

    private companion object {
        val legacyWorker = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(),
            { runnable -> Thread(runnable, "spot-place-search").apply { isDaemon = true } }).apply { allowCoreThreadTimeOut(true) }
    }
}
