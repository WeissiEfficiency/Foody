// App-Hülle der V3 („Foody Debug“): Navigation, Hilt-Zusammenbau, Sync-Verdrahtung, Startdaten (V3, Phase 5).
// Läuft neben der v1 (:app) und wird in Phase 8 zu :app. Gleiche App-ID wie die v1, damit der Umstieg die
// vorhandene Datenbank übernimmt; der Release-Build ist bis dahin abgeschaltet, damit nie die Alltags-App
// überschrieben wird.
plugins {
    alias(libs.plugins.foody.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "de.foody.app3"

    defaultConfig {
        applicationId = "de.foody.app"
        versionCode = 1
        versionName = "3.0.0-entwicklung"
    }

    androidResources {
        localeFilters += listOf("de")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Robolectric braucht die Android-Ressourcen in Unit-Tests
    testOptions.unitTests.isIncludeAndroidResources = true

    // Lint stürzt beim Analysieren der Hilt-Testklassen ab (interner Kotlin-Analysefehler „RAW_FIR“); der
    // Produktivcode wird weiter voll geprüft.
    lint { ignoreTestSources = true }
}

// Bis Phase 8 kein Release: ein versehentlich installierter Release-Build würde die Alltags-App (v1) ersetzen.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enable = false }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:sync"))
    implementation(project(":feature:heute"))
    implementation(project(":feature:rezepte"))
    implementation(project(":feature:einstellungen"))
    implementation(project(":domain"))

    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(bom)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}
