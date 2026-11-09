package com.chatapp.messenger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.chatapp.messenger.GroupReads
import com.chatapp.messenger.Repo
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/** رسالة مجموعة مختصرة للعد: (وقتها، المرسل، محذوفة؟) */
private data class UnreadMsg(val at: Long, val from: String, val deleted: Boolean)

private const val MAX_GROUPS = 100
private const val WINDOW = 100   // نعد من آخر 100 رسالة بكل مجموعة

/**
 * مجموع الرسائل غير المقروءة بكل مجموعاتي (للشارة على تبويب Groups).
 * يسمع لقائمة مجموعاتي ولآخر رسائل كل مجموعة، ويقارنها بمؤشر القراءة المحفوظ بالجهاز ([GroupReads]).
 * مو بس لما تفتح تبويب Groups: يشتغل طول ما الشاشة الرئيسية موجودة.
 */
@Composable
fun rememberGroupUnreadTotal(me: String): Int {
    val ctx = LocalContext.current.applicationContext
    val recent = remember(me) { mutableStateMapOf<String, List<UnreadMsg>>() }

    DisposableEffect(me) {
        if (me.isBlank()) return@DisposableEffect onDispose { }
        val queries = HashMap<String, Pair<Query, ValueEventListener>>()
        val groupsRef = Repo.rtdb.getReference("userGroups/$me")
        val groupsListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val ids = snap.children.mapNotNull { it.key }.take(MAX_GROUPS).toSet()
                // مجموعات انحذفت/طلعت منها
                queries.keys.filter { it !in ids }.forEach { gid ->
                    queries.remove(gid)?.let { (q, l) -> q.removeEventListener(l) }
                    recent.remove(gid)
                }
                for (gid in ids) {
                    if (gid in queries) continue
                    val q = Repo.rtdb.getReference("groupChats/$gid/messages").orderByChild("createdAt").limitToLast(WINDOW)
                    val l = object : ValueEventListener {
                        override fun onDataChange(s: DataSnapshot) {
                            val list = s.children.mapNotNull { c ->
                                val at = (c.child("createdAt").value as? Number)?.toLong() ?: return@mapNotNull null
                                UnreadMsg(at, c.child("from").value as? String ?: "", c.child("deleted").value == true)
                            }
                            // أول مرة نشوف المجموعة بهذا الجهاز: التاريخ القديم يعتبر مقروء (نفس منطق شاشة المجموعة)
                            GroupReads.seed(ctx, me, gid, list.maxOfOrNull { it.at } ?: 0L)
                            recent[gid] = list
                        }

                        override fun onCancelled(e: DatabaseError) {}
                    }
                    queries[gid] = q to l
                    q.addValueEventListener(l)
                }
            }

            override fun onCancelled(e: DatabaseError) {}
        }
        groupsRef.addValueEventListener(groupsListener)
        onDispose {
            groupsRef.removeEventListener(groupsListener)
            queries.values.forEach { (q, l) -> q.removeEventListener(l) }
            queries.clear()
            recent.clear()
        }
    }

    val total by remember(me) {
        derivedStateOf {
            GroupReads.version   // يتحدث لما ينقرأ شي
            var n = 0
            for ((gid, list) in recent) {
                val cursor = GroupReads.get(ctx, me, gid)
                if (cursor < 0) continue
                n += list.count { it.from != me && !it.deleted && it.at > cursor }
            }
            n
        }
    }
    return total
}
