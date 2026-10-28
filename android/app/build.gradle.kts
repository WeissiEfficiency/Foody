import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

// App-Hülle der V3: Navigation, Hilt-Zusammenbau, Sync-Verdrahtung, Startdaten. Gleiche App-ID wie die frühere v1,
// damit ein Update die vorhandene Datenbank übernimmt (Migration 9 → 10 in :core:data). Varianten: „Foody Debug“
// (`.debug`), „Foody“ (Release, eigener Schlüssel) und „Foody Vorschau“ (`.vorschau`, R8 wie ein Release, aber mit
// dem Debug-Schlüssel signiert, läuft neben „Foody“).
plugins {
    alias(libs.plugins.foody.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

// Rechnerspezifisches aus local.properties (nicht im Repository), UTF-8 gelesen: Pfade können Umlaute enthalten.
fun propertiesAus(datei: File) = Properties().apply { datei.takeIf { it.exists() }?.reader(Charsets.UTF_8)?.use(::load) }
val localProperties = propertiesAus(rootProject.file("local.properties"))

// Signaturschlüssel für Release-Builds: Pfade und Passwörter liegen in keystore.properties (nicht im Repository,
// Vorlage: keystore.properties.example) – neben dem Projekt oder dort, wohin `foody.keystoreProperties` in
// local.properties zeigt. Fehlt die Datei – etwa in der CI –, bleibt der Release-Build unsigniert.
val keystoreProperties = propertiesAus(
    localProperties.getProperty("foody.keystoreProperties")?.let(::File) ?: rootProject.file("keystore.properties"),
)

android {
    namespace = "de.foody.app"

    defaultConfig {
        applicationId = "de.foody.app"
        // Vor jedem Release erhöhen; muss über dem der v1 (1) liegen, sonst lehnt Android das Update ab
        versionCode = 300
        versionName = "3.0.0"
    }

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
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        create("vorschau") {
            initWith(getByName("release"))
            applicationIdSuffix = ".vorschau"
            versionNameSuffix = "-vorschau"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
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

// APK dorthin kopieren, von wo sie über OpenCloud aufs Handy und Tablet kommt (docs/installation.md):
// `./gradlew :app:apkNachOpenCloud` („Foody“, Release) und `:app:vorschauNachOpenCloud` („Foody Vorschau“).
// Ziele je Rechner in local.properties: `foody.apkZiel=E:/OpenCloud/Persönlich (2)/foody.apk`, für die Vorschau
// `foody.vorschauZiel` (fehlt es: `foody-vorschau.apk` neben `foody.apkZiel`).
val apkZiel: File? = localProperties.getProperty("foody.apkZiel")?.let(::File)
val vorschauZiel: File? = localProperties.getProperty("foody.vorschauZiel")?.let(::File)
    ?: apkZiel?.let { File(it.parentFile, "foody-vorschau.apk") }
val releaseSigniert = keystoreProperties.isNotEmpty()

/**
 * Kopiert die eine APK einer Variante nach [ziel]. Kein Copy-Task: Der Zielordner ist ein geteilter OpenCloud-Ordner,
 * Gradle soll nur die eine Datei schreiben und nicht den ganzen Ordner als eigene Ausgabe verwalten.
 */
fun apkAblage(name: String, beschreibung: String, ziel: File?, signiert: Boolean) = tasks.register(name) {
    group = "foody"
    description = beschreibung
    doLast {
        if (ziel == null) throw GradleException("foody.apkZiel fehlt in local.properties (Ziel der APK).")
        if (!signiert) throw GradleException("Kein Release-Schlüssel: keystore.properties anlegen (Vorlage keystore.properties.example).")
        val apk = inputs.files.asFileTree.matching { include("*.apk"); exclude("*-unsigned.apk") }.singleOrNull()
            ?: throw GradleException("Keine signierte APK gefunden.")
        apk.copyTo(ziel, overwrite = true)
        logger.lifecycle("APK kopiert nach $ziel")
    }
}

val apkNachOpenCloud = apkAblage(
    "apkNachOpenCloud", "Baut „Foody“ (Release, signiert) und kopiert die APK nach foody.apkZiel.", apkZiel, releaseSigniert,
)
val vorschauNachOpenCloud = apkAblage(
    "vorschauNachOpenCloud", "Baut „Foody Vorschau“ und kopiert die APK nach foody.vorschauZiel.", vorschauZiel, signiert = true,
)

// Die APK über die Variant-API anfordern statt aus build/outputs zu lesen: So kennt Gradle die Abhängigkeit zum
// Bau-Task (direkt aus dem Ordner zu lesen lehnt Gradle 9 als versteckte Abhängigkeit ab).
androidComponents {
    onVariants(selector().withBuildType("release")) { v -> apkNachOpenCloud.configure { inputs.files(v.artifacts.get(SingleArtifact.APK)) } }
    onVariants(selector().withBuildType("vorschau")) { v -> vorschauNachOpenCloud.configure { inputs.files(v.artifacts.get(SingleArtifact.APK)) } }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:sync"))
    implementation(project(":core:scan"))
    implementation(project(":feature:heute"))
    implementation(project(":feature:rezepte"))
    implementation(project(":feature:kochen"))
    implementation(project(":feature:woche"))
    implementation(project(":feature:einkauf"))
    implementation(project(":feature:einstellungen"))
    implementation(project(":feature:zutaten"))
    implementation(project(":domain"))

    // Installiert das mitgelieferte Baseline-Profil auch bei APKs, die nicht über Google Play kommen
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

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
