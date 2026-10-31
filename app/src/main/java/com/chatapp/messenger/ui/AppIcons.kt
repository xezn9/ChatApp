package com.chatapp.messenger.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** أيقونات إضافية (غير موجودة بمكتبة icons-core) مرسومة كـ Vector */
object AppIcons {
    private fun icon(name: String, path: String, evenOdd: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            fill = SolidColor(Color.Black)
        ).build()

    /** صورة / خلفية */
    val Image: ImageVector by lazy {
        icon(
            "Image",
            "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z",
            evenOdd = true
        )
    }

    /** فقاعة محادثة */
    val ChatBubble: ImageVector by lazy {
        icon("ChatBubble", "M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z")
    }

    /** حظر */
    val Block: ImageVector by lazy {
        icon(
            "Block",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM4,12c0,-4.42 3.58,-8 8,-8 1.85,0 3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12zM12,20c-1.85,0 -3.55,-0.63 -4.9,-1.69L18.31,7.1C19.37,8.45 20,10.15 20,12c0,4.42 -3.58,8 -8,8z",
            evenOdd = true
        )
    }

    /** قفل */
    val Lock: ImageVector by lazy {
        icon(
            "Lock",
            "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z",
            evenOdd = true
        )
    }

    /** كرة أرضية (قصة عامة) */
    val Public: ImageVector by lazy {
        icon(
            "Public",
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM11,19.93C7.05,19.44 4,16.08 4,12c0,-0.62 0.08,-1.21 0.21,-1.79L9,15v1c0,1.1 0.9,2 2,2v1.93zM17.9,17.39C17.64,16.58 16.9,16 16,16h-1v-3c0,-0.55 -0.45,-1 -1,-1H8v-2h2c0.55,0 1,-0.45 1,-1V7h2c1.1,0 2,-0.9 2,-2v-0.41C17.92,5.77 20,8.65 20,12c0,2.08 -0.81,3.98 -2.1,5.39z"
        )
    }

    /** بريد */
    val Email: ImageVector by lazy {
        icon(
            "Email",
            "M20,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM20,8l-8,5 -8,-5V6l8,5 8,-5v2z",
            evenOdd = true
        )
    }

    /** عين (إظهار كلمة المرور) */
    val Eye: ImageVector by lazy {
        icon(
            "Eye",
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
            evenOdd = true
        )
    }

    /** تنزيل */
    val Download: ImageVector by lazy {
        icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
    }

    /** رد على رسالة */
    val Reply: ImageVector by lazy {
        icon("Reply", "M10,9V5l-7,7 7,7v-4.1c5,0 8.5,1.6 11,5.1 -1,-5 -4,-10 -11,-11z")
    }

    /** دبوس التثبيت */
    val Pin: ImageVector by lazy {
        icon(
            "Pin",
            "M16,9V4l1,0c0.55,0 1,-0.45 1,-1v0c0,-0.55 -0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3v0c0,0.55 0.45,1 1,1l1,0v5c0,1.66 -1.34,3 -3,3h0v2h5.97v7l1,1l1,-1v-7H19v-2h0C17.34,12 16,10.66 16,9z"
        )
    }

    /** مايك */
    val Mic: ImageVector by lazy {
        icon(
            "Mic",
            "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z"
        )
    }

    /** إيقاف مؤقت */
    val Pause: ImageVector by lazy {
        icon("Pause", "M6,19h4L10,5L6,5v14zM14,5v14h4L18,5h-4z")
    }

    /** تسجيل الخروج */
    val Logout: ImageVector by lazy {
        icon(
            "Logout",
            "M10.09,15.59L11.5,17l5,-5 -5,-5 -1.41,1.41L12.67,11H3v2h9.67l-2.58,2.59zM19,3H5c-1.11,0 -2,0.9 -2,2v4h2V5h14v14H5v-4H3v4c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.89,-2 -2,-2z"
        )
    }

    /** إنشاء مجموعة (أشخاص + إضافة) */
    val GroupAdd: ImageVector by lazy {
        icon(
            "GroupAdd",
            "M8,10H5V7H3v3H0v2h3v3h2v-3h3V10zM18,11c1.66,0 2.99,-1.34 2.99,-3S19.66,5 18,5c-0.32,0 -0.63,0.05 -0.91,0.14C17.66,5.86 18,6.89 18,8s-0.34,2.14 -0.91,2.86C17.37,10.95 17.68,11 18,11zM13,11c1.66,0 2.99,-1.34 2.99,-3S14.66,5 13,5c-1.66,0 -3,1.34 -3,3S11.34,11 13,11zM13,13c-2,0 -6,1 -6,3v2h12v-2C19,14 15,13 13,13zM18.62,13.16C19.4,13.74 20,14.51 20,16v2h4v-2C24,14.46 21.37,13.5 18.62,13.16z"
        )
    }
}
