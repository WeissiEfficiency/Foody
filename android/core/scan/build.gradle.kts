// Packung scannen (V3 Phase 6e3, ADR 0013): Ablauf, Open Food Facts, Play-Dienste (Code Scanner, Texterkennung),
// Einstellung „Online-Produktsuche“ und der Scan-Knopf. Das Feature-Plugin bringt Compose und die Robolectric-Tests mit.
plugins {
    alias(libs.plugins.foody.android.feature)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "de.foody.core.scan"
}

dependencies {
    // api: Packungswerte (aus :core:data) und Nutrient (aus :domain) stehen in der öffentlichen Schnittstelle
    api(project(":core:data"))
    api(project(":domain"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Open Food Facts: eigener Ktor-Client über OkHttp, Antwort selbst ausgewertet
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.serialization.json)
    // Google Code Scanner und Texterkennung über die Play-Dienste (ADR 0008)
    implementation(libs.play.services.base)
    implementation(libs.play.services.code.scanner)
    implementation(libs.play.services.mlkit.text.recognition)
    implementation(libs.coroutines.play.services)
    // Kamera und Galerie im Scan-Knopf
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.ktor.client.mock)
}
