package com.chatapp.messenger.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.BackHandler
import com.chatapp.messenger.Repo
import com.chatapp.messenger.ui.glass.GlassButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch

@Composable
fun CreateGroupScreen(
    backdrop: Backdrop,
    onClose: () -> Unit,
    onOverlayChange: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fg = MaterialTheme.colorScheme.onBackground
    var name by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var created by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            photo = ImageUtil.uriToB64(ctx, uri, 640, 82).orEmpty()
            if (photo.isBlank()) error = "تعذّر تحميل الصورة"
        }
    }
    BackHandler { onClose() }
    LaunchedEffect(Unit) { onOverlayChange(true) }
    DisposableEffect(Unit) { onDispose { onOverlayChange(false) } }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.34f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 28.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(30.dp) },
                    effects = { vibrancy(); blur(18.dp.toPx()); lens(18.dp.toPx(), 28.dp.toPx()) },
                    onDrawSurface = { drawRect(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xDD171A20) else Color(0xDDF8FAFF)) }
                )
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassButton(onClick = onClose, backdrop = backdrop, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع", tint = fg)
                }
                Spacer(Modifier.weight(1f))
                Text("إنشاء مجموعة", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = fg)
                Spacer(Modifier.weight(1f))
                GlassButton(onClick = onClose, backdrop = backdrop, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = fg)
                }
            }
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier.size(104.dp).clip(CircleShape)
                    .background(fg.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center
            ) {
                val image = rememberB64Image(photo)
                if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else Icon(Icons.Filled.AddPhotoAlternate, null, tint = fg.copy(alpha = 0.65f), modifier = Modifier.size(38.dp))
            }
            TextButton(onClick = { picker.launch("image/*") }) {
                Text(if (photo.isBlank()) "إضافة صورة للمجموعة (اختياري)" else "تغيير صورة المجموعة")
            }
            Spacer(Modifier.height(8.dp))
            GroupInput("اسم المجموعة", name, { name = it; error = "" }, fg)
            Spacer(Modifier.height(12.dp))
            GroupInput(
                "معرّف المجموعة (5 أحرف أو أرقام على الأقل)", groupId,
                { value -> groupId = value.filter { it.isLetterOrDigit() }.lowercase().take(32); error = "" },
                fg, singleLine = true
            )
            Text("مثال: mygroup1 — يمكن استخدام الأحرف الإنجليزية والأرقام", color = fg.copy(alpha = 0.58f), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
            Spacer(Modifier.height(12.dp))
            GroupInput("نبذة عن المجموعة (اختياري)", about, { about = it; error = "" }, fg, singleLine = false)
            if (error.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            GlassButton(
                onClick = {
                    if (busy) return@GlassButton
                    if (name.isBlank()) { error = "اكتب اسم المجموعة"; return@GlassButton }
                    if (groupId.length < 5) { error = "المعرّف لازم يكون 5 أحرف أو أرقام على الأقل"; return@GlassButton }
                    busy = true
                    scope.launch {
                        try {
                            Repo.createGroup(name, groupId, about, photo)
                            created = true
                            onClose()
                        } catch (e: Exception) {
                            error = e.message ?: "تعذّر إنشاء المجموعة"
                        } finally { busy = false }
                    }
                },
                backdrop = backdrop,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = fg, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Filled.Check, null, tint = fg)
                    Spacer(Modifier.width(8.dp))
                    Text("إنشاء المجموعة", color = fg, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GroupInput(
    label: String, value: String, onValueChange: (String) -> Unit, color: Color,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = singleLine,
        minLines = if (singleLine) 1 else 3, maxLines = if (singleLine) 1 else 5,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = color, unfocusedTextColor = color,
            focusedBorderColor = Color(0xFF38A9F5), unfocusedBorderColor = color.copy(alpha = 0.25f),
            focusedLabelColor = Color(0xFF38A9F5), unfocusedLabelColor = color.copy(alpha = 0.65f),
            cursorColor = Color(0xFF38A9F5)
        )
    )
}
