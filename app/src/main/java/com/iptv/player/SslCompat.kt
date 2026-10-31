package com.iptv.player

import android.content.Context
import android.os.Build
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Старые Android (6.0 и ниже) не знают новых корневых сертификатов (Let's Encrypt ISRG Root X1 и др.),
 * из-за чего HTTPS-ссылки на плейлисты и потоки не открываются. Добавляем их к системным.
 */
object SslCompat {
    var socketFactory: SSLSocketFactory? = null
        private set
    var trustManager: X509TrustManager? = null
        private set

    fun install(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) return
        try {
            val system = trustManagerFor(null)

            val cf = CertificateFactory.getInstance("X.509")
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            ctx.resources.openRawResource(R.raw.extra_roots).use { input ->
                cf.generateCertificates(input).forEachIndexed { i, c -> ks.setCertificateEntry("extra$i", c) }
            }
            val extra = trustManagerFor(ks)

            val combined = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                    system.checkClientTrusted(chain, authType)

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    try {
                        system.checkServerTrusted(chain, authType)
                    } catch (e: CertificateException) {
                        extra.checkServerTrusted(chain, authType)
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> =
                    system.acceptedIssuers + extra.acceptedIssuers
            }

            val sc = SSLContext.getInstance("TLS")
            sc.init(null, arrayOf<TrustManager>(combined), null)
            socketFactory = sc.socketFactory
            trustManager = combined
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        } catch (_: Exception) {
            // оставляем системные настройки
        }
    }

    private fun trustManagerFor(ks: KeyStore?): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
