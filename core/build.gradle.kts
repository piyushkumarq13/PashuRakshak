import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

// Shared Android library (:core) — all PashuRakshak code lives here.
// The thin :app-farmer and :app-vet application modules depend on it and only
// provide their applicationId, google-services.json (FCM) and app label.
//
// Secrets come from the ROOT local.properties (keep it out of VCS).

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localProp(name: String, default: String = ""): String =
    (localProperties.getProperty(name) ?: default).replace("\\", "\\\\")

// Default to the deployed backend so builds work even without local.properties.
val deployedBackendUrl = "https://pashurakshak-ilol.onrender.com"
// App key registered on the deployed backend for "PashuRakshak Android Phase3".
val deployedAppKey = "7eb0b9d170d142a4098b25f3dfa58f9a24698a5797eccb1ad015c3ea6c442d5e"

android {
    namespace = "com.pashurakshak.app"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk {
            version = release(24)
        }

        buildConfigField("String", "B2_APPLICATION_KEY_ID", "\"${localProp("B2_APPLICATION_KEY_ID")}\"")
        buildConfigField("String", "B2_APPLICATION_KEY", "\"${localProp("B2_APPLICATION_KEY")}\"")
        buildConfigField("String", "B2_BUCKET_NAME", "\"${localProp("B2_BUCKET_NAME")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${localProp("API_BASE_URL", deployedBackendUrl)}\"")
        buildConfigField("String", "APP_API_KEY", "\"${localProp("APP_API_KEY", deployedAppKey)}\"")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.turso.libsql)
    implementation(libs.play.services.location)
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)

    debugImplementation(libs.compose.ui.tooling)
}
