
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
}

// GitHub Actions automatic versioning
val autoVersionCode = System.getenv("VERSION_CODE")
    ?.toIntOrNull() ?: 1

val autoVersionName = System.getenv("VERSION_NAME")
    ?.takeIf { it.isNotBlank() } ?: "1.0.0"

// Release signing environment
val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
val releaseStorePassword = System.getenv("STORE_PASSWORD")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val releaseKeyAlias = System.getenv("KEY_ALIAS")

val releaseSigningAvailable =
    !releaseKeystorePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    file(releaseKeystorePath).exists()

android {
    namespace = "com.example"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mahir.engai"

        minSdk = 24
        targetSdk = 36

        versionCode = autoVersionCode
        versionName = autoVersionName

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        // Backend URL configuration
        val backendBaseUrl = run {
            val defaultUrl = "https://engwithai.vercel.app"

            fun readKey(file: java.io.File): String? {
                if (!file.exists()) return null

                return file.readLines()
                    .firstOrNull {
                        it.trimStart()
                            .startsWith("BACKEND_BASE_URL=")
                    }
                    ?.substringAfter("=")
                    ?.trim()
                    ?.trim('"', '\'')
            }

            val fromEnv = readKey(rootProject.file(".env"))

            val fromSystem =
                System.getenv("BACKEND_BASE_URL")?.trim()

            val fromExample =
                readKey(rootProject.file(".env.example"))

            listOfNotNull(
                fromEnv,
                fromSystem,
                fromExample,
                defaultUrl
            )
                .map { it.trim().trimEnd('/') }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }

        val escapedBackendUrl = backendBaseUrl
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

        buildConfigField(
            "String",
            "BACKEND_BASE_URL",
            "\"$escapedBackendUrl\""
        )
    }

    // Signing configurations
    signingConfigs {

        create("release") {
            if (releaseSigningAvailable) {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }

        create("debugConfig") {
            storeFile = file("${rootProject.projectDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {

        release {
            isCrunchPngs = false
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )

            if (releaseSigningAvailable) {
                signingConfig =
                    signingConfigs.getByName("release")
            }
        }

        debug {
            signingConfig =
                signingConfigs.getByName("debugConfig")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

// Secrets Gradle Plugin
secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"

    ignoreList.addAll(
        listOf(
            "FIREBASE_APPCHECK_DEBUG_TOKEN",
            "FIREBASE_PRIVATE_KEY",
            "FIREBASE_CLIENT_EMAIL",
            "FIREBASE_PROJECT_ID",
            "MONGODB_URI",
            "AI_PROVIDER",
            "AI_API_KEY",
            "API_BASE_URL",
            "BACKEND_BASE_URL",
            "CLOUDINARY_API_KEY",
            "CLOUDINARY_API_SECRET",
            "CLOUDINARY_CLOUD_NAME",
            "CALL_PROVIDER",
            "CALL_P2P_MAX_PARTICIPANTS",
            "CALL_SFU_MAX_PARTICIPANTS",
            "TURN_SERVER_URL",
            "TURN_USERNAME",
            "TURN_CREDENTIAL",
            "LIVEKIT_URL",
            "LIVEKIT_API_KEY",
            "LIVEKIT_API_SECRET",
            "AGORA_APP_ID",
            "DAILY_API_KEY",
            "DAILY_DOMAIN",
            "SSLCOMMERZ_STORE_ID",
            "SSLCOMMERZ_STORE_PASSWORD",
            "SSLCOMMERZ_SANDBOX",
            "SSLCOMMERZ_CURRENCY",
            "SSLCOMMERZ_DEFAULT_PRICE"
        )
    )
}

// Firebase Google Services
googleServices {
    missingGoogleServicesStrategy =
        MissingGoogleServicesStrategy.WARN
}

// Dependencies
dependencies {

    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    // AndroidX Compose
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // AndroidX Core and Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Firebase
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)

    // Google Credentials
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Network & Media
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.stream.webrtc.android)

    // Room Offline Storage
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Unit Testing
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    // Instrumentation Testing
    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)

    // Debug Tools
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
