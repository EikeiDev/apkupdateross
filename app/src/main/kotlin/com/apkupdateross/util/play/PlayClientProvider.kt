package com.apkupdateross.util.play

import android.content.Context
import android.os.Build
import android.util.Log
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.applyDynamicProxy
import com.apkupdateross.util.dynamicTimeoutInterceptor
import com.aurora.gplayapi.PlayClient
import com.aurora.gplayapi.android.AndroidLogger
import com.aurora.gplayapi.android.NativeDeviceProperties
import com.aurora.gplayapi.device.DeviceProfile
import com.aurora.gplayapi.device.DeviceRegistry
import com.aurora.gplayapi.engine.OkHttpEngine
import com.aurora.gplayapi.session.DefaultPlaySessions
import com.aurora.gplayapi.web.WebPlayClient
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

class PlayClientProvider(
    private val context: Context,
    private val prefs: Prefs
) {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(dynamicTimeoutInterceptor { prefs.playTimeoutSec.get() })
        .applyDynamicProxy(prefs)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val engine = OkHttpEngine(okHttpClient)

    // NativeDeviceProperties.read() inspects EGL configs and must be computed once per process
    // so the DeviceProfile fingerprint stays stable across requests.
    private val deviceProfile: DeviceProfile by lazy { buildDeviceProfile() }

    val sessions: DefaultPlaySessions = DefaultPlaySessions(
        engine = engine,
        device = { deviceProfile },
        locale = { PlayLocales.preferredLocale(context) },
        additionalLocales = { PlayLocales.additionalLocales(context) },
        authenticated = null,
        logger = AndroidLogger
    )

    val playClient: PlayClient = PlayClient(
        engine = engine,
        sessions = sessions,
        logger = AndroidLogger
    )

    val webPlayClient: WebPlayClient = WebPlayClient(
        engine = engine,
        locale = { PlayLocales.preferredLocale(context) }
    )

    fun resetSession() {
        sessions.invalidateAnonymous()
    }

    private fun buildDeviceProfile(): DeviceProfile {
        if (isHuaweiDevice()) {
            Log.i("PlayClientProvider", "Huawei device detected; using default Pixel profile for Google Play.")
            return DeviceRegistry.default
        }
        return runCatching {
            DeviceRegistry.parse("native", NativeDeviceProperties.read(context))
        }.getOrElse { error ->
            Log.w("PlayClientProvider", "Falling back to default Play device profile.", error)
            DeviceRegistry.default
        }
    }

    private fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val hardware = Build.HARDWARE.lowercase(Locale.ROOT)
        return manufacturer.contains("huawei") ||
            hardware.contains("kirin") ||
            hardware.contains("hi3")
    }
}
