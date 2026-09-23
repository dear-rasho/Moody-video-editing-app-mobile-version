plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.moody.moodyvideoeditor"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.moody.moodyvideoeditor"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // ═══════════════════════════════════════════════════════════
    //  COMPOSE (Base)
    // ═══════════════════════════════════════════════════════════
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ═══════════════════════════════════════════════════════════
    //  ICONS (Movie, Cut, Duplicate, Undo, Redo, Volume icons)
    // ═══════════════════════════════════════════════════════════
    implementation("androidx.compose.material:material-icons-extended")

    // ═══════════════════════════════════════════════════════════
    //  NAVIGATION (Screen routing)
    // ═══════════════════════════════════════════════════════════
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // ═══════════════════════════════════════════════════════════
    //  VIEWMODEL (State management)
    // ═══════════════════════════════════════════════════════════
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // ═══════════════════════════════════════════════════════════
    //  COROUTINES (Background tasks)
    // ═══════════════════════════════════════════════════════════
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ═══════════════════════════════════════════════════════════
    //  MEDIA3 (Video playback + Export)
    // ═══════════════════════════════════════════════════════════
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")

    // ═══════════════════════════════════════════════════════════
    //  COIL (Thumbnail loading)
    // ═══════════════════════════════════════════════════════════
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // ═══════════════════════════════════════════════════════════
    //  PERMISSIONS (Accompanist)
    // ═══════════════════════════════════════════════════════════
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // ═══════════════════════════════════════════════════════════
    //  TESTING
    // ═══════════════════════════════════════════════════════════
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}