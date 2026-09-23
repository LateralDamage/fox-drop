package com.foxdrop.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class FoxApp : Application(), SingletonImageLoader.Factory {
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", "FoxDrop/1.0 (Android; personal family app)").build())
            }
            .build()
    }
    val api by lazy { Api(http) }
    val prefs by lazy { Prefs(this) }

    companion object {
        /** When the app last left the screen; 0 in a fresh process. MainActivity uses it to replay the fox. */
        var leftAt = 0L
    }

    override fun onCreate() {
        super.onCreate()
        Notify.createChannels(this)
        WatchWorker.schedule(this)
        EventAlarms.reschedule(this)
    }

    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(200L shl 20).build() }
        .build()
}
