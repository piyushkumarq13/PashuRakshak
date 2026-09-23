plugins {
    alias(libs.plugins.android.application)
}

// Vet app — applicationId com.pashurakshak.vet. Register this package in the
// Firebase Console and drop google-services.json here to enable FCM push;
// the project builds fine without it (the plugin is only applied when the
// file exists).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    // R/BuildConfig namespace must differ from :core's (com.pashurakshak.app).
    namespace = "com.pashurakshak.app.vet"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pashurakshak.vet"
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
