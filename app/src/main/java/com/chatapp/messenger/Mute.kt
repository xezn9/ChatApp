package com.chatapp.messenger

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * كتم إشعارات المحادثات. محلي على الجهاز (الإشعارات أصلاً تنبني محلياً من خدمة الاستماع / FCM).
 * المكتوم يبقى يوصله رسائل وعداد غير مقروء عادي، بس ما يطلع له إشعار.
 * المفتاح "<myUid>:<otherUid>" حتى كل حساب على نفس الجهاز له كتمه.
 */
object MuteStore {
    private const val PREFS = "chat_mutes"
    private const val KEY = "set"
    private val _muted = MutableStateFlow<Set<String>>(emptySet())
    val muted: StateFlow<Set<String>> = _muted
    @Volatile private var loaded = false

    private fun key(me: String, other: String) = "$me:$other"

    fun init(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _muted.value = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
            loaded = true
        }
    }

    fun isMuted(ctx: Context, other: String): Boolean {
        val me = Repo.auth.currentUser?.uid ?: return false
        init(ctx)
        return key(me, other) in _muted.value
    }

    fun setMuted(ctx: Context, other: String, muted: Boolean) = setMany(ctx, listOf(other), muted)

    fun setMany(ctx: Context, others: Collection<String>, muted: Boolean) {
        val me = Repo.auth.currentUser?.uid ?: return
        init(ctx)
        synchronized(this) {
            val next = _muted.value.toMutableSet()
            others.forEach { if (muted) next.add(key(me, it)) else next.remove(key(me, it)) }
            _muted.value = next
            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY, next).apply()
        }
    }
}
