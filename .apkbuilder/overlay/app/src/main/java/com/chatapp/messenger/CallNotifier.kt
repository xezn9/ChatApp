package com.chatapp.messenger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person

/** إشعار المكالمة الواردة: رنين + شاشة كاملة (حتى مع قفل الشاشة) + أزرار رد/رفض */
object CallNotifier {
    const val CHANNEL = "incoming_call"
    private const val NOTIF_ID = 7701

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "المكالمات الواردة", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "رنين المكالمات الصوتية"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    fun showIncoming(ctx: Context, callId: String, name: String) {
        ensureChannel(ctx)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        fun activityIntent(answer: Boolean) = Intent(ctx, CallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(CallActivity.EXTRA_ANSWER, answer)
        }

        val open = PendingIntent.getActivity(ctx, 1, activityIntent(false), flags)
        val answer = PendingIntent.getActivity(ctx, 2, activityIntent(true), flags)
        val decline = PendingIntent.getBroadcast(
            ctx, 3,
            Intent(ctx, CallActionReceiver::class.java).putExtra("callId", callId),
            flags
        )

        val caller = Person.Builder().setName(name).setImportant(true).build()
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText("مكالمة صوتية واردة")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(CallConfig.RING_TIMEOUT_MS + 5000)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer))
            .build()
        // نكرر الرنين لين يرد أو يرفض
        n.flags = n.flags or Notification.FLAG_INSISTENT
        runCatching {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        }
    }

    fun cancel(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID) }
    }

    private const val GROUP_NOTIF_ID = 7703

    /** رنين مكالمة جماعية: انضمام / تجاهل */
    fun showGroupIncoming(ctx: Context, gid: String, groupName: String, fromName: String) {
        ensureChannel(ctx)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val title = groupName.ifBlank { "مجموعة" }
        val join = PendingIntent.getActivity(
            ctx, 5,
            Intent(ctx, GroupCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(GroupCallActivity.EXTRA_GID, gid)
                putExtra(GroupCallActivity.EXTRA_NAME, groupName)
            },
            flags
        )
        val dismiss = PendingIntent.getBroadcast(
            ctx, 6,
            Intent(ctx, CallActionReceiver::class.java).putExtra("group", true),
            flags
        )
        val caller = Person.Builder().setName(title).setImportant(true).build()
        val text = if (fromName.isBlank()) "مكالمة جماعية" else "$fromName بدأ مكالمة جماعية"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(45_000L)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(join)
            .setFullScreenIntent(join, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, dismiss, join))
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        runCatching {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(ctx).notify(GROUP_NOTIF_ID, n)
        }
    }

    fun cancelGroup(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(GROUP_NOTIF_ID) }
    }
}

/** زر «رفض» بإشعار المكالمة */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.getBooleanExtra("group", false)) { CallNotifier.cancelGroup(ctx); return }
        val id = i.getStringExtra("callId") ?: return
        CallManager.declineById(ctx.applicationContext, id)
    }
}
