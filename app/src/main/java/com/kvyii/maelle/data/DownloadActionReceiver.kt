package com.kvyii.maelle.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kvyii.maelle.MaelleApplication

/**
 * Handles the pause / resume / stop actions tapped on the download
 * notification, forwarding them to the [DownloadManager].
 */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = (context.applicationContext as MaelleApplication).container.downloads
        when (intent.action) {
            ACTION_PAUSE_ALL -> manager.pauseAll()
            ACTION_RESUME_ALL -> manager.resumeAll()
            ACTION_STOP_ALL -> manager.stopAll()
        }
    }

    companion object {
        const val ACTION_PAUSE_ALL = "com.kvyii.maelle.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.kvyii.maelle.action.RESUME_ALL"
        const val ACTION_STOP_ALL = "com.kvyii.maelle.action.STOP_ALL"
    }
}
