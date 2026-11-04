package com.chatapp.messenger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * إعدادات الخصوصية حقتي (مخزنة بـ Realtime Database بمسار privacy/<uid>).
 * - hideOnline:   أخفي حالة اتصالي، وأنا بعد ما أشوف حالة اتصال الناس
 * - hideRead:     ما أرسل "مقروء" للناس، وأنا بعد ما أشوف "مقروء" على رسائلي
 * - hideLastSeen: أخفي وقت آخر ظهوري، وأنا بعد ما أشوف آخر ظهور الناس
 * - msgReq:       أي شخص جديد يراسلني تصير رسالته "طلب رسالة" لين أوافق عليه
 */
data class Privacy(
    val hideOnline: Boolean = false,
    val hideRead: Boolean = false,
    val hideLastSeen: Boolean = false,
    val msgReq: Boolean = false
)

/** النسخة الحية من الإعدادات (تتحدث من MainScreen وتنقرأ من أي مكان بالتطبيق) */
object PrivacyState {
    var value: Privacy by mutableStateOf(Privacy())
}
