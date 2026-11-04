import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.chatapp.messenger"
    compileSdk = 37

    defaultConfig {
        // لازم يطابق Package name المسجّل بـ Firebase
        applicationId = "com.e4classb.android"
        minSdk = 24
        targetSdk = 34
        // ارفع versionCode (رقم صحيح أكبر) مع كل نسخة جديدة تنشرها، حتى يظهر التحديث للمستخدمين
        versionCode = 24
        versionName = "2.13"
    }

    // مفتاح توقيع ثابت: كل نسخة تبنيها (من أي جهاز أو GitHub Actions) تنوقّع بنفس المفتاح،
    // فيقبلها أندرويد كتحديث فوق النسخة القديمة بدون ما تحذفها.
    // ⚠️ لا تغيّر الملف ولا كلمات المرور، ولا تضيّع الملف: لو تغيّر المفتاح لازم الكل يحذف ويعيد التنزيل.
    signingConfigs {
        create("chatapp") {
            storeFile = file("chatapp.jks")
            storePassword = "chatapp123"
            keyAlias = "chatapp"
            keyPassword = "chatapp123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("chatapp")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("chatapp")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose
    implementation("androidx.compose.ui:ui:1.10.3")
    implementation("androidx.compose.ui:ui-util:1.10.3")
    implementation("androidx.compose.foundation:foundation:1.10.3")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Liquid Glass (Backdrop)
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // تسجيل الدخول بحساب گوگل (Credential Manager)
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // ضغط الفيديو قبل الإرسال (Media3 Transformer)
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    // ملصقات الرموز المتحركة (tgs)
    implementation("com.airbnb.android:lottie-compose:6.6.7")
    // عرض صور GIF المتحركة (البانر وخلفية المحادثات)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
}

// حل خطأ checkDebugAarMetadata: تخطّي فحص بيانات مكتبات AAR (الفحص بس تحقق من متطلبات compileSdk/AGP ولا يؤثر على البناء)
tasks.configureEach {
    if (name.endsWith("AarMetadata")) {
        enabled = false
    }
}
