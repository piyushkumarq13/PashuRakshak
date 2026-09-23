plugins {
    alias(libs.plugins.android.application)
}

// Farmer app — applicationId must match the Firebase package in
// google-services.json (com.pashurakshak.app). Applies the Google Services
// plugin only when that file is present so the project builds before Firebase
// is configured (copy google-services.json.example → google-services.json).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    // R/BuildConfig namespace must differ from :core's (com.pashurakshak.app).
    namespace = "com.pashurakshak.app.farmer"

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
}

dependencies {
    implementation(project(":core"))
}
