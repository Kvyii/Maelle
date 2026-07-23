package com.kvyii.maelle.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.kvyii.maelle.MaelleApplication

/**
 * Foreground service that keeps batch downloads alive while the app is
 * backgrounded and shows the ongoing progress notification. It owns no download
 * logic itself — [DownloadManager] drives it via [start]/[updateNotification]/
 * [stop]. The mandatory foreground notification is what stops Android from
 * killing the work when the app is minimized.
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Show a placeholder immediately so we satisfy the foreground contract,
        // then the manager pushes real progress via updateNotification().
        val app = applicationContext as MaelleApplication
        val notification = app.container.downloads.buildNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}
