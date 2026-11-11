package com.chatapp.messenger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** يمرر طلب فتح محادثة (من ضغطة الإشعار) من MainActivity إلى MainScreen */
object Nav {
    var openChatUid by mutableStateOf<String?>(null)

    /** فتح بروفايل مستخدم (من مسح الباركود / رابط الحساب) */
    var openProfileUid by mutableStateOf<String?>(null)
}
