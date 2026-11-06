package com.chatapp.messenger

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * علامات محلية على المحادثات (على الجهاز): تمييز بنجمة / تقييد.
 * المفتاح "<myUid>:<otherUid>" حتى كل حساب على نفس الجهاز له علاماته.
 */
class ChatFlagStore(private val prefsName: String) {
    private val _set = MutableStateFlow<Set<String>>(emptySet())
    val flags: StateFlow<Set<String>> = _set
    @Volatile private var loaded = false

    private fun key(me: String, other: String) = "$me:$other"

    fun init(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _set.value = ctx.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .getStringSet("set", emptySet())?.toSet() ?: emptySet()
            loaded = true
        }
    }

    fun has(ctx: Context, other: String): Boolean {
        val me = Repo.auth.currentUser?.uid ?: return false
        init(ctx)
        return key(me, other) in _set.value
    }

    fun set(ctx: Context, other: String, on: Boolean) {
        val me = Repo.auth.currentUser?.uid ?: return
        init(ctx)
        synchronized(this) {
            val next = _set.value.toMutableSet()
            if (on) next.add(key(me, other)) else next.remove(key(me, other))
            _set.value = next
            ctx.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit().putStringSet("set", next).apply()
        }
    }
}

/** تمييز محادثة بنجمة (محلي) */
object StarStore {
    val store = ChatFlagStore("chat_stars")
}

/** تقييد محادثة (محلي): بدون إشعارات منها، وتنعلّم بالقائمة */
object RestrictStore {
    val store = ChatFlagStore("chat_restricts")
}
