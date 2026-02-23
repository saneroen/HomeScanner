package org.nighthawklabs.homescanner.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import java.util.UUID

object ReceiptForeground {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ReceiptWorkConstants.CHANNEL_ID,
                ReceiptWorkConstants.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    fun buildForegroundInfo(
        context: Context,
        workerId: UUID,
        stage: String,
        percent: Int,
        message: String
    ): ForegroundInfo {
        ensureChannel(context)
        val cancelPendingIntent = WorkManager.getInstance(context).createCancelPendingIntent(workerId)
        val notification = NotificationCompat.Builder(context, ReceiptWorkConstants.CHANNEL_ID)
            .setContentTitle("Processing receipt")
            .setContentText(message.ifEmpty { stage })
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(
                ReceiptWorkConstants.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(ReceiptWorkConstants.NOTIFICATION_ID, notification)
        }
    }
}
