package com.apkupdateross.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.ui.activity.MainActivity

class UpdatesNotification(private val context: Context) {

    companion object {
        const val UpdateAction = "updateAction"

        private const val UPDATE_NOTIFICATION_ID = 42
        private const val AUTO_INSTALL_SUCCESS_ID = 43
        private const val AUTO_INSTALL_FAILURE_ID = 44
        private const val BACKGROUND_STATUS_ID = 45
    }

    private val notificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val updatesChannelId = context.getString(R.string.notification_channel_id)
    private val backgroundChannelId = context.getString(R.string.notification_background_channel_id)

    init {
        createNotificationChannels()
    }

    fun showUpdateNotification(num: Int, updates: List<AppUpdate> = emptyList()) {
        val lines = updates.take(5).map { update ->
            val target = update.version.ifBlank { update.versionCode.toString() }
            "${update.name} → $target"
        }
        val extraSummary = if (updates.size > 5) {
            context.getString(R.string.notification_more_updates, updates.size - 5)
        } else {
            null
        }
        notify(
            id = UPDATE_NOTIFICATION_ID,
            channelId = updatesChannelId,
            title = context.getString(R.string.notification_update_title),
            message = context.resources.getQuantityString(R.plurals.notification_update_description, num, num),
            icon = R.drawable.ic_install,
            inboxLines = lines,
            summaryText = extraSummary
        )
    }

    fun showAutoInstallSuccessNotification(num: Int) = notify(
        id = AUTO_INSTALL_SUCCESS_ID,
        channelId = backgroundChannelId,
        title = context.getString(R.string.notification_auto_install_title),
        message = context.resources.getQuantityString(R.plurals.notification_auto_install_success, num, num),
        icon = R.drawable.ic_install
    )

    fun showAutoInstallFailedNotification(num: Int) = notify(
        id = AUTO_INSTALL_FAILURE_ID,
        channelId = backgroundChannelId,
        title = context.getString(R.string.notification_auto_install_title),
        message = context.resources.getQuantityString(R.plurals.notification_auto_install_failed, num, num),
        icon = R.drawable.ic_install
    )

    fun showNoUpdatesNotification() = notify(
        id = BACKGROUND_STATUS_ID,
        channelId = backgroundChannelId,
        title = context.getString(R.string.notification_no_updates_title),
        message = context.getString(R.string.notification_no_updates_message),
        icon = R.drawable.ic_refresh
    )

    fun showCheckFailedNotification() = notify(
        id = BACKGROUND_STATUS_ID,
        channelId = backgroundChannelId,
        title = context.getString(R.string.notification_check_failed_title),
        message = context.getString(R.string.notification_check_failed_message),
        icon = R.drawable.ic_refresh
    )

    fun showAutoInstallUnavailableNotification() = notify(
        id = AUTO_INSTALL_FAILURE_ID,
        channelId = backgroundChannelId,
        title = context.getString(R.string.notification_auto_install_title),
        message = context.getString(R.string.notification_auto_install_unavailable),
        icon = R.drawable.ic_install
    )

    fun cancelUpdateNotification() {
        NotificationManagerCompat.from(context).cancel(UPDATE_NOTIFICATION_ID)
    }

    fun cancelBackgroundStatusNotification() {
        NotificationManagerCompat.from(context).cancel(BACKGROUND_STATUS_ID)
    }

    fun checkNotificationPermission(launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= 33 && !areNotificationsEnabled()) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun areNotificationsEnabled(): Boolean = NotificationManagerCompat
        .from(context)
        .areNotificationsEnabled()

    fun settingsIntent(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }

    @SuppressLint("MissingPermission")
    private fun notify(
        id: Int,
        channelId: String,
        title: String,
        message: String,
        icon: Int,
        inboxLines: List<String> = emptyList(),
        summaryText: String? = null
    ) {
        if (!areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            action = UpdateAction
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val style: NotificationCompat.Style = if (inboxLines.isNotEmpty()) {
            val inbox = NotificationCompat.InboxStyle()
            inbox.setBigContentTitle(message)
            inboxLines.forEach { line -> inbox.addLine(line) }
            summaryText?.let { summary -> inbox.setSummaryText(summary) }
            inbox
        } else {
            NotificationCompat.BigTextStyle().bigText(message)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val updates = NotificationChannel(
            updatesChannelId,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        val background = NotificationChannel(
            backgroundChannelId,
            context.getString(R.string.notification_background_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_background_channel_description)
        }
        notificationManager.createNotificationChannels(listOf(updates, background))
    }
}
