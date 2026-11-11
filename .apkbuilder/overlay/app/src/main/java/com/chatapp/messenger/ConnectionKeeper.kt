package com.chatapp.messenger

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network

/**
 * يخلي اتصال Realtime Database "حي" فعلاً.
 * بعد ما الجهاز ينام (Doze) أو يتغير النت (واي فاي ↔ بيانات) الاتصال ممكن يبقى ميت نص دقيقة أو أكثر
 * قبل ما الـ SDK يكتشف، ومن هنا تجي "الرسائل ما تبين إلا بعد فترة".
 * هنا نعيد الاتصال فوراً: لما يرجع المستخدم للتطبيق بعد غياب، ولما يرجع/يتبدل النت.
 */
object ConnectionKeeper {
    @Volatile private var backgroundAt = 0L
    @Volatile private var lastReconnect = 0L
    @Volatile private var installed = false
    @Volatile private var firstCallback = true

    fun install(app: Application) {
        if (installed) return
        installed = true
        try {
            val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // أول استدعاء يجي مباشرة عند التسجيل (النت موجود أصلاً): نتجاهله
                    if (firstCallback) { firstCallback = false; return }
                    reconnect()
                }
            })
        } catch (_: Exception) {
        }
    }

    /** التطبيق انفتح: نتأكد الاتصال شغال، وإذا غبنا أكثر من 20 ثانية نعيده من الصفر */
    fun onForeground() {
        val uid = Repo.auth.currentUser?.uid ?: return
        try {
            // قائمة المحادثات تبقى متزامنة دائماً (تبين فوراً من الكاش وتتحدث بدون انتظار)
            Repo.rtdb.getReference("userChats/$uid").keepSynced(true)
            val away = backgroundAt
            if (away != 0L && System.currentTimeMillis() - away > 20_000L) reconnect() else Repo.rtdb.goOnline()
        } catch (_: Exception) {
        }
    }

    fun onBackground() {
        backgroundAt = System.currentTimeMillis()
    }

    private fun reconnect() {
        if (Repo.auth.currentUser == null) return
        val now = System.currentTimeMillis()
        if (now - lastReconnect < 10_000L) return     // ما نكرر إذا النت يتقطع ويرجع بسرعة
        lastReconnect = now
        try {
            val db = Repo.rtdb
            db.goOffline()
            db.goOnline()
        } catch (_: Exception) {
        }
    }
}
