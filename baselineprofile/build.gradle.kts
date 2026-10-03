// Erzeugt das Baseline-Profil für :app – die Liste der Methoden, die beim Start und in den Haupt-Tabs gebraucht
// werden. ART kompiliert sie schon bei der Installation vorab, statt sie erst beim Benutzen zu interpretieren.
//
// Neu erzeugen (Emulator/Gerät mit Android 13+ verbunden):
//   ./gradlew :app:generateReleaseBaselineProfile
// Ergebnis: app/src/release/generated/baselineProfiles/baseline-prof.txt – wird eingecheckt; normale Builds
// und die CI brauchen dafür kein Gerät.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "de.foody.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
}
