// Einstellungen der V3; in Phase 5 nur „Server verbinden“, der Rest folgt in Phase 6.
plugins {
    alias(libs.plugins.foody.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "de.foody.feature.einstellungen"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:sync"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
}
