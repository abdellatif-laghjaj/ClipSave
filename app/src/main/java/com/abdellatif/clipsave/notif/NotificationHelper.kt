package com.abdellatif.clipsave.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.abdellatif.clipsave.R
import com.abdellatif.clipsave.download.DownloadService

object NotificationHelper {

    const val CHANNEL_ID = "clipsave_downloads"
    const val FOREGROUND_ID = 1001
    private const val DONE_BASE = 2000

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "ClipSave download progress and results" }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun launchIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun progressNotification(
        context: Context,
        title: String,
        progress: Int,
        downloadId: String?
    ): android.app.Notification {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading")
            .setContentText(title.ifBlank { "Media" })
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launchIntent(context))
        if (progress in 0..100) builder.setProgress(100, progress, progress == 0)
        else builder.setProgress(0, 0, true)
        if (downloadId != null) {
            val pauseIntent = Intent(context, DownloadService::class.java)
                .setAction(DownloadService.ACTION_PAUSE)
                .putExtra(DownloadService.EXTRA_DOWNLOAD_ID, downloadId)
            val pauseAction = PendingIntent.getService(
                context,
                downloadId.hashCode(),
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseAction)
        }
        return builder.build()
    }

    fun notifyDone(context: Context, id: Int, title: String, success: Boolean, detail: String) {
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (success) "Download complete" else "Download failed")
            .setContentText(title.ifBlank { detail })
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setContentIntent(launchIntent(context))
            .build()
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            runCatching { NotificationManagerCompat.from(context).notify(DONE_BASE + id, n) }
        }
    }
}
