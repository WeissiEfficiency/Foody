// Startseite „Heute“ und Tagebuch der V3 (Phase 5).
plugins {
    alias(libs.plugins.foody.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "de.foody.feature.heute"
    testOptions.unitTests.isIncludeAndroidResources = true
}

roborazzi {
    // Referenzbilder liegen im Repo; die CI vergleicht mit verifyRoborazziDebug.
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:sync"))
    implementation(project(":domain"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    // Rezeptfotos (file:-Links aus der Fotoablage)
    implementation(libs.coil.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    // Fotos im Test ohne Dekodieren (Robolectric kann unter Windows keine Bilder dekodieren)
    testImplementation(libs.coil.test)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
