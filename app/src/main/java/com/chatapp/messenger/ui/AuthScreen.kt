package com.chatapp.messenger.ui

import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.chatapp.messenger.R
import com.chatapp.messenger.Repo
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.chatapp.messenger.ui.glass.GlassButton
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** يحوّل أخطاء Firebase لرسائل عربية مفهومة */
private fun authErrorMessage(e: Exception): String = when (e) {
    is FirebaseAuthWeakPasswordException -> "كلمة المرور ضعيفة، اختر كلمة أقوى"
    is FirebaseAuthUserCollisionException -> "هذا البريد مسجّل من قبل، سجّل دخول بدل إنشاء حساب"
    is FirebaseAuthInvalidUserException ->
        if (e.errorCode == "ERROR_USER_DISABLED") "هذا الحساب معطّل" else "ما لقينا حساب بهذا البريد"
    is FirebaseAuthInvalidCredentialsException -> "البريد أو كلمة المرور غير صحيحة"
    is FirebaseTooManyRequestsException -> "محاولات كثيرة، انتظر شوية وجرّب مرة ثانية"
    is FirebaseNetworkException -> "ما في اتصال بالإنترنت، تأكد من الشبكة"
    is FirebaseAuthException -> when (e.errorCode) {
        "ERROR_INVALID_EMAIL" -> "البريد الإلكتروني غير صحيح"
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "البريد أو كلمة المرور غير صحيحة"
        "ERROR_USER_NOT_FOUND" -> "ما لقينا حساب بهذا البريد"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "هذا البريد مسجّل من قبل"
        "ERROR_OPERATION_NOT_ALLOWED" -> "تسجيل الدخول بالبريد غير مفعّل بإعدادات Firebase"
        else -> e.localizedMessage ?: "صار خطأ، جرّب مرة ثانية"
    }
    else -> e.localizedMessage ?: "صار خطأ، جرّب مرة ثانية"
}

/** شاشة تسجيل الدخول / إنشاء حساب بتصميم Liquid Glass */
@Composable
fun AuthScreen() {
    var register by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // كلمات المرور ما تنحفظ بأي مكان (حتى لو دار الجهاز)
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    // هزّة اللوحة عند الخطأ (انيميشن تسجيل الدخول)
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error != null) {
            for (target in floatArrayOf(-14f, 14f, -10f, 10f, -5f, 5f, 0f)) {
                shake.animateTo(target, tween(55))
            }
        }
    }

    fun submit() {
        if (loading) return
        val e = email.trim()
        val n = name.trim()
        val problem = when {
            register && n.length < 2 -> "اكتب اسمك (حرفين على الأقل)"
            !Patterns.EMAIL_ADDRESS.matcher(e).matches() -> "البريد الإلكتروني غير صحيح"
            password.length < 6 -> "كلمة المرور لازم تكون 6 أحرف على الأقل"
            register && password != confirm -> "كلمتا المرور غير متطابقتين"
            else -> null
        }
        if (problem != null) {
            error = problem
            info = null
            return
        }
        loading = true
        error = null
        info = null
        val pass = password
        val isRegister = register
        Repo.appScope.launch {
            try {
                if (isRegister) {
                    val res = Repo.auth.createUserWithEmailAndPassword(e, pass).await()
                    // فشل هذي الخطوات ما يلغي إنشاء الحساب (التفعيل يقدر يعاد إرساله من شاشة التفعيل)
                    try {
                        res.user?.updateProfile(
                            UserProfileChangeRequest.Builder().setDisplayName(n).build()
                        )?.await()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (_: Exception) {
                    }
                    try {
                        res.user?.sendEmailVerification()?.await()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (_: Exception) {
                    }
                } else {
                    Repo.auth.signInWithEmailAndPassword(e, pass).await()
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                error = authErrorMessage(ex)
            }
            loading = false
        }
    }

    val context = LocalContext.current

    /**
     * الدخول / إنشاء حساب عبر گوگل.
     * Firebase يربط الحساب تلقائياً: إذا بريد گوگل مطابق لبريد حساب موجود، يدخلك لنفس الحساب (نفس الـ uid).
     */
    fun signInWithGoogle() {
        if (loading) return
        loading = true
        error = null
        info = null
        Repo.appScope.launch {
            try {
                val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = CredentialManager.create(context).getCredential(context, request)
                val cred = result.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    val res = Repo.auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                    // حساب گوگل بريده مفعّل: نحدّث المستخدم والتوكن حتى ما يعلق بشاشة التفعيل
                    try {
                        res.user?.reload()?.await()
                        res.user?.getIdToken(true)?.await()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (_: Exception) {
                    }
                } else {
                    error = "نوع حساب غير مدعوم"
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: GetCredentialCancellationException) {
                // غالباً SHA-1 مو مضاف بـ Firebase أو google-services.json قديم (يرجع "إلغاء" بعد اختيار الحساب)
                error = "ما كمل الدخول عبر گوگل. إذا اخترت حسابك وما صار شي، تأكد من إضافة SHA-1 بـ Firebase وتحديث google-services.json"
            } catch (_: NoCredentialException) {
                error = "ما لقينا حساب گوگل على الجهاز، ضيف حساب من إعدادات الجهاز وجرّب"
            } catch (ex: GetCredentialException) {
                android.util.Log.e("GoogleSignIn", "credential error", ex)
                error = "تعذّر الدخول عبر گوگل (${ex.type}): ${ex.message ?: ""}"
            } catch (ex: Exception) {
                android.util.Log.e("GoogleSignIn", "firebase error", ex)
                error = authErrorMessage(ex) + " [${ex.javaClass.simpleName}]"
            }
            loading = false
        }
    }

    fun resetPassword() {
        val e = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
            error = "اكتب بريدك الصحيح أولاً حتى نرسل لك رابط استرجاع كلمة المرور"
            info = null
            return
        }
        if (loading) return
        loading = true
        error = null
        info = null
        Repo.appScope.launch {
            try {
                Repo.auth.sendPasswordResetEmail(e).await()
                info = "أرسلنا رابط استرجاع كلمة المرور إلى بريدك (شوف مجلد Spam أيضاً)"
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                error = authErrorMessage(ex)
            }
            loading = false
        }
    }

    GlassAuthScaffold { backdrop, dark ->
        val fg = glassFg(dark)

        // الشعار
        androidx.compose.foundation.layout.Box(
            Modifier.size(88.dp).glassCircle(backdrop, dark),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.ChatBubble, null, tint = GlassAccent, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(18.dp))
        // عنوان + وصف يتبدلون بانزلاق أفقي حسب اتجاه التبديل
        AnimatedContent(
            targetState = register,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (slideInHorizontally(tween(320)) { it / 3 * dir } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 3 * dir } + fadeOut(tween(180)))
            },
            label = "authTitle"
        ) { reg ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (reg) "إنشاء حساب جديد" else "أهلاً بيك",
                    color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (reg) "سجّل بياناتك وابدأ المحادثة" else "سجّل دخول حتى تكمل محادثاتك",
                    color = fg.copy(alpha = 0.65f), fontSize = 15.sp, textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(22.dp))

        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // مبدّل: دخول | حساب جديد
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    onClick = { register = false; error = null; info = null },
                    backdrop = backdrop,
                    tint = if (!register) GlassAccent else Color.Unspecified,
                    pressScale = 1.04f,
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text("تسجيل دخول", color = if (!register) Color.White else fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                GlassButton(
                    onClick = { register = true; error = null; info = null },
                    backdrop = backdrop,
                    tint = if (register) GlassAccent else Color.Unspecified,
                    pressScale = 1.04f,
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text("حساب جديد", color = if (register) Color.White else fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))

            // اللوحة الزجاجية (تهتز عند الخطأ)
            Column(
                Modifier.fillMaxWidth()
                    .graphicsLayer { translationX = shake.value.dp.toPx() }
                    .glassPanel(backdrop, dark).padding(16.dp)
            ) {
                // الاسم: يظهر بتمدد ناعم عند "حساب جديد" ويختفي عند الدخول
                AnimatedVisibility(
                    visible = register,
                    enter = expandVertically(spring(0.85f, 380f)) + fadeIn(tween(260)),
                    exit = shrinkVertically(tween(220)) + fadeOut(tween(160))
                ) {
                    GlassField(
                        value = name, onValueChange = { name = it.take(30) },
                        placeholder = "الاسم", icon = Icons.Filled.Person,
                        backdrop = backdrop, dark = dark,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                GlassField(
                    value = email, onValueChange = { email = it.replace(" ", "") },
                    placeholder = "البريد الإلكتروني", icon = AppIcons.Email,
                    backdrop = backdrop, dark = dark,
                    keyboardType = KeyboardType.Email
                )
                Spacer(Modifier.height(12.dp))
                GlassField(
                    value = password, onValueChange = { password = it },
                    placeholder = "كلمة المرور (6 أحرف على الأقل)", icon = AppIcons.Lock,
                    backdrop = backdrop, dark = dark,
                    password = true,
                    imeAction = if (register) ImeAction.Next else ImeAction.Done,
                    onDone = { if (!register) submit() }
                )
                AnimatedVisibility(
                    visible = register,
                    enter = expandVertically(spring(0.85f, 380f)) + fadeIn(tween(260)),
                    exit = shrinkVertically(tween(220)) + fadeOut(tween(160))
                ) {
                    GlassField(
                        value = confirm, onValueChange = { confirm = it },
                        placeholder = "تأكيد كلمة المرور", icon = AppIcons.Lock,
                        backdrop = backdrop, dark = dark,
                        password = true,
                        imeAction = ImeAction.Done,
                        onDone = { submit() },
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                AnimatedVisibility(
                    visible = error != null,
                    enter = expandVertically(spring(0.85f, 380f)) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
                ) {
                    Text(
                        error ?: "",
                        color = if (dark) Color(0xFFFF8A8A) else Color(0xFFC62828),
                        fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp)
                    )
                }
                AnimatedVisibility(
                    visible = info != null,
                    enter = expandVertically(spring(0.85f, 380f)) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
                ) {
                    Text(
                        info ?: "",
                        color = if (dark) Color(0xFF7DF0B8) else Color(0xFF1B7F4B),
                        fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                GlassButton(
                    onClick = { submit() },
                    backdrop = backdrop,
                    enabled = !loading,
                    tint = GlassAccent,
                    pressScale = 1.03f,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    // الزر: النص يتحول لمؤشر تحميل بحركة تكبير/تصغير
                    AnimatedContent(
                        targetState = loading,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(spring(0.7f, 500f), initialScale = 0.6f)) togetherWith
                                (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f))
                        },
                        label = "authBtn"
                    ) { busy ->
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                if (register) "إنشاء الحساب" else "دخول",
                                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // فاصل "أو"
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f).height(1.dp).background(fg.copy(alpha = 0.25f)))
                    Text("أو", color = fg.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(fg.copy(alpha = 0.25f)))
                }
                // زر گوگل
                GlassButton(
                    onClick = { signInWithGoogle() },
                    backdrop = backdrop,
                    enabled = !loading,
                    pressScale = 1.03f,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    GoogleLogo(22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Sign Google",
                        color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))

                AnimatedVisibility(
                    visible = !register,
                    enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                ) {
                    Text(
                        "نسيت كلمة المرور؟",
                        color = GlassAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !loading) { resetPassword() }.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}


/** شعار گوگل الرسمي (G) مرسوم بالـ Canvas */
@Composable
fun GoogleLogo(size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val k = this.size.width / 48f
        val parts = listOf(
            "M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84c-.51 2.75-2.06 5.08-4.39 6.64v5.52h7.11c4.16-3.83 6.56-9.47 6.56-16.17z" to Color(0xFF4285F4),
            "M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.11-5.52c-1.97 1.32-4.49 2.1-7.45 2.1-5.73 0-10.58-3.87-12.31-9.07H4.34v5.7C7.96 41.07 15.4 46 24 46z" to Color(0xFF34A853),
            "M11.69 28.18C11.25 26.86 11 25.45 11 24s.25-2.86.69-4.18v-5.7H4.34C2.85 17.09 2 20.45 2 24c0 3.55.85 6.91 2.34 9.88l7.35-5.7z" to Color(0xFFFBBC05),
            "M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 2 7.96 6.93 4.34 14.12l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z" to Color(0xFFEA4335)
        )
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            for ((d, c) in parts) {
                drawPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath(), c)
            }
        }
    }
}
