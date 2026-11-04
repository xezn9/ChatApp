package com.chatapp.messenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.chatapp.messenger.ui.theme.isAppDark
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.PrivacyState
import com.chatapp.messenger.Repo
import com.chatapp.messenger.ui.glass.LiquidToggle
import com.kyant.backdrop.Backdrop

// ============================================================================
//  صفحة "الخصوصية": نفس شكل صفحة الحساب (خلفية Liquid Glass + بطاقات زجاجية)،
//  وكل خيار بمفتاح LiquidToggle. التغيير يتطبق فوراً وينحفظ بـ privacy/<uid>
// ============================================================================

@Composable
internal fun PrivacySettingsPage(onBack: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AccountScene { bd -> PrivacySettingsContent(bd, onBack) }
    }
}

@Composable
private fun PrivacySettingsContent(bd: Backdrop, onBack: () -> Unit) {
    val dark = isAppDark()
    val fg = MaterialTheme.colorScheme.onBackground
    val p = PrivacyState.value

    // كل شي (حتى زر الرجوع وعنوان "الخصوصية") داخل نفس التمرير: الصفحة بملء الشاشة ويصعدون وياها
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
        AppSettingsHeader("الخصوصية", bd, onBack)

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StaggerIn(0, Modifier.padding(top = 4.dp), startDelay = 120L) {
                AccountHero(bd, AppIcons.Lock, SetGreen, SetMint)
            }
            Spacer(Modifier.height(10.dp))

            StaggerIn(1, Modifier.fillMaxWidth().padding(vertical = 6.dp), startDelay = 120L, step = 80L) {
                PrivacyToggleCard(
                    "إخفاء حالة الاتصال",
                    "الناس ما يشوفون إذا أنا متصل، وأنا بعد ما أشوف اتصال أحد",
                    AppIcons.EyeOff, AccentBlue, SetViolet, p.hideOnline, bd, dark
                ) { Repo.setPrivacy("hideOnline", it) }
            }
            StaggerIn(2, Modifier.fillMaxWidth().padding(vertical = 6.dp), startDelay = 120L, step = 80L) {
                PrivacyToggleCard(
                    "إخفاء حالة مقروء",
                    "ما يبين لهم إذا قريت رسائلهم، وأنا بعد ما أشوف مقروء على رسائلي",
                    AppIcons.Eye, SetPink, SetOrange, p.hideRead, bd, dark
                ) { Repo.setPrivacy("hideRead", it) }
            }
            StaggerIn(3, Modifier.fillMaxWidth().padding(vertical = 6.dp), startDelay = 120L, step = 80L) {
                PrivacyToggleCard(
                    "إخفاء وقت الظهور",
                    "يخفي آخر ظهور لي عن الناس، وأنا بعد ما أشوف آخر ظهور أحد",
                    AppIcons.Clock, SetMint, SetSky, p.hideLastSeen, bd, dark
                ) { Repo.setPrivacy("hideLastSeen", it) }
            }
            StaggerIn(4, Modifier.fillMaxWidth().padding(vertical = 6.dp), startDelay = 120L, step = 80L) {
                PrivacyToggleCard(
                    "طلبات الرسائل",
                    "رسالة أي شخص جديد توصلني كطلب، ما يرسل غيرها لين أوافق وأرد عليه",
                    AppIcons.Inbox, SetViolet, SetPink, p.msgReq, bd, dark
                ) { Repo.setPrivacy("msgReq", it) }
            }

            Spacer(Modifier.height(18.dp))
            StaggerIn(5, Modifier.fillMaxWidth().padding(horizontal = 12.dp), startDelay = 120L, step = 80L) {
                Text(
                    "الطلبات تطلع بصفحة Home بخانة \"الطلبات\". المحادثات الموجودة من قبل تبقى عادية.",
                    fontSize = 13.sp, color = fg.copy(0.5f), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** بطاقة خيار بمفتاح: أيقونة متوهجة + عنوان + شرح، والمفتاح بالطرف. الحافة تتلون إذا الخيار شغّال */
@Composable
private fun PrivacyToggleCard(
    title: String, subtitle: String, icon: ImageVector, c1: Color, c2: Color,
    checked: Boolean, bd: Backdrop, dark: Boolean, onChange: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    val fg = MaterialTheme.colorScheme.onBackground
    Row(
        Modifier.fillMaxWidth()
            .liquidCard(bd, shape, dark, borderColor = if (checked) c1.copy(alpha = 0.75f) else null)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box54(c1, c2, icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 13.sp, color = fg.copy(alpha = 0.62f), modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        LiquidToggle(
            selected = { checked },
            onSelect = onChange,
            backdrop = bd,
            dark = dark,
            accent = c1
        )
    }
}

/** أيقونة دائرية متدرجة متوهجة (نفس شكل أيقونات بطاقات الحساب) */
@Composable
private fun Box54(c1: Color, c2: Color, icon: ImageVector) {
    Box(
        Modifier.size(54.dp)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(c1.copy(alpha = 0.55f), Color.Transparent), radius = size.width * 0.95f),
                    radius = size.width * 0.95f
                )
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c1, c2)))
            .border(1.dp, Color.White.copy(0.45f), CircleShape),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
}
