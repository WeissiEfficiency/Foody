// Designsystem der V3-App: Tokens (Ofenwarm), zwei Glasstufen mit Haze, gemeinsame Komponenten (V3, Phase 4).
// Das Feature-Plugin bringt Compose und die Robolectric-Testabhängigkeiten mit.
plugins {
    alias(libs.plugins.foody.android.feature)
}

android {
    namespace = "de.foody.core.designsystem"
}

dependencies {
    implementation(libs.haze)

    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
}
