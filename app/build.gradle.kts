plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Reads secrets from local.properties — keep that file out of VCS (it is in .gitignore;
// see also local.properties.example). Values are empty until you fill them in.
import java.util.Properties

// Applies the Google Services plugin only when a real google-services.json is present,
// so the project still builds before Firebase is configured
// (copy google-services.json.example → google-services.json; see README).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localProp(name: String): String =
    (localProperties.getProperty(name) ?: "").replace("\\", "\\\\")

android {
    namespace = "com.pashurakshak.app"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pashurakshak.app"
        minSdk {
            version = release(24)
        }
        targetSdk {
            version = release(37)
        }
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "B2_APPLICATION_KEY_ID", "\"${localProp("B2_APPLICATION_KEY_ID")}\"")
        buildConfigField("String", "B2_APPLICATION_KEY", "\"${localProp("B2_APPLICATION_KEY")}\"")
        buildConfigField("String", "B2_BUCKET_NAME", "\"${localProp("B2_BUCKET_NAME")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${localProp("API_BASE_URL")}\"")
        buildConfigField("String", "APP_API_KEY", "\"${localProp("APP_API_KEY")}\"")
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
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)

    debugImplementation(libs.compose.ui.tooling)
}
