package com.chatapp.messenger

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * مؤشر القراءة لكل مجموعة (محلي على الجهاز): وقت آخر رسالة قريتها.
 * الرسائل الي وقتها أحدث من المؤشر ومن غيري = "غير مقروءة" وتنعلّم بنقطة وفاصل "رسائل جديدة".
 */
object GroupReads {
    private const val FILE = "group_reads"

    /** يزيد كل ما تغيّر أي مؤشر قراءة (حتى شارة تبويب Groups تتحدث فوراً) */
    var version by mutableIntStateOf(0)
        private set

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun key(uid: String, gid: String) = "r_${uid}_$gid"

    /** آخر وقت قراءة، أو -1 إذا ما افتحت هذي المجموعة قبل على هذا الجهاز */
    fun get(ctx: Context, uid: String, gid: String): Long = prefs(ctx).getLong(key(uid, gid), -1L)

    /** يرفع المؤشر (ما ينزل أبداً) */
    @Synchronized
    fun set(ctx: Context, uid: String, gid: String, createdAt: Long) {
        val p = prefs(ctx)
        if (createdAt > p.getLong(key(uid, gid), -1L)) {
            p.edit().putLong(key(uid, gid), createdAt).apply()
            version++
        }
    }

    /** أول مرة نشوف المجموعة (بالخلفية): الرسائل الجاية بعد هذي اللحظة تنعدّ غير مقروءة */
    @Synchronized
    fun seed(ctx: Context, uid: String, gid: String, createdAt: Long) {
        val p = prefs(ctx)
        if (!p.contains(key(uid, gid))) {
            p.edit().putLong(key(uid, gid), createdAt).apply()
            version++
        }
    }
}
