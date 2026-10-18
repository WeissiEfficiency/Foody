// Sync-Client der V3-App: Anmeldung, Push/Pull gegen /api/v3, Fotos (V3, Phase 3).
plugins {
    alias(libs.plugins.foody.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.foody.core.sync"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":api-protocol"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.coroutines.test)
    // Echter V3-Server im Test (Ende-zu-Ende-Sync gegen :server-sync)
    testImplementation(project(":server-sync"))
}
