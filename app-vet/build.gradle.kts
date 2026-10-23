import java.util.Properties

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

// Release signing: reads root keystore.properties. Falls back to the debug
// signing config so `assembleRelease` always produces an installable (signed)
// APK even when the release keystore has not been configured.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
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
        versionCode = 7
        versionName = "1.6"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
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
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
}
