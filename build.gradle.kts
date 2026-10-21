plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.google.services) apply false
}

// ---------------------------------------------------------------------------
// 16 KB ELF segment alignment for bundled native libraries.
//
// Third-party prebuilt .so files (e.g. tech.turso:libsql) may have PT_LOAD
// segments that are not 16 KB aligned, which breaks devices using 16 KB page
// sizes. This must run on the *native libs* stage — never on the finished APK:
// rewriting an APK after packaging destroys its v1/v2/v3 signature and makes
// Android report "App not installed as package appears to be invalid".
// ---------------------------------------------------------------------------
fun locatePython(): String? {
    for (candidate in listOf(System.getenv("PYTHON"), "python3", "python")) {
        if (candidate.isNullOrBlank()) continue
        try {
            val proc = ProcessBuilder(candidate, "--version").start()
            if (proc.waitFor() == 0) return candidate
        } catch (_: Exception) {
        }
    }
    return null
}

subprojects {
    if (name != "app-farmer" && name != "app-vet") return@subprojects

    val aligner = rootProject.file("scripts/align-elf-16k.py")
    if (!aligner.exists()) return@subprojects

    tasks.matching { it.name.startsWith("strip") && it.name.endsWith("DebugSymbols") }
        .configureEach {
            // Alignment edits the task output in place; never let Gradle restore
            // a cached (possibly unaligned) copy without re-running this step.
            outputs.cacheIf { false }
            val taskName = name
            // "stripDebugDebugSymbols" -> variant "debug"
            val variant = taskName
                .removePrefix("strip")
                .removeSuffix("DebugSymbols")
                .lowercase()
            val libRoot = layout.buildDirectory
                .dir("intermediates/stripped_native_libs/$variant/$taskName/out/lib")
                .get().asFile

            doLast {
                if (!libRoot.isDirectory) return@doLast
                val python = locatePython() ?: run {
                    logger.warn("16KB alignment skipped: no python3/python on PATH")
                    return@doLast
                }
                libRoot.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".so") }
                    .forEach { so ->
                        // A single pass can leave a later PT_LOAD unaligned when
                        // padding shifts the segments; repeat until stable.
                        var output = ""
                        var ok = true
                        for (pass in 1..3) {
                            val proc = ProcessBuilder(python, aligner.absolutePath, so.absolutePath)
                                .redirectErrorStream(true)
                                .start()
                            output = proc.inputStream.bufferedReader().readText().trim()
                            proc.waitFor()
                            if (proc.exitValue() != 0) {
                                ok = false
                                break
                            }
                            if (output == "ALREADY_ALIGNED") break
                        }
                        if (!ok) {
                            logger.warn("16KB alignment failed for ${so.name}: $output")
                        } else {
                            logger.lifecycle("16KB alignment ${so.name}: $output")
                        }
                    }
            }
        }
}
