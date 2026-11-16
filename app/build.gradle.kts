plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room { schemaDirectory("$projectDir/schemas") }

android {
    namespace = "io.github.muzammilawan.clipharbor"

    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.muzammilawan.clipharbor"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "0.1.0-phase1-preview"

        buildConfigField("String", "GIT_SHA", "\"${(findProperty("gitSha") as String?) ?: "unknown"}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The engine ships native payloads for arm64-v8a, armeabi-v7a, x86 and x86_64; the Phase 0 phone is arm64,
        // and shipping one ABI keeps the APK near 50 MB instead of ~200 MB (docs/RESEARCH-LOG.md R14).
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    packaging {
        // youtubedl-android reads libpython.zip.so / libffmpeg.zip.so by path from nativeLibraryDir, so the native
        // libraries must stay extracted (docs/RESEARCH-LOG.md R20).
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
