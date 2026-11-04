package com.chatapp.messenger.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.messenger.ui.glass.GlassButton
import com.chatapp.messenger.ui.glass.LiquidToggle
import com.kyant.backdrop.Backdrop

private val EdBlue get() = AccentBlue

private val Palette = listOf(
    0xFFFFFFFF, 0xFFF4B6C8, 0xFFFF5C8A, 0xFFE0245E, 0xFFFF9A2E, 0xFFFFD54F,
    0xFFFFF59D, 0xFF8FE0F2, 0xFF29B6F6, 0xFF3D5AFE, 0xFF7C4DFF, 0xFF5DBB63,
    0xFF00BFA5, 0xFFD9A066, 0xFF8D6E63, 0xFF90A4AE, 0xFF1C2733, 0xFF000000
).map { it.toInt() }

/**
 * محرر "صنع فقاعة": معاينة حيّة + خيارات (ألوان، تدرج، استدارة، إطار، زخرفة، توهج).
 * [initial] = null يعني فقاعة جديدة. [onSave] تستلم التصميم النهائي.
 */
@Composable
fun BubbleEditor(
    backdrop: Backdrop,
    dark: Boolean,
    fg: Color,
    initial: CustomBubble?,
    onSave: (CustomBubble) -> Unit,
    onCancel: () -> Unit
) {
    val base = remember { initial ?: CustomBubble(id = CustomBubble.newId()) }
    var name by remember { mutableStateOf(base.name) }
    var radius by remember { mutableStateOf(base.radius.toFloat()) }
    var fill1 by remember { mutableStateOf(base.fill1) }
    var gradient by remember { mutableStateOf(base.fill2 != null) }
    var fill2 by remember { mutableStateOf(base.fill2 ?: 0xFFFF5C8A.toInt()) }
    var textC by remember { mutableStateOf(base.text) }
    var borderW by remember { mutableStateOf(base.borderW) }
    var borderC by remember { mutableStateOf(base.borderC) }
    var decor by remember { mutableStateOf(base.decor) }
    var accent by remember { mutableStateOf(base.accent) }
    var decor2 by remember { mutableStateOf(base.decor2) }
    var accent2 by remember { mutableStateOf(base.accent2) }
    var svg1 by remember { mutableStateOf(base.svg1) }
    var svg2 by remember { mutableStateOf(base.svg2) }
    val ctx = LocalContext.current
    remember {
        SvgStore.load(ctx)
        base.svg1?.let { SvgStore.add(ctx, "شكلي", it) }
        base.svg2?.let { SvgStore.add(ctx, "شكلي", it) }
        0
    }
    var manage by remember { mutableStateOf<SvgEntry?>(null) }
    var glowOn by remember { mutableStateOf(base.glow != null) }
    var glowC by remember { mutableStateOf(base.glow ?: 0xFF00F0FF.toInt()) }

    val current = CustomBubble(
        id = base.id,
        name = name.trim().take(20).ifBlank { "فقاعتي" },
        radius = radius.toInt(),
        fill1 = fill1,
        fill2 = if (gradient) fill2 else null,
        text = textC,
        borderW = borderW,
        borderC = borderC,
        decor = decor,
        accent = accent,
        decor2 = decor2,
        accent2 = accent2,
        svg1 = svg1,
        svg2 = svg2,
        glow = if (glowOn) glowC else null
    )
    val sub = fg.copy(alpha = 0.65f)

    // ----- المعاينة -----
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1C2733).copy(alpha = 0.75f))
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StyledBubble(current.toStyle(), mine = false, dark = true, fg = Color.White, backdrop = null) {
                BubbleSampleText("مرحبا، شلونك؟", it)
            }
            StyledBubble(current.toStyle(), mine = true, dark = true, fg = Color.White, backdrop = null) {
                BubbleSampleText("هذي فقاعتي الجديدة", it)
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    // ----- الاسم -----
    EdLabel("اسم الفقاعة", sub)
    BasicTextField(
        value = name,
        onValueChange = { name = it.take(20) },
        singleLine = true,
        textStyle = TextStyle(color = fg, fontSize = 16.sp),
        cursorBrush = SolidColor(EdBlue),
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fg.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )

    // ----- لون الفقاعة -----
    EdLabel("لون الفقاعة", sub)
    SwatchRow(fill1, fg) { fill1 = it }
    ToggleRow("تدرّج لوني", gradient, backdrop, dark, fg) { gradient = it }
    if (gradient) {
        EdLabel("اللون الثاني", sub)
        SwatchRow(fill2, fg) { fill2 = it }
    }

    // ----- لون النص -----
    EdLabel("لون النص", sub)
    SwatchRow(textC, fg) { textC = it }

    // ----- الاستدارة -----
    EdLabel("استدارة الزوايا", sub)
    EdSlider(radius, 4f..30f) { radius = it }

    // ----- الإطار -----
    EdLabel("سماكة الإطار", sub)
    EdSlider(borderW, 0f..4f) { borderW = it }
    if (borderW > 0.1f) {
        EdLabel("لون الإطار", sub)
        SwatchRow(borderC, fg) { borderC = it }
    }

    // ----- الأشكال (مع الدمج) -----
    EdLabel("الشكل", sub)
    DecorPicker(decor, EdBlue, fg, bodyColor = Color(fill1), tint = Color(accent), mirror = false,
        svgs = SvgStore.items, currentSvg = svg1, onPickSvg = { svg1 = it.svg; decor = "svg" }, onManage = { manage = it },
        onPick = { decor = it })
    SvgImportBar(fg, EdBlue) { svg1 = it; decor = "svg" }
    if (if (decor == "svg") svg1?.hasTint == true else bubbleDecorById(decor).tintable) {
        EdLabel("لون الشكل", sub)
        SwatchRow(accent, fg) { accent = it }
    }
    Row(Modifier.padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MergeBadge()
        Spacer(Modifier.width(8.dp))
        Text("شكل ثاني (يظهر بالجهة المقابلة ويندمج مع الأول)", fontSize = 13.sp, color = sub)
    }
    DecorPicker(decor2, MergeColor, fg, bodyColor = Color(fill1), tint = Color(accent2), mirror = true,
        svgs = SvgStore.items, currentSvg = svg2, onPickSvg = { svg2 = it.svg; decor2 = "svg" }, onManage = { manage = it },
        onPick = { decor2 = it }, mergeBadge = true)
    SvgImportBar(fg, MergeColor) { svg2 = it; decor2 = "svg" }
    if (if (decor2 == "svg") svg2?.hasTint == true else bubbleDecorById(decor2).tintable) {
        EdLabel("لون الشكل الثاني", sub)
        SwatchRow(accent2, fg) { accent2 = it }
    }

    // ----- التوهج -----
    Spacer(Modifier.height(4.dp))
    ToggleRow("توهج نيون حول الفقاعة", glowOn, backdrop, dark, fg) { glowOn = it }
    if (glowOn) SwatchRow(glowC, fg) { glowC = it }

    // ----- الأزرار -----
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton(onClick = onCancel, backdrop = backdrop, modifier = Modifier.weight(1f).height(48.dp)) {
            Text("إلغاء", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        GlassButton(onClick = { onSave(current) }, backdrop = backdrop, tint = EdBlue, modifier = Modifier.weight(1.4f).height(48.dp)) {
            Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("حفظ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    manage?.let { e ->
        SvgManageDialog(
            entry = e, fg = fg,
            onRename = { nm -> SvgStore.rename(ctx, e.id, nm); manage = null },
            onDelete = { SvgStore.delete(ctx, e.id); manage = null },
            onDismiss = { manage = null }
        )
    }
}

@Composable
private fun EdLabel(text: String, color: Color) {
    Text(text, fontSize = 13.sp, color = color, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
}

/**
 * صف الألوان السريعة + زر (قوس قزح) جنبه يفتح لوحة "كل الألوان" مع تدرّج للغامق.
 * اللون المخصّص (إذا مو من الباليت) يظهر أول الصف محدّد.
 */
@Composable
private fun SwatchRow(selected: Int, fg: Color, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val inPalette = Palette.any { (it and 0xFFFFFF) == (selected and 0xFFFFFF) }
    val shown = if (inPalette) Palette else listOf(selected) + Palette
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                shown.forEach { c ->
                    val sel = (c and 0xFFFFFF) == (selected and 0xFFFFFF)
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                            .border(if (sel) 3.dp else 1.dp, if (sel) EdBlue else fg.copy(alpha = 0.3f), CircleShape)
                            .clickable { onPick(c) }
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            AllColorsButton(open, fg) { open = !open }
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            FullColorPanel(selected, fg, onPick)
        }
    }
}

/** زر دائري بتدرّج الطيف الكامل — يفتح/يسكّر لوحة كل الألوان. */
@Composable
internal fun AllColorsButton(open: Boolean, fg: Color, onClick: () -> Unit) {
    val rainbow = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF00C7BE),
                Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D92), Color(0xFFFF3B30)
            )
        )
    }
    Box(
        Modifier.size(34.dp).clip(CircleShape)
            .drawBehind { drawCircle(rainbow) }
            .border(if (open) 3.dp else 1.dp, if (open) EdBlue else fg.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)))
    }
}

// أعمدة اللوحة: 12 درجة لون (كل 30°) × 5 مستويات سطوع + صف رمادي
private val HueStops = (0 until 12).map { it * 30f }
private val ShadeRows = listOf(0.30f to 1.0f, 0.60f to 1.0f, 1.0f to 1.0f, 1.0f to 0.72f, 1.0f to 0.45f)
private val GrayRow = (0 until 12).map { Color.hsv(0f, 0f, 1f - it / 11f) }

/**
 * لوحة "كل الألوان": شبكة ألوان كاملة + شريط تدرّج من اللون المختار لين الغامق.
 */
@Composable
internal fun FullColorPanel(selected: Int, fg: Color, onPick: (Int) -> Unit) {
    var base by remember { mutableStateOf(Color(selected)) }
    var dark by remember { mutableStateOf(0f) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(fg.copy(alpha = 0.06f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ShadeRows.forEach { (sat, v) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                HueStops.forEach { h -> ColorCell(Color.hsv(h, sat, v), selected, fg, Modifier.weight(1f).aspectRatio(1f)) { base = it; dark = 0f; onPick(it.toArgb()) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            GrayRow.forEach { g -> ColorCell(g, selected, fg, Modifier.weight(1f).aspectRatio(1f)) { base = it; dark = 0f; onPick(it.toArgb()) } }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("درجة الغمق", fontSize = 13.sp, color = fg.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
            // باج: تدرّج للغامق
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF8A5CFF).copy(alpha = 0.22f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(width = 22.dp, height = 8.dp).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(base, Color.Black)))
                )
                Spacer(Modifier.width(6.dp))
                Text("تدرّج للغامق", fontSize = 11.sp, color = Color(0xFFB79BFF), fontWeight = FontWeight.SemiBold)
            }
        }
        DarkShadeBar(base, dark) { t ->
            dark = t
            onPick(lerp(base, Color.Black, t).toArgb())
        }
    }
}

@Composable
private fun ColorCell(c: Color, selected: Int, fg: Color, modifier: Modifier, onClick: (Color) -> Unit) {
    val sel = (c.toArgb() and 0xFFFFFF) == (selected and 0xFFFFFF)
    Box(
        modifier.clip(CircleShape).background(c)
            .border(if (sel) 2.5.dp else 1.dp, if (sel) EdBlue else fg.copy(alpha = 0.25f), CircleShape)
            .clickable { onClick(c) }
    )
}

/** شريط يتدرّج من [base] لين الأسود؛ اسحب أو اضغط لاختيار درجة الغمق (0..1). */
@Composable
private fun DarkShadeBar(base: Color, t: Float, onChange: (Float) -> Unit) {
    var widthPx by remember { mutableStateOf(1) }
    val latest by rememberUpdatedState(onChange)
    val track = Brush.horizontalGradient(listOf(base, Color.Black))
    fun at(x: Float) = (x / widthPx).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().height(32.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { latest(at(it.x)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> change.consume(); latest(at(change.position.x)) } },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(track)
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
        )
        // المقبض
        Box(
            Modifier.fillMaxWidth().height(32.dp).drawBehind {
                val x = t * size.width
                drawCircle(lerp(base, Color.Black, t), 13.dp.toPx(), Offset(x, size.height / 2f))
                drawCircle(Color.White, 13.dp.toPx(), Offset(x, size.height / 2f), style = Stroke(3.dp.toPx()))
            }
        )
    }
}

@Composable
private fun EdSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Slider(
        value = value, onValueChange = onChange, valueRange = range,
        colors = SliderDefaults.colors(
            thumbColor = EdBlue, activeTrackColor = EdBlue, inactiveTrackColor = Color.White.copy(alpha = 0.2f)
        )
    )
}

@Composable
private fun ToggleRow(title: String, on: Boolean, backdrop: Backdrop, dark: Boolean, fg: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = fg, modifier = Modifier.weight(1f))
        LiquidToggle(selected = { on }, onSelect = onChange, backdrop = backdrop, dark = dark, accent = EdBlue)
    }
}

private val MergeColor = Color(0xFF8A5CFF)

private fun fileNameOf(ctx: android.content.Context, uri: Uri): String = try {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }?.substringBeforeLast('.')?.trim().orEmpty().ifBlank { "شكلي" }
} catch (e: Exception) { "شكلي" }

/** نافذة إدارة شكل SVG محفوظ: تغيير الاسم أو الحذف. */
@Composable
private fun SvgManageDialog(entry: SvgEntry, fg: Color, onRename: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(entry.name) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFF1C2733))
                .border(0.8.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp)).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val d = entry.decor
            Box(
                Modifier.padding(top = 8.dp + d.topPad).size(width = 76.dp, height = 36.dp).drawBehind {
                    drawRoundRect(Color(0xFF7C4DFF), cornerRadius = CornerRadius(12.dp.toPx()))
                    d.draw?.invoke(this, size.width, size.height, Color(0xFFFFD54F))
                }
            )
            Spacer(Modifier.height(16.dp))
            Text("اسم الشكل", fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value = name, onValueChange = { name = it.take(20) }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(EdBlue),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 12.dp)
            )
            Spacer(Modifier.height(16.dp))
            EdDialogBtn(Modifier.fillMaxWidth().height(46.dp), EdBlue, { onRename(name) }) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("حفظ الاسم", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EdDialogBtn(Modifier.weight(1f).height(44.dp), Color.White.copy(alpha = 0.14f), onDismiss) {
                    Text("إلغاء", color = Color.White, fontSize = 14.sp)
                }
                EdDialogBtn(Modifier.weight(1f).height(44.dp), Color(0xFFE0245E), onDelete) {
                    Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("حذف", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun EdDialogBtn(modifier: Modifier, color: Color, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(color).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

/** أزرار: استرداد شكل SVG من الملفات + فتح موقع SVG Repo لتنزيل أشكال. */
@Composable
private fun SvgImportBar(fg: Color, accent: Color, onImported: (SvgDecor) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val res = withContext(Dispatchers.IO) {
                try {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    when {
                        bytes == null -> SvgImport.Result(null, "ما قدرت أقرا الملف")
                        bytes.size > 600 * 1024 -> SvgImport.Result(null, "الملف كبير بزيادة")
                        else -> SvgImport.parse(String(bytes, Charsets.UTF_8))
                    }
                } catch (e: Exception) {
                    SvgImport.Result(null, "ما قدرت أقرا الملف")
                }
            }
            val svg = res.svg
            if (svg != null) {
                val saved = SvgStore.add(ctx, fileNameOf(ctx, uri), svg)
                onImported(svg)
                AppToast.show(if (saved != null) "تمت إضافة الشكل للقائمة ✓ (اضغط ✎ لتغيير اسمه)" else "مكتبة الأشكال ممتلئة، انطبق الشكل بس ما انحفظ")
            } else AppToast.show(res.error ?: "الملف مو SVG صالح")
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.22f))
                .border(1.dp, accent, RoundedCornerShape(12.dp))
                .clickable { picker.launch(arrayOf("*/*")) }.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) { Text("استرداد شكل SVG", fontSize = 13.sp, color = fg, fontWeight = FontWeight.SemiBold) }
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(fg.copy(alpha = 0.08f))
                .clickable {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.svgrepo.com/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) { AppToast.show("ما لقيت متصفح") }
                }.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) { Text("موقع SVG Repo ↗", fontSize = 13.sp, color = fg) }
    }
}

/** باج مميز يبيّن إن هذا الشكل ضمن أشكال الدمج. */
@Composable
private fun MergeBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(MergeColor).padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text("دمج", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DecorPicker(
    selected: String, accent: Color, fg: Color,
    bodyColor: Color, tint: Color, mirror: Boolean,
    svgs: List<SvgEntry> = emptyList(),
    currentSvg: SvgDecor? = null,
    onPickSvg: (SvgEntry) -> Unit = {},
    onManage: (SvgEntry) -> Unit = {},
    mergeBadge: Boolean = false,
    onPick: (String) -> Unit
) {
    // "بدون" أول شي، بعدين أشكال SVG المحفوظة، بعدين الأشكال الجاهزة
    val items: List<Pair<BubbleDecor, SvgEntry?>> =
        listOf<Pair<BubbleDecor, SvgEntry?>>(BubbleDecors[0] to null) +
            svgs.map { it.decor to it } +
            BubbleDecors.drop(1).map { it to null }
    items.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { (d, entry) ->
                val sel = if (entry != null) selected == "svg" && currentSvg?.sig == entry.svg.sig else selected == d.id
                Box(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) accent.copy(alpha = 0.22f) else fg.copy(alpha = 0.08f))
                        .border(1.5.dp, if (sel) accent else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { if (entry != null) onPickSvg(entry) else onPick(d.id) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(entry?.name ?: d.name, fontSize = 13.sp, color = if (sel) accent else fg, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = if (entry != null) 18.dp else 0.dp))
                    Spacer(Modifier.height(8.dp))
                    // معاينة الشكل تحت الاسم (أشكال الأعلى تحتاج مساحة فوق الجسم)
                    Box(
                        Modifier.padding(top = d.topPad)
                            .size(width = 76.dp, height = 36.dp)
                            .drawBehind {
                                drawRoundRect(bodyColor, cornerRadius = CornerRadius(12.dp.toPx()))
                                val fn = d.draw
                                if (fn != null) {
                                    if (mirror) {
                                        withTransform({ scale(-1f, 1f, Offset(size.width / 2f, size.height / 2f)) }) {
                                            fn(this, size.width, size.height, tint)
                                        }
                                    } else fn(this, size.width, size.height, tint)
                                }
                            }
                    )
                }
                if (mergeBadge && d.id != "none") MergeBadge(Modifier.align(Alignment.TopStart).padding(4.dp))
                if (entry != null) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(3.dp).size(24.dp).clip(CircleShape)
                            .background(fg.copy(alpha = 0.12f)).clickable { onManage(entry) },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Edit, null, tint = fg, modifier = Modifier.size(13.dp)) }
                }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
