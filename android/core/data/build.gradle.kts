// Datenbank der V3-App: Room-Datenbank foody.db, Repositories, Fotos, Einstellungen (V3, Phase 3).
plugins {
    alias(libs.plugins.foody.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    // Sicherung: backup.json
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.foody.core.data"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        // Gradle Managed Device wie in :app: Gradle startet den Emulator selbst, so laufen die
        // Room-/Migrationstests auch in der CI (./gradlew :core:data:ciDeviceDebugAndroidTest).
        managedDevices {
            localDevices {
                create("ciDevice") {
                    device = "Pixel 6"
                    apiLevel = 36
                    systemImageSource = "google"
                }
            }
        }
    }

    sourceSets {
        // Room-Schemata für Migrationstests
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

// Room-Gradle-Plugin exportiert die Schemata nach schemas/ (9 = letztes der v1, für den Migrationstest 9 → 10; ab 10 FoodyDatenbank).
room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    // api: Entities zeigen Typen aus :domain, FoodyDatenbank den Supertyp RoomDatabase; :core:sync braucht sie.
    api(project(":domain"))
    api(project(":api-protocol"))

    implementation(libs.androidx.core.ktx)
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.coroutines.android)
    // Sicherung (backup.json); kommt auch über :api-protocol, hier ausdrücklich
    implementation(libs.serialization.json)
    // Fotoablage: Drehung aus den EXIF-Daten
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.coroutines.test)
    // DaoTest: In-Memory-Room unter Robolectric
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // SchemaVergleichTest liest die exportierten Schema-JSONs
    testImplementation(libs.serialization.json)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.kotlin.test.junit)
}
