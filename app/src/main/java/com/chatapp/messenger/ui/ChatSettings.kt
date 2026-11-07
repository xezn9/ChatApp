package com.chatapp.messenger.ui

import com.chatapp.messenger.ui.theme.GlassFx
import com.chatapp.messenger.ui.theme.glassSurfaceLayer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.glass.LiquidToggle
import com.chatapp.messenger.ui.theme.appBackground
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

private val Blue get() = AccentBlue
private val Red = Color(0xFFE5484D)
private val Gold = Color(0xFFFFC107)

/** أسباب البلاغ. الـ id لازم يطابق firestore.rules و functions/index.js */
private val ReportReasons = listOf(
    "abuse" to "كلمات مسيئة",
    "violence" to "عنف ودموية",
    "suicide_kill" to "أفكار انتحار وقتل"
)

/**
 * لوحة إعدادات المحادثة بتصميم Liquid Glass (نفس زجاج التطبيق).
 * تُوضع فوق شاشة المحادثة، و[backdrop] هو نفس backdrop الأشرطة حتى يظهر الزجاج على المحادثة الحقيقية.
 */
@Composable
fun AnimatedVisibilityScope.ChatSettingsPanel(
    backdrop: Backdrop,
    dark: Boolean,
    otherName: String,
    bg: ChatBg,
    hasImage: Boolean,
    onBgChange: (ChatBg) -> Unit,
    onPickImage: () -> Unit,
    styleId: String,
    onStyleChange: (String) -> Unit,
    /** JSON الفقاعة المخصّصة المفعّلة عندي حالياً (فاضي = ما عندي فقاعة مخصّصة) */
    customJson: String = "",
    /** تفعيل فقاعة من مكتبتي: يستلم JSON التصميم */
    onCustomSelect: (String) -> Unit = {},
    pinned: Boolean,
    onTogglePin: () -> Unit,
    blockedByMe: Boolean,
    onToggleBlock: () -> Unit,
    onReport: (String) -> Unit,
    onDismiss: () -> Unit,
    /** عنوان اللوحة (للمجموعات: "إعدادات المجموعة") */
    title: String = "إعدادات المحادثة",
    /** false = بدون خيارات الشخص (تثبيت/حظر/إبلاغ)، تُستعمل بمحادثات المجموعات */
    personActions: Boolean = true,
    /** ملاحظة تحت عنوان الخلفية (تغيّر النص الافتراضي) */
    bgNote: String? = null
) {
    val fg = if (dark) Color.White else Color(0xFF101418)
    val defaultBg = appBackground()
    var page by remember { mutableStateOf("main") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { CustomBubbleStore.load(ctx) }
    // الفقاعة الي أحررها حالياً (null = جديدة) والفقاعة الي فتحت قائمة خياراتها بالضغط المطوّل
    var editing by remember { mutableStateOf<CustomBubble?>(null) }
    var optionsFor by remember { mutableStateOf<CustomBubble?>(null) }
    val activeCustomId = remember(customJson) { CustomBubble.fromJson(customJson)?.id }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val b = BubbleFiles.readFromUri(ctx, uri)
            if (b == null) AppToast.show("هذا الملف مو فقاعة صالحة")
            else if (!CustomBubbleStore.upsert(ctx, b)) AppToast.show("مكتبة الفقاعات ممتلئة، احذف فقاعة أولاً")
            else AppToast.show("تم استرداد فقاعة «${b.name}» ✓")
        }
    }

    BackHandler {
        when (page) {
            "main" -> onDismiss()
            "bubble_edit" -> page = "bubbles"
            else -> page = "main"
        }
    }

    // طبقة تعتيم: الضغط عليها يغلق اللوحة
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        optionsFor?.let { b ->
            BubbleOptionsDialog(
                bubble = b, fg = fg,
                onSave = {
                    scope.launch {
                        val name = BubbleFiles.saveToDownloads(ctx, b)
                        AppToast.show(if (name != null) "انحفظت بمجلد التنزيلات: $name" else "تعذّر حفظ الملف", long = name != null)
                    }
                    optionsFor = null
                },
                onEdit = { editing = b; optionsFor = null; page = "bubble_edit" },
                others = CustomBubbleStore.items.filter { it.id != b.id },
                onMerge = { o ->
                    val m = mergeBubbles(b, o)
                    optionsFor = null
                    if (CustomBubbleStore.upsert(ctx, m)) {
                        AppToast.show("تم الدمج ✓ تقدر تعدّل النتيجة هسه")
                        editing = m; page = "bubble_edit"
                    } else AppToast.show("مكتبة الفقاعات ممتلئة، احذف فقاعة أولاً")
                },
                onDelete = { CustomBubbleStore.delete(ctx, b.id); optionsFor = null },
                onDismiss = { optionsFor = null }
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter)
                .animateEnterExit(
                    enter = slideInVertically(spring(dampingRatio = 0.74f, stiffness = 330f)) { it / 2 } +
                        scaleIn(spring(dampingRatio = 0.8f, stiffness = 360f), initialScale = 0.82f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                        fadeIn(tween(180)),
                    exit = slideOutVertically(tween(230)) { it / 2 } +
                        scaleOut(tween(230), targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                        fadeOut(tween(170))
                )
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(10.dp)
                .heightIn(max = 600.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(32.dp) },
                    effects = {
                        vibrancy()
                        blur((16f.dp.toPx()) * GlassFx.blurK)
                        lens((12f.dp.toPx()) * GlassFx.refractK, (24f.dp.toPx()) * GlassFx.refractK)
                    },
                    onDrawSurface = { glassSurfaceLayer { drawRect(if (dark) Color.Black.copy(0.38f) else Color.White.copy(0.50f)) } }
                )
                .border(
                    0.8.dp,
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(0.45f), Color.White.copy(0.08f))),
                    RoundedCornerShape(32.dp)
                )
                // يمنع الضغط داخل اللوحة من إغلاقها
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides fg) {
                when (page) {
                    "main" -> {
                        StaggerIn(0, Modifier.fillMaxWidth(), startDelay = 40L) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                GlassButton(onClick = onDismiss, backdrop = backdrop, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Filled.Close, null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        StaggerIn(1, Modifier.fillMaxWidth(), startDelay = 40L) {
                            PanelRow(AppIcons.Image, "خلفية المحادثة", Blue, fg) { page = "bg" }
                        }
                        StaggerIn(2, Modifier.fillMaxWidth(), startDelay = 40L) {
                            PanelRow(AppIcons.ChatBubble, "فقاعات الرسائل", Color(0xFF8A5CFF), fg) { page = "bubbles" }
                        }
                        if (personActions) {
                            StaggerIn(3, Modifier.fillMaxWidth(), startDelay = 40L) {
                                PanelRow(Icons.Filled.Star, if (pinned) "إلغاء التثبيت" else "تثبيت كشخص مميز", Gold, fg) {
                                    onTogglePin(); onDismiss()
                                }
                            }
                            StaggerIn(4, Modifier.fillMaxWidth(), startDelay = 40L) {
                                PanelRow(AppIcons.Block, if (blockedByMe) "إلغاء حظر $otherName" else "حظر $otherName", Red, fg) {
                                    if (blockedByMe) { onToggleBlock(); onDismiss() } else page = "block"
                                }
                            }
                            StaggerIn(5, Modifier.fillMaxWidth(), startDelay = 40L) {
                                PanelRow(Icons.Filled.Warning, "إبلاغ عن $otherName", Color(0xFFFF9800), fg) { page = "report" }
                            }
                        }
                    }

                    "bg" -> {
                        SubHeader("خلفية المحادثة", backdrop) { page = "main" }
                        Text(bgNote ?: if (personActions) "الخلفية تنطبق على هذي المحادثة فقط" else "الخلفية تنطبق على هذي المجموعة فقط", fontSize = 13.sp, color = fg.copy(alpha = 0.65f))
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BgTile("افتراضي", bg.kind == "default", fg, { Modifier.background(defaultBg) }) {
                                onBgChange(bg.copy(kind = "default"))
                            }
                            ChatBgPresets.forEach { p ->
                                BgTile(p.name, bg.kind == "preset" && bg.preset == p.id, fg, { Modifier.presetBackground(p) }) {
                                    onBgChange(bg.copy(kind = "preset", preset = p.id))
                                }
                            }
                            if (hasImage) {
                                BgTile("صورتي", bg.kind == "image", fg, { Modifier.background(Color(0xFF3A4048)) }, showImageIcon = true) {
                                    onBgChange(bg.copy(kind = "image"))
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        GlassButton(
                            onClick = onPickImage, backdrop = backdrop, tint = Blue,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(AppIcons.Image, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("اختيار صورة أو GIF", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("خلفية ضبابية", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("تمويه خفيف للخلفية", fontSize = 13.sp, color = fg.copy(alpha = 0.65f))
                            }
                            LiquidToggle(
                                selected = { bg.blur },
                                onSelect = { onBgChange(bg.copy(blur = it)) },
                                backdrop = backdrop,
                                dark = dark,
                                accent = Blue
                            )
                        }
                    }

                    "bubbles" -> {
                        SubHeader("فقاعات الرسائل", backdrop) { page = "main" }
                        Text(if (personActions) "الشكل ينطبق على كل محادثاتك ويظهر عند الطرفين" else "الشكل ينطبق على كل محادثاتك ويظهر لأعضاء المجموعة", fontSize = 13.sp, color = fg.copy(alpha = 0.65f))
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton(
                                onClick = { editing = null; page = "bubble_edit" }, backdrop = backdrop, tint = Blue,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("صنع فقاعة", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                            GlassButton(
                                onClick = { importLauncher.launch(arrayOf("*/*")) }, backdrop = backdrop,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(AppIcons.Download, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("استرداد فقاعة", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        BubbleStyles.chunked(2).forEachIndexed { rowIdx, row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEachIndexed { colIdx, st ->
                                    StaggerIn(rowIdx * 2 + colIdx, Modifier.weight(1f), step = 30L, startDelay = 30L) {
                                        BubbleTile(st, st.id == styleId, fg, onClick = { onStyleChange(st.id) })
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }

                        // ----- فقاعاتي: بفاصل عن فقاعات التطبيق الأصلية -----
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(1.dp).background(fg.copy(alpha = 0.2f)))
                            Text("فقاعاتي", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = 0.8f), modifier = Modifier.padding(horizontal = 12.dp))
                            Box(Modifier.weight(1f).height(1.dp).background(fg.copy(alpha = 0.2f)))
                        }
                        if (CustomBubbleStore.items.isEmpty()) {
                            Text(
                                "ما عندك فقاعات مخصّصة. اضغط «صنع فقاعة» أو «استرداد فقاعة». اضغط مطوّلاً على فقاعتك لحفظها كملف.",
                                fontSize = 13.sp, color = fg.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            )
                        } else {
                            Text(
                                "اضغط مطوّلاً على الفقاعة لحفظها كملف أو تعديلها",
                                fontSize = 12.sp, color = fg.copy(alpha = 0.55f), textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            )
                            CustomBubbleStore.items.toList().chunked(2).forEach { row ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { b ->
                                        Box(Modifier.weight(1f)) {
                                            BubbleTile(
                                                b.toStyle(), styleId == "custom" && b.id == activeCustomId, fg,
                                                onClick = { onCustomSelect(b.toJson()) },
                                                onLongClick = { optionsFor = b }
                                            )
                                        }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    "bubble_edit" -> {
                        SubHeader(if (editing == null) "صنع فقاعة" else "تعديل الفقاعة", backdrop) { page = "bubbles" }
                        BubbleEditor(
                            backdrop = backdrop, dark = dark, fg = fg, initial = editing,
                            onSave = { b ->
                                if (!CustomBubbleStore.upsert(ctx, b)) {
                                    AppToast.show("مكتبة الفقاعات ممتلئة، احذف فقاعة أولاً")
                                } else {
                                    // إذا كانت هذي الفقاعة هي المفعّلة، نحدّثها عند الطرف الثاني أيضاً
                                    if (styleId == "custom" && b.id == activeCustomId) onCustomSelect(b.toJson())
                                    AppToast.show("تم حفظ الفقاعة ✓")
                                    page = "bubbles"
                                }
                            },
                            onCancel = { page = "bubbles" }
                        )
                    }

                    "block" -> {
                        SubHeader("حظر $otherName؟", backdrop) { page = "main" }
                        Text(
                            "ما راح يقدر يراسلك ولا يشوف ملفك الشخصي، إلى أن تلغي الحظر.",
                            fontSize = 15.sp, color = fg.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton(
                                onClick = { page = "main" }, backdrop = backdrop,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("إلغاء", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                            GlassButton(
                                onClick = { onToggleBlock(); onDismiss() }, backdrop = backdrop, tint = Red,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(AppIcons.Block, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("حظر", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    "report" -> {
                        var selected by remember { mutableStateOf<String?>(null) }
                        SubHeader("إبلاغ عن $otherName", backdrop) { page = "main" }
                        Text(
                            "اختر سبب البلاغ. سيتم فحص الرسائل ومعاقبة المستخدم إذا ثبت الخطأ.",
                            fontSize = 13.sp, color = fg.copy(alpha = 0.65f)
                        )
                        Spacer(Modifier.height(10.dp))
                        ReportReasons.forEach { (id, label) ->
                            val sel = selected == id
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (sel) Blue.copy(alpha = 0.22f) else fg.copy(alpha = 0.08f))
                                    .border(1.dp, if (sel) Blue else Color.Transparent, RoundedCornerShape(18.dp))
                                    .clickable { selected = id }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                if (sel) Icon(Icons.Filled.Check, null, tint = Blue, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        GlassButton(
                            onClick = { selected?.let { onReport(it) } },
                            backdrop = backdrop, tint = Red, enabled = selected != null,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("إرسال البلاغ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelRow(icon: ImageVector, title: String, tint: Color, fg: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(fg.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SubHeader(title: String, backdrop: Backdrop, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassButton(onClick = onBack, backdrop = backdrop, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
    }
}

@Composable
private fun BgTile(
    label: String,
    selected: Boolean,
    fg: Color,
    background: () -> Modifier,
    showImageIcon: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 64.dp, height = 96.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(background())
                .border(if (selected) 2.5.dp else 1.dp, if (selected) Blue else Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (showImageIcon) Icon(AppIcons.Image, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = if (selected) Blue else fg.copy(alpha = 0.75f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BubbleTile(style: BubbleStyle, selected: Boolean, fg: Color, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1C2733).copy(alpha = 0.75f))
            .border(if (selected) 2.dp else 1.dp, if (selected) Blue else Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StyledBubble(style, mine = false, dark = true, fg = Color.White, backdrop = null,
            // بدون هذا الشي الفقاعات الي بيها شكلين (يمين ويسار) ينضغط النص بمربع المعاينة الضيّق
            modifier = Modifier.wrapContentWidth(unbounded = true)) {
            BubbleSampleText("مرحبا", it)
        }
        Spacer(Modifier.height(8.dp))
        Text(style.name, fontSize = 13.sp, color = if (selected) Blue else Color.White.copy(alpha = 0.85f))
    }
}

/** قائمة الضغط المطوّل على فقاعة مخصّصة: حفظ كملف (يروح للتنزيلات) / دمج / تعديل / حذف */
@Composable
private fun BubbleOptionsDialog(
    bubble: CustomBubble, fg: Color,
    onSave: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
    others: List<CustomBubble>, onMerge: (CustomBubble) -> Unit
) {
    var mergeMode by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1C2733))
                .border(0.8.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))
            StyledBubble(bubble.toStyle(), mine = false, dark = true, fg = Color.White, backdrop = null) {
                BubbleSampleText("مرحبا", it)
            }
            Spacer(Modifier.height(10.dp))
            Text(bubble.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (!mergeMode) {
                Text("احفظها كملف وأرسلها لأي شخص حتى يسترجعها عنده", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(16.dp))
                DialogBtn(Modifier.fillMaxWidth().height(48.dp), Blue, onSave) {
                    Icon(AppIcons.Download, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("حفظ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogBtn(Modifier.weight(1f).height(44.dp), Color.White.copy(alpha = 0.14f), onEdit) {
                        Icon(Icons.Filled.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("تعديل", color = Color.White, fontSize = 14.sp)
                    }
                    if (others.isNotEmpty()) {
                        DialogBtn(Modifier.weight(1f).height(44.dp), Color(0xFF8A5CFF), { mergeMode = true }) {
                            Icon(AppIcons.Sparkles, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("دمج", color = Color.White, fontSize = 14.sp)
                        }
                    }
                    DialogBtn(Modifier.weight(1f).height(44.dp), Red, onDelete) {
                        Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("حذف", color = Color.White, fontSize = 14.sp)
                    }
                }
            } else {
                Text("اختر الفقاعة الي تريد تدمجها معها", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.forEach { o ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { onMerge(o) }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StyledBubble(o.toStyle(), mine = false, dark = true, fg = Color.White, backdrop = null) {
                                BubbleSampleText("مرحبا", it)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(o.name, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                DialogBtn(Modifier.fillMaxWidth().height(44.dp), Color.White.copy(alpha = 0.14f), { mergeMode = false }) {
                    Text("رجوع", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

/** زر بسيط للحوارات (الحوار نافذة منفصلة فما نستعمل زجاج الـ backdrop فيه) */
@Composable
private fun DialogBtn(modifier: Modifier, color: Color, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(color).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}
