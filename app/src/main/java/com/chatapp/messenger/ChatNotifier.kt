package com.chatapp.messenger

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** يبني إشعار الرسالة (اسم المرسل + صورته + النص). تستخدمه خدمة الاستماع بالخلفية. */
object ChatNotifier {

    private class Info(val name: String, val avatar: Bitmap?, val at: Long)

    private val cache = ConcurrentHashMap<String, Info>()
    private const val TTL = 10 * 60 * 1000L

    private suspend fun info(from: String): Info {
        cache[from]?.let { if (System.currentTimeMillis() - it.at < TTL) return it }
        var name = ""
        var avatar: Bitmap? = null
        var got = false
        try {
            val doc = withTimeoutOrNull(8000) {
                FirebaseFirestore.getInstance().collection("profiles").document(from).get().await()
            }
            if (doc != null) {
                got = true
                name = doc.getString("name").orEmpty()
                val b64 = doc.getString("avatar").orEmpty()
                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    avatar = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { circle(it, 160) }
                }
            }
            if (name.isBlank()) {
                val u = withTimeoutOrNull(5000) {
                    FirebaseFirestore.getInstance().collection("users").document(from).get().await()
                }
                name = u?.getString("name").orEmpty()
            }
        } catch (_: Exception) {
        }
        val i = Info(name.ifBlank { "مستخدم" }, avatar, System.currentTimeMillis())
        if (got) cache[from] = i          // ما نخزن نتيجة فاشلة
        return i
    }

    suspend fun show(ctx: Context, from: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val info = info(from)
        val sender = Person.Builder().setName(info.name).setKey(from)
            .apply { info.avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()

        val id = from.hashCode()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // إذا فيه إشعار قديم من نفس الشخص نضيف عليه (رسائل متعددة بإشعار واحد)
        val style = nm.activeNotifications.firstOrNull { it.id == id }?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(Person.Builder().setName("أنا").build())
        style.addMessage(text, System.currentTimeMillis(), sender)

        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ChatMessagingService.EXTRA_OPEN_CHAT, from)
        }
        val pi = PendingIntent.getActivity(
            ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, ChatApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.notification_accent))
            .setContentTitle(info.name)
            .setContentText(text)
            .setLargeIcon(info.avatar)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    // ------------------------------------------------------------ رسائل المجموعات

    private class GroupInfo(val name: String, val avatar: Bitmap?, val at: Long)

    private val groupCache = ConcurrentHashMap<String, GroupInfo>()
    private val groupLock = Mutex()
    private val shown = ConcurrentHashMap<String, Long>()
    private val cursorLock = Any()

    /**
     * true إذا هذي أول مرة نشوف الرسالة. نفس الرسالة ممكن توصل من FCM ومن الخدمة الاحتياطية،
     * فالي يسبق يطلع الإشعار والثاني يتجاهلها (بدون تكرار).
     */
    fun claim(key: String): Boolean {
        val now = System.currentTimeMillis()
        if (shown.size > 500) {
            val it = shown.entries.iterator()
            while (it.hasNext()) if (now - it.next().value > TTL) it.remove()
        }
        return shown.putIfAbsent(key, now) == null
    }

    /** يرفع مؤشر آخر رسالة معالجة للمجموعة (نفس مؤشر الخدمة الاحتياطية). true إذا كانت الرسالة أحدث من المؤشر. */
    fun advanceGroupCursor(ctx: Context, gid: String, createdAt: Long): Boolean {
        val p = ctx.getSharedPreferences("notif_cursor_rt", Context.MODE_PRIVATE)
        synchronized(cursorLock) {
            if (createdAt <= p.getLong("g_$gid", 0L)) return false
            p.edit().putLong("g_$gid", createdAt).apply()
            return true
        }
    }

    private suspend fun groupInfo(gid: String, hint: String): GroupInfo {
        groupCache[gid]?.let { if (System.currentTimeMillis() - it.at < TTL) return it }
        var name = ""
        var avatar: Bitmap? = null
        var got = false
        try {
            val meta = withTimeoutOrNull(5000) {
                Repo.rtdb.getReference("groups/$gid/meta").get().await()
            }
            if (meta != null && meta.exists()) {
                got = true
                name = meta.child("name").value as? String ?: ""
                val b64 = meta.child("avatar").value as? String ?: ""
                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    avatar = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { circle(it, 160) }
                }
            }
        } catch (_: Exception) {
        }
        val i = GroupInfo(name.ifBlank { hint }.ifBlank { "مجموعة" }, avatar, System.currentTimeMillis())
        if (got) groupCache[gid] = i
        return i
    }

    /** إشعار رسالة مجموعة: عنوانه اسم المجموعة، وكل رسالة باسم وصورة مرسلها. الرسائل المتعددة تتجمع بإشعار واحد. */
    suspend fun showGroup(ctx: Context, gid: String, groupHint: String, from: String, fromHint: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val (si, g) = coroutineScope {
            val a = async { info(from) }
            val b = async { groupInfo(gid, groupHint) }
            a.await() to b.await()
        }
        val senderName = if (si.name == "مستخدم" && fromHint.isNotBlank()) fromHint else si.name
        val sender = Person.Builder().setName(senderName).setKey(from)
            .apply { si.avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()

        val id = ("g:" + gid).hashCode()
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ChatMessagingService.EXTRA_OPEN_CHAT, "group:$gid")
        }
        val pi = PendingIntent.getActivity(
            ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // قفل: لو وصلت عدة رسائل مع بعض ما نضيع وحدة بسبب قراءة/كتابة الإشعار القديم
        groupLock.withLock {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val style = nm.activeNotifications.firstOrNull { it.id == id }?.notification
                ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
                ?: NotificationCompat.MessagingStyle(Person.Builder().setName("أنا").build())
            style.conversationTitle = g.name
            style.isGroupConversation = true
            style.addMessage(text, System.currentTimeMillis(), sender)

            val n = NotificationCompat.Builder(ctx, ChatApp.CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.notification_accent))
                .setContentTitle(g.name)
                .setContentText("$senderName: $text")
                .setLargeIcon(g.avatar ?: si.avatar)
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(ctx).notify(id, n)
        }
    }

    private fun circle(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val side = minOf(src.width, src.height)
        val r = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        c.drawBitmap(src, r, Rect(0, 0, size, size), p)
        return out
    }
}
