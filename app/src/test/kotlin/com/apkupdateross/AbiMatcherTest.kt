package com.apkupdateross

import com.apkupdateross.util.AbiMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbiMatcherTest {
    @Test
    fun universalArchIsCompatible() {
        assertTrue(AbiMatcher.isCompatible(listOf("universal"), listOf("armeabi-v7a")))
        assertTrue(AbiMatcher.isCompatible(listOf("noarch"), listOf("x86")))
    }

    @Test
    fun arm64IsNotCompatibleWithArm32OnlyDevice() {
        assertFalse(AbiMatcher.isCompatible(listOf("arm64-v8a"), listOf("armeabi-v7a", "armeabi")))
    }

    @Test
    fun arm32IsCompatibleWithArm32Device() {
        assertTrue(AbiMatcher.isCompatible(listOf("armeabi-v7a"), listOf("armeabi-v7a", "armeabi")))
        assertTrue(AbiMatcher.isCompatible(listOf("arm"), listOf("armeabi-v7a", "armeabi")))
    }

    @Test
    fun arm32IsCompatibleWithDeviceThatReportsBothArmAbis() {
        assertTrue(AbiMatcher.isCompatible(listOf("armeabi-v7a"), listOf("arm64-v8a", "armeabi-v7a")))
    }

    @Test
    fun arm32IsNotCompatibleWithArm64OnlyDevice() {
        assertFalse(AbiMatcher.isCompatible(listOf("armeabi-v7a"), listOf("arm64-v8a")))
    }

    @Test
    fun x86_64IsNotCompatibleWithX86OnlyDevice() {
        assertFalse(AbiMatcher.isCompatible(listOf("x86_64"), listOf("x86")))
    }

    @Test
    fun x86IsCompatibleWithDeviceThatReportsBothX86Abis() {
        assertTrue(AbiMatcher.isCompatible(listOf("x86"), listOf("x86_64", "x86")))
    }

    @Test
    fun selectsArmv7AssetBeforeArm64OnArm32Device() {
        val assets = listOf(
            Asset("https://example.com/app-arm64-v8a.apk", 20),
            Asset("https://example.com/app-armv7.apk", 10)
        )

        assertEquals(
            "https://example.com/app-armv7.apk",
            AbiMatcher.selectCompatible(assets, listOf("armeabi-v7a"), Asset::name, Asset::size)?.name
        )
    }

    @Test
    fun rejectsArm64OnlyAssetsOnArm32Device() {
        val assets = listOf(Asset("https://example.com/app-arm64-v8a.apk", 20))

        assertNull(AbiMatcher.selectCompatible(assets, listOf("armeabi-v7a"), Asset::name, Asset::size))
    }

    @Test
    fun selectsUntaggedAssetWhenSpecificAssetsAreIncompatible() {
        val assets = listOf(
            Asset("https://example.com/app-arm64-v8a.apk", 20),
            Asset("https://example.com/app.apk", 30)
        )

        assertEquals(
            "https://example.com/app.apk",
            AbiMatcher.selectCompatible(assets, listOf("armeabi-v7a"), Asset::name, Asset::size)?.name
        )
    }

    private data class Asset(
        val name: String,
        val size: Long
    )
}
