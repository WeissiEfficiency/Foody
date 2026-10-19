// Katalog: zeigt alle Komponenten des V3-Designsystems in allen Zuständen; nur zum Ansehen und für
// Screenshot-Tests, wird nie ausgeliefert (V3, Phase 4).
plugins {
    alias(libs.plugins.foody.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "de.foody.katalog"

    defaultConfig {
        applicationId = "de.foody.katalog"
        versionCode = 1
        versionName = "1"
    }

    buildFeatures.compose = true
    // Robolectric braucht die Android-Ressourcen in Unit-Tests
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":core:designsystem"))

    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.androidx.activity.compose)

    testImplementation(bom)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.compose.ui.test.manifest)
}
