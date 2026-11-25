package com.apkupdateross.util

import com.apkupdateross.prefs.Prefs
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

data class ProxyConfig(
	val enabled: Boolean,
	val type: Int, // 0 = HTTP, 1 = SOCKS5
	val host: String,
	val port: Int,
	val username: String = "",
	val password: String = ""
) {
	fun isValid(): Boolean = enabled && host.trim().isNotEmpty() && port in 1..65535

	fun toJavaProxy(): Proxy {
		if (!isValid()) return Proxy.NO_PROXY
		val javaType = if (type == 1) Proxy.Type.SOCKS else Proxy.Type.HTTP
		return Proxy(javaType, InetSocketAddress.createUnresolved(host.trim(), port))
	}
}

fun Prefs.currentProxyConfig(): ProxyConfig = ProxyConfig(
	enabled = proxyEnabled.get(),
	type = proxyType.get(),
	host = proxyHost.get().trim(),
	port = proxyPort.get(),
	username = proxyUsername.get(),
	password = proxyPassword.get()
)

fun dynamicTimeoutInterceptor(timeoutSecondsProvider: () -> Int): Interceptor = Interceptor { chain ->
	val timeoutSec = timeoutSecondsProvider().coerceIn(5, 300)
	val connectSec = (timeoutSec / 2).coerceIn(5, 60)
	chain
		.withConnectTimeout(connectSec, TimeUnit.SECONDS)
		.withReadTimeout(timeoutSec, TimeUnit.SECONDS)
		.withWriteTimeout(connectSec, TimeUnit.SECONDS)
		.proceed(chain.request())
}

fun dynamicProxySelector(configProvider: () -> ProxyConfig): ProxySelector = object : ProxySelector() {
	override fun select(uri: URI?): List<Proxy> {
		val config = configProvider()
		return listOf(config.toJavaProxy())
	}

	override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
		// No-op: handled by caller retry/error flow
	}
}

fun dynamicProxyAuthenticator(configProvider: () -> ProxyConfig): Authenticator = Authenticator { _, response ->
	val config = configProvider()
	if (!config.isValid() || config.username.isBlank()) return@Authenticator null
	if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
	val credential = Credentials.basic(config.username, config.password)
	response.request.newBuilder()
		.header("Proxy-Authorization", credential)
		.build()
}

fun syncGlobalSocksAuthenticator(configProvider: () -> ProxyConfig) {
	java.net.Authenticator.setDefault(object : java.net.Authenticator() {
		override fun getPasswordAuthentication(): PasswordAuthentication? {
			if (requestorType != RequestorType.PROXY) return null
			val config = configProvider()
			if (!config.isValid() || config.type != 1 || config.username.isBlank()) return null
			return PasswordAuthentication(config.username, config.password.toCharArray())
		}
	})
}

fun OkHttpClient.Builder.applyDynamicProxy(prefs: Prefs): OkHttpClient.Builder {
	syncGlobalSocksAuthenticator { prefs.currentProxyConfig() }
	return proxySelector(dynamicProxySelector { prefs.currentProxyConfig() })
		.proxyAuthenticator(dynamicProxyAuthenticator { prefs.currentProxyConfig() })
}
