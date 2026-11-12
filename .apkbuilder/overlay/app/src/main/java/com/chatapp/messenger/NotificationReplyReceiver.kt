package com.chatapp.messenger

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * يستقبل الرد السريع من الإشعار (زر "رد").
 * - محادثة فردية: key = uid الطرف الثاني  → Repo.send
 * - مجموعة:        key = "group:<gid>"      → Repo.sendGroupMessage
 *
 * بعد الإرسال يحدّث نفس الإشعار ويضيف ردي بالمحادثة (حتى يختفي مؤشر التحميل).
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_TEXT)?.toString()?.trim().orEmpty()
        val app = context.applicationContext
        val me = Repo.auth.currentUser?.uid

        // ما فيه نص أو ما فيه حساب: بس نرجّع الإشعار لحالته (يطفّي مؤشر التحميل)
        if (text.isEmpty() || me == null) {
            refreshNotification(app, key, notifId, null)
            if (me == null) toast(app, "سجّل دخول أول")
            return
        }

        // نبقي الـ Receiver شغال لين توصل الرسالة للسيرفر (وإلا النظام ممكن يقتل العملية قبلها)
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        fun finish(ok: Boolean) {
            if (!finished.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            if (!ok) toast(app, "ما انرسل الرد، جرّب مرة ثانية")
            pending.finish()
        }
        handler.postDelayed({ finish(true) }, 8000)   // حد أقصى للانتظار

        // يظهر ردي بالإشعار فوراً
        refreshNotification(app, key, notifId, text)

        try {
            if (key.startsWith("group:")) {
                val gid = key.removePrefix("group:")
                Repo.sendGroupMessage(
                    gid, text,
                    onFail = { finish(false) },
                    onSent = { finish(true) }
                )
            } else {
                Repo.send(me, key, text, onResult = { ok ->
                    if (ok) Repo.markRead(me, key)   // رديت = قريت المحادثة (يحترم إعداد "إخفاء حالة مقروء")
                    finish(ok)
                })
            }
        } catch (_: Exception) {
            finish(false)
        }
    }

    /** يعيد نشر نفس الإشعار مع إضافة ردي (إذا [reply] مو null) بدون صوت أو اهتزاز */
    private fun refreshNotification(ctx: Context, key: String, id: Int, reply: String?) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val old = nm.activeNotifications.firstOrNull { it.id == id }?.notification ?: return
            val style = NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(old) ?: return
            if (reply != null) style.addMessage(reply, System.currentTimeMillis(), style.user)
            val extras = old.extras
            val n = NotificationCompat.Builder(ctx, PigeonApp.CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(ctx, R.color.notification_accent))
                .setContentTitle(extras.getCharSequence(Notification.EXTRA_TITLE))
                .setContentText(reply ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                .setLargeIcon(ChatNotifier.largeIcons[id])
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)          // بدون صوت/اهتزاز مرة ثانية
                .setAutoCancel(true)
                .setContentIntent(old.contentIntent)
                .addAction(ChatNotifier.replyAction(ctx, key, id))
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: Exception) {
        }
    }

    private fun toast(ctx: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_REPLY = "com.chatapp.messenger.ACTION_NOTIFICATION_REPLY"
        const val KEY_TEXT = "reply_text"
        const val EXTRA_KEY = "reply_chat_key"
        const val EXTRA_NOTIF_ID = "reply_notif_id"
    }
}
