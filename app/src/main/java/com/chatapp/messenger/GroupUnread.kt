package com.chatapp.messenger

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * عدد رسائل المجموعات غير المقروءة (للشارة على تبويبات Home و Groups).
 * الرسالة غير مقروءة إذا: من غيري + وقتها أحدث من مؤشر القراءة المحلي (GroupReads).
 */
object GroupUnread {

    /** مجموع غير المقروء بكل مجموعاتي (يتحدث فوراً) */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun totalFlow(ctx: Context, me: String): Flow<Int> {
        if (me.isBlank()) return flowOf(0)
        val app = ctx.applicationContext
        return groupIdsFlow(me).distinctUntilChanged().flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(0)
            else combine(ids.map { countFlow(app, me, it) }) { arr -> arr.sum() }
        }
    }

    private fun groupIdsFlow(me: String): Flow<List<String>> = callbackFlow {
        val ref = Repo.rtdb.getReference("userGroups/$me")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { trySend(s.children.mapNotNull { it.key }.sorted()) }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun countFlow(ctx: Context, me: String, gid: String): Flow<Int> =
        GroupReads.version.flatMapLatest {
            callbackFlow<Int> {
                val msgs = Repo.rtdb.getReference("groupChats/$gid/messages")
                var cursor = GroupReads.get(ctx, me, gid)
                if (cursor < 0) {
                    // أول مرة نشوف المجموعة بهذا الجهاز: التاريخ القديم يعتبر مقروء
                    try {
                        val last = withTimeoutOrNull(8000) {
                            msgs.orderByChild("createdAt").limitToLast(1).get().await()
                        }
                        val t = (last?.children?.firstOrNull()?.child("createdAt")?.value as? Number)?.toLong() ?: 0L
                        GroupReads.seed(ctx, me, gid, t)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }
                    cursor = GroupReads.get(ctx, me, gid)
                }
                if (cursor < 0) {
                    trySend(0)
                    awaitClose { }
                    return@callbackFlow
                }
                val q = msgs.orderByChild("createdAt").startAt((cursor + 1).toDouble()).limitToLast(100)
                val l = object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        val n = s.children.count { c ->
                            (c.child("from").value as? String).let { it != null && it != me } &&
                                c.child("deleted").value != true
                        }
                        trySend(n)
                    }
                    override fun onCancelled(e: DatabaseError) { trySend(0) }
                }
                q.addValueEventListener(l)
                awaitClose { q.removeEventListener(l) }
            }
        }
}
