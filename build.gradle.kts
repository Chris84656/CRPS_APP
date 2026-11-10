// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val hostRegressionRuntime by configurations.creating
dependencies {
    hostRegressionRuntime("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    hostRegressionRuntime("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:${libs.versions.coroutines.get()}")
    hostRegressionRuntime("org.json:json:20240303")
}

tasks.register("hostRegression") {
    group = "verification"
    description = "Run source-based BLE and ViewModel checks with Android/GATT adapters (requires Python 3)."
    doLast {
        val toolchain = layout.buildDirectory.dir("host-regression/toolchain").get().asFile
        toolchain.mkdirs()
        val javaName = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
        toolchain.resolve("dependency-manifest.json").writeText(groovy.json.JsonOutput.toJson(mapOf(
            "java" to file("${System.getProperty("java.home")}/bin/$javaName").absolutePath,
            "jars" to hostRegressionRuntime.files.map { mapOf("path" to it.absolutePath) },
        )))
        for (script in listOf("check_android_state.py", "check_android_connection.py")) {
            project.exec {
                commandLine("python3", "tests/$script")
                environment("CRPS_ANDROID_TOOLCHAIN", toolchain.absolutePath)
            }
        }
    }
}
