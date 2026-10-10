import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

// Signaturschlüssel für Release-Builds: Pfade und Passwörter liegen in keystore.properties (nicht im Repository,
// Vorlage: keystore.properties.example). Fehlt die Datei – etwa in der CI –, bleibt der Release-Build unsigniert.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "de.foody.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.foody.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Foody ist nur deutsch: Übersetzungen der Bibliotheken (AndroidX, Play-Dienste, …) in Dutzende Sprachen
    // weglassen. Die Standard-Ressourcen (`values/`) bleiben immer erhalten.
    androidResources {
        localeFilters += listOf("de")
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Eigene App-ID: Debug- und Alltagsversion laufen nebeneinander, ohne sich Daten zu teilen
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Gradle Managed Device: Gradle lädt das Systemabbild und startet den Emulator selbst –
        // so laufen die Room-/Migrationstests auch in der CI (./gradlew ciDeviceDebugAndroidTest).
        // ATD = schlankes, auf automatisierte Tests optimiertes Abbild.
        managedDevices {
            localDevices {
                create("ciDevice") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG: lokale http-Adressen nur im Debug-Build zulassen (ServerUrl)
        buildConfig = true
    }

    sourceSets {
        // Room-Schemata für Migrationstests
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Der Code ist warnungsfrei – so bleibt er es (z. B. neue Deprecations nach Bibliotheks-Updates).
        allWarningsAsErrors.set(true)
    }
}

// Room-Gradle-Plugin exportiert Schemata je Variante konfliktfrei (statt ksp-Arg room.schemaLocation).
room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
}

// SQLite 3.18 (Android API 26) für den Trigger-Kompatibilitätstest. Liegt bewusst nicht im Test-Klassenpfad:
// :server bringt eine neuere sqlite-jdbc mit, und beide Versionen sind dasselbe Artefakt. Der Test lädt die
// alte Version aus dieser Konfiguration in einem eigenen ClassLoader.
val legacySqlite by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

tasks.withType<Test>().configureEach {
    systemProperty("foody.legacySqliteJar", legacySqlite.singleFile.absolutePath)
}

dependencies {
    legacySqlite(libs.sqlite.jdbc.legacy)

    implementation(project(":domain"))
    implementation(project(":sync-protocol"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Drehung aus den Kamera-Metadaten, bevor Fotos verkleinert neu gespeichert werden
    implementation(libs.androidx.exifinterface)
    // Installiert das mitgelieferte Baseline-Profil auch bei APKs, die nicht über Google Play kommen
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material3.adaptive)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Hintergrund-Sync: WorkManager mit Hilt-Worker-Factory
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.coil.compose)
    // Sync-Client (optional, nur HTTPS); Bodies werden selbst mit kotlinx.serialization verarbeitet
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    // Nährwerte von der Packung: Strichcode und Texterkennung über die Google-Play-Dienste (Module werden nachgeladen)
    implementation(libs.play.services.base)
    implementation(libs.play.services.mlkit.text.recognition)
    implementation(libs.play.services.code.scanner)
    implementation(libs.coroutines.play.services)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    // Echter Server im Test (testApplication) für Client- und Ende-zu-Ende-Tests
    testImplementation(project(":server"))
    testImplementation(libs.ktor.server.test.host)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
