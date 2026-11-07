package com.chatapp.messenger

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * مؤشر القراءة لكل مجموعة (محلي على الجهاز): وقت آخر رسالة قريتها.
 * الرسائل الي وقتها أحدث من المؤشر ومن غيري = "غير مقروءة" وتنعلّم بنقطة وفاصل "رسائل جديدة".
 */
object GroupReads {
    private const val FILE = "group_reads"

    private val _version = MutableStateFlow(0)
    /** يتغير كل ما تتحدث مؤشرات القراءة (حتى يتحدث عدّاد غير المقروء فوراً) */
    val version: StateFlow<Int> = _version

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
            _version.value = _version.value + 1
        }
    }

    /** أول مرة نشوف المجموعة (بالخلفية): الرسائل الجاية بعد هذي اللحظة تنعدّ غير مقروءة */
    @Synchronized
    fun seed(ctx: Context, uid: String, gid: String, createdAt: Long) {
        val p = prefs(ctx)
        if (!p.contains(key(uid, gid))) {
            p.edit().putLong(key(uid, gid), createdAt).apply()
            _version.value = _version.value + 1
        }
    }
}
