// Erzeugt das Baseline-Profil für :app (V3) – die Liste der Methoden, die beim Start und in den Haupt-Tabs gebraucht
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
    // SDK-Stände, Java 17 und Kotlin ohne Warnungen aus build-logic (wie alle Module)
    alias(libs.plugins.foody.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "de.foody.baselineprofile"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // Kurztest der R8-Variante gegen „Foody Vorschau“ (ReleaseStartTest, E4); braucht denselben Build-Typ wie :app
    buildTypes {
        create("vorschau")
    }

    testOptions {
        // Gradle Managed Device wie in :core:data: in der CI startet Gradle den Emulator selbst
        managedDevices {
            localDevices {
                create("ciDevice") {
                    device = "Pixel 6"
                    apiLevel = 36
                    systemImageSource = "google"
                }
            }
        }
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
