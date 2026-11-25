package com.apkupdateross

import com.apkupdateross.util.ProxyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.Proxy

class ProxyConfigTest {

	@Test
	fun validatesConfigAndBuildsJavaProxy() {
		assertFalse(ProxyConfig(enabled = false, type = 0, host = "127.0.0.1", port = 8080).isValid())
		assertEquals(Proxy.NO_PROXY, ProxyConfig(enabled = false, type = 0, host = "127.0.0.1", port = 8080).toJavaProxy())

		assertFalse(ProxyConfig(enabled = true, type = 0, host = " ", port = 8080).isValid())
		assertFalse(ProxyConfig(enabled = true, type = 0, host = "127.0.0.1", port = 0).isValid())
		assertFalse(ProxyConfig(enabled = true, type = 0, host = "127.0.0.1", port = 70000).isValid())

		val httpProxy = ProxyConfig(enabled = true, type = 0, host = "proxy.local", port = 8080).toJavaProxy()
		assertEquals(Proxy.Type.HTTP, httpProxy.type())

		val socksProxy = ProxyConfig(enabled = true, type = 1, host = "proxy.local", port = 1080).toJavaProxy()
		assertEquals(Proxy.Type.SOCKS, socksProxy.type())
	}
}
