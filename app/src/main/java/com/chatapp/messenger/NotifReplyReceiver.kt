package com.chatapp.messenger

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** الرد على رسالة (محادثة أو مجموعة) مباشرة من الإشعار بدون فتح التطبيق */
class NotifReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val target = intent.getStringExtra(EXTRA_TARGET) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_TEXT)?.toString()?.trim().orEmpty()
        val me = Repo.auth.currentUser?.uid

        if (me == null) {
            NotificationManagerCompat.from(app).cancel(notifId)
            return
        }
        if (text.isEmpty()) {
            refresh(app, notifId, null, kind, target)
            return
        }

        val pending = goAsync()
        Repo.appScope.launch {
            try {
                if (kind == KIND_GROUP) {
                    Repo.sendGroupMessage(target, text, onFail = {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(app, "تعذر إرسال الرد", Toast.LENGTH_SHORT).show()
                        }
                    })
                    // الرد = قرأت المجموعة: نرفع مؤشر القراءة حتى تروح الشارة
                    try {
                        val last = withTimeoutOrNull(5000) {
                            Repo.rtdb.getReference("groupChats/$target/messages")
                                .orderByChild("createdAt").limitToLast(1).get().await()
                        }
                        val t = (last?.children?.firstOrNull()?.child("createdAt")?.value as? Number)?.toLong()
                        if (t != null) GroupReads.set(app, me, target, t)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }
                } else {
                    Repo.send(me, target, text)
                    Repo.markRead(me, target)
                    delay(3000)   // نعطي الكتابة وقت توصل للسيرفر قبل ما ينتهي الريسيفر
                }
            } finally {
                refresh(app, notifId, text, kind, target)
                pending.finish()
            }
        }
    }

    /** يعيد نشر الإشعار مع ردي (أو بدونه) حتى يوقف دوران "جاري الإرسال" */
    @SuppressLint("MissingPermission")
    private fun refresh(ctx: Context, notifId: Int, myText: String?, kind: String, target: String) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val old = nm.activeNotifications.firstOrNull { it.id == notifId }?.notification ?: return
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(old) ?: return
            if (myText != null) style.addMessage(myText, System.currentTimeMillis(), null as Person?)

            val ex = old.extras
            val avatar = try {
                (old.getLargeIcon()?.loadDrawable(ctx) as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } catch (_: Exception) {
                null
            }
            val n = NotificationCompat.Builder(ctx, old.channelId ?: ChatApp.CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.notification_accent))
                .setContentTitle(ex.getCharSequence(NotificationCompat.EXTRA_TITLE))
                .setContentText(ex.getCharSequence(NotificationCompat.EXTRA_TEXT))
                .setLargeIcon(avatar)
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(old.contentIntent)
                .addAction(ChatNotifier.replyAction(ctx, kind, target, notifId))
                .build()
            NotificationManagerCompat.from(ctx).notify(notifId, n)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val KEY_TEXT = "reply_text"
        const val EXTRA_KIND = "reply_kind"
        const val EXTRA_TARGET = "reply_target"
        const val EXTRA_NOTIF_ID = "reply_notif_id"
        const val KIND_CHAT = "chat"
        const val KIND_GROUP = "group"
    }
}
