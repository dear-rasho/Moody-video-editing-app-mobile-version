plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.moody.moodyvideoeditor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.moody.moodyvideoeditor"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ═══════════════════════════════════════════════════════
        //  ABI FILTERS — only arm64 (fork's native libs)
        // ═══════════════════════════════════════════════════════
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

dependencies {

    // ═══════════════════════════════════════════════════════════
    //  CORE ANDROID
    // ═══════════════════════════════════════════════════════════
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ═══════════════════════════════════════════════════════════
    //  COMPOSE
    // ═══════════════════════════════════════════════════════════
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // ═══════════════════════════════════════════════════════════
    //  LIFECYCLE + VIEWMODEL
    // ═══════════════════════════════════════════════════════════
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ═══════════════════════════════════════════════════════════
    //  NAVIGATION
    // ═══════════════════════════════════════════════════════════
    implementation(libs.androidx.navigation.compose)

    // ═══════════════════════════════════════════════════════════
    //  MEDIA3
    // ═══════════════════════════════════════════════════════════
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // ═══════════════════════════════════════════════════════════
    //  COIL 2.x
    // ═══════════════════════════════════════════════════════════
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // ═══════════════════════════════════════════════════════════
    //  COROUTINES
    // ═══════════════════════════════════════════════════════════
    implementation(libs.kotlinx.coroutines.android)

    // ═══════════════════════════════════════════════════════════
    //  FFMPEG KIT — community maintained fork
    // ═══════════════════════════════════════════════════════════
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7")

    // ═══════════════════════════════════════════════════════════
    //  🆕 SMART EXCEPTION — REQUIRED by FFmpeg Kit
    //  Without this: NoClassDefFoundError crash
    // ═══════════════════════════════════════════════════════════
    implementation("com.arthenica:smart-exception-java:0.2.1")

    // ═══════════════════════════════════════════════════════════
    //  TESTING
    // ═══════════════════════════════════════════════════════════
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}