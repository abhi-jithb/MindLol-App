package com.abhijith.mindlol.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.abhijith.mindlol.MainActivity
import com.abhijith.mindlol.R
import com.abhijith.mindlol.domain.WarningsAccess
import com.abhijith.mindlol.domain.WarningsPermission

/**
 * Warning notifications (Step 3, W3). One channel, "Warnings":
 * - HIGH importance, so it shows as a heads-up banner while you're in an app,
 * - no sound and no vibration (silent heads-up).
 *
 * Each notification removes itself at the lock time (setTimeoutAfter), so an old "15 min" never
 * stays in the shade. Nothing here throws: a failure is logged.
 */
class WarningNotifier(context: Context) {

    private val app = context.applicationContext
    private val manager = NotificationManagerCompat.from(app)

    /** Creates the channel once (Android keeps it; creating again is a no-op). */
    fun ensureChannel() {
        try {
            val channel = NotificationChannel(CHANNEL_ID, "Warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Before a limit or bedtime locks an app"
                setSound(null, null)
                enableVibration(false)
                vibrationPattern = null
            }
            app.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.w(TAG, "Warnings: couldn't create the channel", e)
        }
    }

    fun access(): WarningsAccess {
        val granted = Build.VERSION.SDK_INT < WarningsPermission.RUNTIME_PERMISSION_SDK ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channel = app.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_ID)
        val channelEnabled = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
        return WarningsPermission.access(Build.VERSION.SDK_INT, granted, manager.areNotificationsEnabled(), channelEnabled)
    }

    /**
     * Shows [text] under [id] (one id per kind, so a newer warning replaces an older one),
     * removing itself after [timeoutMillis]. Returns false if it couldn't be shown.
     */
    fun show(id: Int, text: String, timeoutMillis: Long): Boolean {
        if (access() != WarningsAccess.ALLOWED) {
            Log.w(TAG, "Warnings: not allowed, skipped \"$text\"")
            return false
        }
        ensureChannel()
        val open = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_warning)
            .setContentTitle("MindLol")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setTimeoutAfter(timeoutMillis.coerceAtLeast(1_000L))
            .build()
        return try {
            manager.notify(id, notification)
            Log.w(TAG, "Warnings: notified \"$text\"")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Warnings: permission missing", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Warnings: couldn't notify", e)
            false
        }
    }

    companion object {
        const val CHANNEL_ID = "warnings"
        const val ID_BEDTIME = 1001
        const val ID_LIMIT = 1002
        private const val TAG = "LiveLock"
    }
}
