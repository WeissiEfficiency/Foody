// Fotoerkennung auf dem Gerät (ADR 0017, Phase 2): Qwen3-VL über llama.cpp, Modelldateien, Ablauf mit Zeitlimit.
// Die native Bibliothek liegt fertig in nativ/ (die CI braucht kein NDK). Neu bauen nach einer Änderung an src/main/cpp:
//   ./gradlew :core:erkennung:nativUebernehmen -Pfoody.nativ
plugins {
    alias(libs.plugins.foody.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val nativBauen = providers.gradleProperty("foody.nativ").isPresent

android {
    namespace = "de.foody.core.erkennung"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    if (nativBauen) {
        ndkVersion = "30.0.16248370"
        defaultConfig {
            ndk { abiFilters += "arm64-v8a" }
            externalNativeBuild { cmake { arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_static") } }
        }
        externalNativeBuild {
            cmake {
                path("src/main/cpp/CMakeLists.txt")
                version = "3.31.6"
            }
        }
    } else {
        sourceSets {
            getByName("main").jniLibs.directories.add("$projectDir/nativ")
        }
        // Die Bibliothek in nativ/ ist schon ohne Symbole; ohne NDK könnte der Build sie ohnehin nicht kürzen
        packaging.jniLibs.keepDebugSymbols += "**/libfoody_erkennung.so"
    }
}

// Kopiert die gebaute, von Symbolen befreite Bibliothek nach nativ/ (danach einchecken)
tasks.register<Copy>("nativUebernehmen") {
    val gebaut = nativBauen
    doFirst { check(gebaut) { "nativUebernehmen braucht -Pfoody.nativ" } }
    if (nativBauen) dependsOn("stripReleaseDebugSymbols")
    from(layout.buildDirectory.dir("intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib"))
    include("arm64-v8a/libfoody_erkennung.so")
    into("$projectDir/nativ")
}

dependencies {
    api(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.coroutines.test)

    // Messung auf dem Handy (MessungTest, nur mit Instrumentation-Argumenten)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.coroutines.test)
}
