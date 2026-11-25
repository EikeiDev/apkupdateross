package com.apkupdateross.util

import com.apkupdateross.data.ui.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update


class Badger {

    private val badges = MutableStateFlow(mapOf(
        Screen.Apps.route to "",
        Screen.Search.route to "",
        Screen.Updates.route to "",
        Screen.Settings.route to ""
    ))

    fun flow() = badges

    fun changeSearchBadge(number: String) = changeBadge(Screen.Search.route, number)

    fun changeAppsBadge(number: String) = changeBadge(Screen.Apps.route, number)

    fun changeUpdatesBadge(number: String) = changeBadge(Screen.Updates.route, number)

    private fun changeBadge(route: String, number: String) {
        val finalNumber = if (number.toIntOrNull() == 0) "" else number
        badges.update { current -> current + (route to finalNumber) }
    }

}
