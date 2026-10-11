// Erzeugt das Baseline-Profil für :app – die Liste der Methoden, die beim Start und in den Haupt-Tabs gebraucht
// werden. ART kompiliert sie schon bei der Installation vorab, statt sie erst beim Benutzen zu interpretieren.
//
// Neu erzeugen (genau ein Emulator/Gerät mit Android 13+ verbunden – nie eines mit echten Daten):
//   ./gradlew :baselineprofile:connectedNonMinifiedReleaseAndroidTest
//     -Pandroid.testInstrumentationRunnerArguments.class=de.foody.baselineprofile.BaselineProfileGenerator
//     -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
//     -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
//   adb pull /sdcard/Android/media/de.foody.baselineprofile/additional_test_output/BaselineProfileGenerator_generate-startup-prof.txt
//     android/app/src/release/generated/baselineProfiles/baseline-prof.txt
//   adb uninstall de.foody.baselineprofile
// (`:app:generateReleaseBaselineProfile` meldet zwar Erfolg, mit Benchmark 1.5 und AGP 9.4 wird die Datei aber vor
// dem Abholen mit der Test-App gelöscht.) Die Datei wird eingecheckt; normale Builds und die CI brauchen kein Gerät.
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
