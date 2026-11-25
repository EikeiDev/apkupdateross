package com.apkupdateross

import com.apkupdateross.data.ui.Screen
import com.apkupdateross.util.Badger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BadgerTest {

    @Test
    fun zeroClearsBadgeAndNonZeroSetsValue() {
        val badger = Badger()
        badger.changeUpdatesBadge("5")
        assertEquals("5", badger.flow().value[Screen.Updates.route])

        badger.changeUpdatesBadge("0")
        assertEquals("", badger.flow().value[Screen.Updates.route])
    }

    @Test
    fun concurrentBadgeUpdatesDoNotLoseOtherRoutes() = runBlocking {
        val badger = Badger()
        val jobs = listOf(
            async(Dispatchers.Default) {
                repeat(200) { i -> badger.changeAppsBadge((i + 1).toString()) }
            },
            async(Dispatchers.Default) {
                repeat(200) { i -> badger.changeUpdatesBadge((i + 1).toString()) }
            },
            async(Dispatchers.Default) {
                repeat(200) { i -> badger.changeSearchBadge((i + 1).toString()) }
            }
        )
        jobs.awaitAll()

        val state = badger.flow().value
        assertEquals("200", state[Screen.Apps.route])
        assertEquals("200", state[Screen.Updates.route])
        assertEquals("200", state[Screen.Search.route])
    }
}
