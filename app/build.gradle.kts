plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val harnessVersionName: String = (System.getenv("HARNESS_VERSION_NAME") ?: "0.1.0")
val harnessVersionCode: Int = (System.getenv("HARNESS_VERSION_CODE")?.toIntOrNull() ?: 1)

android {
    namespace = "dev.harness"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.harness"
        minSdk = 26
        targetSdk = 36
        versionCode = harnessVersionCode
        versionName = harnessVersionName

        // Only arm64-v8a keeps the APK small; PRoot + Alpine are aarch64-only here.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            val storeB64 = System.getenv("HARNESS_KEYSTORE_BASE64")
            if (!storeB64.isNullOrBlank()) {
                val ksFile = layout.buildDirectory.file("harness-release.keystore").get().asFile
                ksFile.parentFile.mkdirs()
                ksFile.writeBytes(java.util.Base64.getDecoder().decode(storeB64.trim()))
                storeFile = ksFile
                storePassword = System.getenv("HARNESS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HARNESS_KEY_ALIAS") ?: "harness"
                keyPassword = System.getenv("HARNESS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Fall back to the debug key when no release keystore is configured so the
            // APK is always installable (see docs/ARCHITECTURE.md).
            signingConfig = if (!System.getenv("HARNESS_KEYSTORE_BASE64").isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // PRoot relies on libproot.so being extractable at a path the linker can exec.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
