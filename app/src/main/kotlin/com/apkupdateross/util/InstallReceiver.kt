package com.apkupdateross.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.apkupdateross.data.ui.AppInstallStatus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class InstallReceiver : BroadcastReceiver(), KoinComponent {

    private val installLog: InstallLog by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val id = intent.getIntExtra(SessionInstaller.INSTALL_ID, -1)
        
        Log.d("InstallReceiver", "Received install status: $status for id: $id")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmationIntent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                installLog.emitStatus(AppInstallStatus(true, id, true))
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e("InstallReceiver", "Installation failed: $message ($status)")
                installLog.emitStatus(AppInstallStatus(false, id, true))
            }
        }
    }
}
