plugins {
    alias(libs.plugins.android.application)
}

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import java.io.File

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

fun findPython(): String {
    for (p in listOf(System.getenv("PYTHON"), "python3", "python")) {
        if (p != null) {
            try {
                val proc = ProcessBuilder(p, "--version").start()
                if (proc.waitFor() == 0) return p
            } catch (_: Exception) {}
        }
    }
    error("No python3/python found; required for 16KB ELF alignment")
}
tasks.register<DefaultTask>("alignDebugApk") {
    group = "custom"
    description = "Aligns native .so inside the debug APK to 16KB boundaries"
    doLast {
        val apk = File(project.projectDir, "build/outputs/apk/debug/app-vet-debug.apk")
        if (!apk.exists()) return@doLast
        val aligner = project.rootProject.file("scripts/align-elf-16k.py").absolutePath
        val apktool = project.rootProject.file("scripts/align-apk.py").absolutePath
        val python = findPython()
        val proc = ProcessBuilder(listOf(python, apktool, apk.absolutePath, python, aligner)).start()
        proc.waitFor()
        println("alignDebugApk: exit=${proc.exitValue()}")
    }
}
afterEvaluate {
    tasks.named<Task>("createDebugApkListingFileRedirect").configure { dependsOn(tasks.named<DefaultTask>("alignDebugApk").get()) }
    tasks.named<DefaultTask>("alignDebugApk").configure { dependsOn("packageDebug") }
}
