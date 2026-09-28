plugins {
    alias(libs.plugins.android.application)
}

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Per-ABI APKs: native libs are ~35MB per ABI, so a device-only APK is
    // ~100MB lighter and installs much faster than the fat universal APK.
    // The universal APK is still produced for emulators and unknown devices.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
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
    description = "Aligns native .so inside the debug APKs to 16KB boundaries"
    doLast {
        val apkDir = File(project.projectDir, "build/outputs/apk/debug")
        val apks = apkDir.listFiles { f -> f.extension == "apk" } ?: return@doLast
        val aligner = project.rootProject.file("scripts/align-elf-16k.py").absolutePath
        val apktool = project.rootProject.file("scripts/align-apk.py").absolutePath
        val python = findPython()
        apks.forEach { apk ->
            val proc = ProcessBuilder(listOf(python, apktool, apk.absolutePath, python, aligner)).start()
            proc.waitFor()
            println("alignDebugApk: ${apk.name} exit=${proc.exitValue()}")
        }
    }
}

tasks.register<DefaultTask>("alignReleaseApk") {
    group = "custom"
    description = "Aligns native .so inside the release APKs to 16KB boundaries"
    doLast {
        val apkDir = File(project.projectDir, "build/outputs/apk/release")
        val apks = apkDir.listFiles { f -> f.extension == "apk" } ?: return@doLast
        val aligner = project.rootProject.file("scripts/align-elf-16k.py").absolutePath
        val apktool = project.rootProject.file("scripts/align-apk.py").absolutePath
        val python = findPython()
        apks.forEach { apk ->
            val proc = ProcessBuilder(listOf(python, apktool, apk.absolutePath, python, aligner)).start()
            proc.waitFor()
            println("alignReleaseApk: ${apk.name} exit=${proc.exitValue()}")
        }
    }
}

afterEvaluate {
    tasks.named<Task>("createDebugApkListingFileRedirect").configure { dependsOn(tasks.named<DefaultTask>("alignDebugApk").get()) }
    tasks.named<DefaultTask>("alignDebugApk").configure { dependsOn("packageDebug") }
    tasks.named<Task>("createReleaseApkListingFileRedirect").configure { dependsOn(tasks.named<DefaultTask>("alignReleaseApk").get()) }
    tasks.named<DefaultTask>("alignReleaseApk").configure { dependsOn("packageRelease") }
}
