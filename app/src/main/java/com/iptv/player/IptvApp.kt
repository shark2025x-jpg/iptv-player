package com.iptv.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

class IptvApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        SslCompat.install(this)
    }

    override fun newImageLoader(): ImageLoader {
        val builder = OkHttpClient.Builder()
        val sf = SslCompat.socketFactory
        val tm = SslCompat.trustManager
        if (sf != null && tm != null) builder.sslSocketFactory(sf, tm)
        return ImageLoader.Builder(this)
            .okHttpClient(builder.build())
            .crossfade(true)
            .build()
    }
}
