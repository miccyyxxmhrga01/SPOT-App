package com.example.spotapp.ui.theme.components

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Videos streamed from Cloudinary are kept on the phone (up to 256 MB, oldest dropped first), so a reel watched
 * again starts at once and costs no data. Local files and gallery picks are read directly, never copied here.
 */
@OptIn(UnstableApi::class)
internal object SpotVideoCache {
    private var cache: SimpleCache? = null

    @Synchronized
    private fun cache(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.applicationContext.cacheDir, "spot-video"),
        LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
        StandaloneDatabaseProvider(context.applicationContext)
    ).also { cache = it }

    private fun network(context: Context) = CacheDataSource.Factory()
        .setCache(cache(context))
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
        // A damaged cache entry falls back to the network instead of failing playback.
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** For the player: https goes through the cache, content:// and file:// are read as usual. */
    fun dataSourceFactory(context: Context): DataSource.Factory = DefaultDataSource.Factory(context.applicationContext, network(context))

    /** Downloads the first [bytes] of [url] into the cache, so the reel after this one starts right away. */
    suspend fun prefetch(context: Context, url: String, bytes: Long = 1_500_000) {
        if (!url.startsWith("https://")) return
        withContext(Dispatchers.IO) {
            val writer = CacheWriter(network(context).createDataSource(),
                DataSpec.Builder().setUri(url).setPosition(0).setLength(bytes).build(), null, null)
            // Swiping on before it finishes stops the download.
            val stop = coroutineContext.job.invokeOnCompletion { writer.cancel() }
            try { writer.cache() } catch (_: IOException) { } finally { stop.dispose() }
        }
    }
}
