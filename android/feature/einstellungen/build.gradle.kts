// Einstellungen der V3: Seite mit Konto, Darstellung, Tagesziel, Zutaten (Katalog, Online-Produktsuche), Sicherung,
// Löschen (6e1) und „Server verbinden“.
plugins {
    alias(libs.plugins.foody.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "de.foody.feature.einstellungen"
    testOptions.unitTests.isIncludeAndroidResources = true
}

roborazzi {
    // Referenzbilder liegen im Repo; die CI vergleicht mit verifyRoborazziDebug.
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:sync"))
    // Darstellung, Tagesziel, Sicherung
    implementation(project(":core:data"))
    // Schalter „Online-Produktsuche“ (ScanEinstellung)
    implementation(project(":core:scan"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    // Dateidialoge für Sicherung und Wiederherstellen
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
