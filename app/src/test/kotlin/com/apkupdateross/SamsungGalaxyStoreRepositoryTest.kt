package com.apkupdateross

import com.apkupdateross.repository.SamsungGalaxyStoreDevice
import com.apkupdateross.repository.buildSamsungOdsEnvelope
import com.apkupdateross.repository.buildSamsungGalaxyStoreProfile
import com.apkupdateross.repository.isSamsungGalaxyStoreUpdate
import com.apkupdateross.repository.isTrustedSamsungDownloadUrl
import com.apkupdateross.repository.parseSamsungGalaxyStoreSearch
import com.apkupdateross.repository.parseSamsungGalaxyStoreStub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SamsungGalaxyStoreRepositoryTest {

	@Test
	fun buildsInternationalAndChinaProfiles() {
		val global = buildSamsungGalaxyStoreProfile(
			region = "GLOBAL",
			sdk = 35,
			actualSamsungModel = "SM-S921B",
			abi32 = "armeabi-v7a",
			abi64 = "arm64-v8a"
		)
		assertEquals("SM-S921B", global.device.model)
		assertEquals("DBT", global.device.csc)
		assertEquals("425", global.device.mcc)
		assertEquals("01", global.device.mnc)
		assertEquals("en_US", global.language)
		assertEquals("https://us-odc.samsungapps.com/ods.as", global.odsEndpoint)

		val china = buildSamsungGalaxyStoreProfile(
			region = " cn ",
			sdk = 35,
			actualSamsungModel = "SM-S921B",
			abi32 = "armeabi-v7a",
			abi64 = "arm64-v8a"
		)
		assertEquals("SM-S9480", china.device.model)
		assertEquals("CHC", china.device.csc)
		assertEquals("460", china.device.mcc)
		assertEquals("00", china.device.mnc)
		assertEquals("zh_CN", china.language)
		assertEquals("https://cn-ms.galaxyappstore.com/ods.as", china.odsEndpoint)
	}

    @Test
    fun parsesSignedStubDownload() {
        val release = parseSamsungGalaxyStoreStub(
            """
            <result>
              <appId>com.sec.android.app.sbrowser</appId>
              <resultCode>1</resultCode>
              <downloadURI><![CDATA[https://aka-dn.gw.samsungapps.com/astore/app.apk?token=1]]></downloadURI>
              <contentSize>333553465</contentSize>
              <versionCode>1310081502</versionCode>
              <versionName>31.0.0.81</versionName>
              <productId>000005046627</productId>
              <productName><![CDATA[Samsung Browser]]></productName>
            </result>
            """.trimIndent()
        )

        requireNotNull(release)
        assertEquals("com.sec.android.app.sbrowser", release.packageName)
        assertEquals("31.0.0.81", release.version)
        assertEquals(1310081502L, release.versionCode)
        assertEquals(333553465L, release.size)
    }

    @Test
    fun parsesSearchItemsAndSkipsUnavailableEntries() {
        val result = parseSamsungGalaxyStoreSearch(
            """
            <SamsungProtocol>
              <response id="2040" returnCode="0">
                <errorInfo><errorString errorCode="0"/></errorInfo>
                <list>
                  <value name="GUID">com.sec.android.app.sbrowser</value>
                  <value name="productName">Samsung Browser</value>
                  <value name="sellerName">Samsung Electronics</value>
                  <value name="productImgUrl">https://img.samsungapps.com/icon.webp</value>
                  <value name="version">31.0.0.81</value>
                  <value name="versionCode">1310081502</value>
                  <value name="realContentSize">333553465</value>
                  <value name="price">0.00</value>
                </list>
                <list>
                  <value name="GUID">com.example.unavailable</value>
                  <value name="versionCode">2</value>
                  <value name="installableYN">N</value>
                </list>
              </response>
            </SamsungProtocol>
            """.trimIndent()
        )

        assertEquals(1, result.size)
        assertEquals("Samsung Browser", result.single().name)
        assertEquals("Samsung Electronics", result.single().author)
    }

    @Test
    fun buildsEscapedOdsRequestWithCorrectParameterCount() {
        val xml = buildSamsungOdsEnvelope(
            method = "searchProductListEx2Notc",
            requestId = "2040",
            parameters = linkedMapOf("keyword" to "Music & tools", "status" to "0"),
            identity = "0123456789abcdef",
            device = SamsungGalaxyStoreDevice(
                model = "SM-S948B",
                sdk = 35,
                csc = "DBT",
                mcc = "425",
                mnc = "01",
                abi32 = "armeabi-v7a",
                abi64 = "arm64-v8a"
            ),
            nowMillis = 0L
        )

        assertTrue(xml.contains("numParam=\"2\""))
        assertTrue(xml.contains("Music &amp; tools"))
        assertTrue(xml.contains("deviceModel=\"SM-S948B\""))
    }

	@Test
	fun buildsChineseOdsRequestWithChineseLocale() {
		val profile = buildSamsungGalaxyStoreProfile(
			region = "CN",
			sdk = 35,
			actualSamsungModel = null,
			abi32 = "armeabi-v7a",
			abi64 = "arm64-v8a"
		)
		val xml = buildSamsungOdsEnvelope(
			method = "searchProductListEx2Notc",
			requestId = "2040",
			parameters = mapOf("keyword" to "Samsung"),
			identity = "0123456789abcdef",
			device = profile.device,
			language = profile.language,
			nowMillis = 0L
		)

		assertTrue(xml.contains("lang=\"zh_CN\""))
		assertTrue(xml.contains("mcc=\"460\""))
		assertTrue(xml.contains("mnc=\"00\""))
		assertTrue(xml.contains("csc=\"CHC\""))
	}

    @Test
    fun onlyAcceptsSamsungHttpsDownloads() {
        assertTrue(isTrustedSamsungDownloadUrl("https://aka-dn.gw.samsungapps.com/app.apk"))
        assertTrue(isTrustedSamsungDownloadUrl("https://cdn.galaxyappstore.com/app.apk"))
        assertFalse(isTrustedSamsungDownloadUrl("http://aka-dn.gw.samsungapps.com/app.apk"))
        assertFalse(isTrustedSamsungDownloadUrl("https://samsungapps.com.evil.test/app.apk"))
    }

    @Test
    fun comparesRealVersionCodes() {
        assertTrue(isSamsungGalaxyStoreUpdate(200L, 199L))
        assertFalse(isSamsungGalaxyStoreUpdate(200L, 200L))
        assertFalse(isSamsungGalaxyStoreUpdate(199L, 200L))
        assertFalse(isSamsungGalaxyStoreUpdate(200L, Long.MAX_VALUE, "2.1", "2.0", allowVersionCodeDowngrade = false))
        assertTrue(isSamsungGalaxyStoreUpdate(200L, Long.MAX_VALUE, "2.1", "2.0", allowVersionCodeDowngrade = true))
        assertFalse(isSamsungGalaxyStoreUpdate(200L, Long.MAX_VALUE, "2.0", "2.0", allowVersionCodeDowngrade = true))
    }

    @Test(expected = IOException::class)
    fun rejectsDoctypeResponses() {
        parseSamsungGalaxyStoreStub(
            "<!DOCTYPE result [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><result><resultCode>0</resultCode></result>"
        )
    }
}
