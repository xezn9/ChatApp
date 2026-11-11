package com.chatapp.messenger.ui

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.chatapp.messenger.AppState
import com.chatapp.messenger.ChatApp
import com.chatapp.messenger.MessageListenerService
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.Backdrop

/** حالة نافذة "فحص الإشعارات": تنفتح من الإعدادات أو تلقائياً إذا فيه مشكلة تمنع وصول الإشعارات */
object NotifHealthUi {
    var show by mutableStateOf(false)
}

/**
 * فحص كل الأسباب الشائعة الي تمنع الإشعارات توصل والتطبيق مسكّر، مع فتح الإعداد الصحيح لكل واحد.
 * أجهزة (أوبو/ريلمي/شاومي/فيفو/هواوي/سامسونگ...) تقتل التطبيقات بالخلفية حتى لو الكود صح،
 * فهذا الفحص هو الحل الوحيد لهالجزء.
 */
object NotifHealth {

    class Status(
        val notifications: Boolean,   // إشعارات التطبيق مفعّلة (وصلاحية أندرويد 13+)
        val channel: Boolean,         // قناة "الرسائل" مو مطفية
        val battery: Boolean,         // التطبيق مستثنى من توفير البطارية
        val service: Boolean          // خدمة الاستماع شغالة
    ) {
        val allOk get() = notifications && channel && battery && service
    }

    fun check(ctx: Context): Status {
        val app = ctx.applicationContext
        val notifOn = NotificationManagerCompat.from(app).areNotificationsEnabled()
        var channelOk = true
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = app.getSystemService(NotificationManager::class.java)?.getNotificationChannel(ChatApp.CHANNEL_MESSAGES)
            if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) channelOk = false
        }
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val battery = pm?.isIgnoringBatteryOptimizations(app.packageName) ?: true
        return Status(notifOn, channelOk, battery, MessageListenerService.running)
    }

    /** تنبيه تلقائي: فقط إذا فيه مشكلة، ومرة كل يومين مثل أقصى حد (حتى ما نزعج المستخدم) */
    fun autoPromptDue(ctx: Context): Boolean {
        if (check(ctx).allOk) return false
        val p = ctx.applicationContext.getSharedPreferences("notif_health", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong("last_prompt", 0L) < 2 * 24 * 3600 * 1000L) return false
        p.edit().putLong("last_prompt", now).apply()
        return true
    }

    private fun start(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) { false }

    fun openAppNotificationSettings(ctx: Context) {
        val i = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        } else openAppDetails(ctx)
        if (!start(ctx, i)) start(ctx, openAppDetails(ctx))
    }

    private fun openAppDetails(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** نافذة أندرويد "السماح بالعمل بالخلفية"، وإذا ما انفتحت نفتح قائمة تحسين البطارية */
    fun requestBatteryExemption(ctx: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        if (!start(ctx, direct)) {
            if (!start(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) start(ctx, openAppDetails(ctx))
        }
    }

    /**
     * صفحة "التشغيل التلقائي / تشغيل بالخلفية" الخاصة بشركة الجهاز. أندرويد ما عنده API موحّد وما نكدر نعرف
     * إذا مفعّلة، فنجرّب صفحات الشركات المعروفة، وإذا ما انفتحت واحدة منها نفتح إعدادات التطبيق.
     */
    fun openAutoStart(ctx: Context) {
        fun c(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
        val candidates = listOf(
            // أوبو / ريلمي / ون بلس (ColorOS / OxygenOS)
            c("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            c("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            c("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            c("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"),
            c("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            // شاومي / ريدمي / بوكو (MIUI / HyperOS)
            c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // فيفو / آيكو
            c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            // هواوي / هونر
            c("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            c("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            // سامسونگ
            c("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            // آسوس
            c("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
        )
        for (i in candidates) if (start(ctx, i)) return
        start(ctx, openAppDetails(ctx))
    }
}

@Composable
private fun HealthRow(ok: Boolean, title: String, hint: String, action: String?, backdrop: Backdrop, fg: Color, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (ok) Color(0xFF30D158) else Color(0xFFFF9F0A)),
            contentAlignment = Alignment.Center
        ) { Text(if (ok) "✓" else "!", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, color = fg.copy(alpha = 0.65f), fontSize = 12.sp)
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            GlassButton(
                onClick = onAction, backdrop = backdrop, tint = if (ok) Color.Unspecified else AccentBlue,
                modifier = Modifier.height(36.dp)
            ) { Text(action, color = if (ok) fg else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp)) }
        }
    }
}

/** نافذة فحص الإشعارات: كل سبب مع حالته وزر إصلاحه */
@Composable
fun NotifHealthDialog(backdrop: Backdrop, dark: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val fg = glassFg(dark)
    // نعيد الفحص كل ما يرجع المستخدم للتطبيق من صفحة الإعدادات (AppState.foreground يتغير)
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(AppState.foreground) { if (AppState.foreground) tick++ }
    val st = remember(tick) { NotifHealth.check(ctx) }

    GlassDialog(backdrop = backdrop, dark = dark, title = "فحص الإشعارات", onDismiss = onDismiss) {
        Text(
            if (st.allOk) "كل شي تمام. إذا رسالة ما وصلتك كلشي، تأكد كمان من «التشغيل التلقائي» وقفل التطبيق بالشاشات الأخيرة."
            else "فيه إعدادات بجهازك تمنع الإشعارات توصل والتطبيق مسكّر. صلّحها بالأزرار:",
            color = fg.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)
        )
        HealthRow(st.notifications, "إشعارات التطبيق", if (st.notifications) "مفعّلة" else "مطفية من إعدادات الجهاز",
            if (st.notifications) null else "تفعيل", backdrop, fg) { NotifHealth.openAppNotificationSettings(ctx) }
        HealthRow(st.channel, "قناة الرسائل", if (st.channel) "شغالة" else "مطفية، ما راح يبين أي إشعار رسالة",
            if (st.channel) null else "تفعيل", backdrop, fg) { NotifHealth.openAppNotificationSettings(ctx) }
        HealthRow(st.battery, "البطارية بدون قيود", if (st.battery) "مستثنى من توفير البطارية" else "الجهاز ممكن يقتل التطبيق بالخلفية",
            if (st.battery) null else "استثناء", backdrop, fg) { NotifHealth.requestBatteryExemption(ctx) }
        HealthRow(st.service, "خدمة الاستماع", if (st.service) "شغالة" else "واقفة (تشتغل من جديد لما تفتح التطبيق)",
            if (st.service) null else "تشغيل", backdrop, fg) { MessageListenerService.start(ctx); tick++ }
        HealthRow(true, "التشغيل التلقائي", "أوبو/ريلمي/شاومي/فيفو/هواوي: لازم تفعّله يدوياً (ما نكدر نفحصه)",
            "افتح", backdrop, fg) { NotifHealth.openAutoStart(ctx) }
        Spacer(Modifier.height(6.dp))
        Text(
            "نصيحة: افتح الشاشات الأخيرة واقفل التطبيق (اسحب البطاقة للأسفل أو اضغط أيقونة القفل). وتجنّب تسحبه لفوق لتطرده، لأنه يوقف الإشعارات.",
            color = fg.copy(alpha = 0.65f), fontSize = 12.sp
        )
    }
}
