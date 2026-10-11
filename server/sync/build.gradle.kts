plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-library`
}

// Sync-Server als Bibliothek: API, Konten, Datenbank, Sync, Fotos, Admin-CLI. Gestartet wird er von :server
// (server/start), der auch die Web-Oberfläche (:server-web) einhängt.

// Kein jvmToolchain: kompiliert mit dem vorhandenen JDK, erzeugt Java-17-Bytecode.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":sync-protocol"))
    api(libs.ktor.server.core)
    api(libs.ktor.server.netty)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.forwarded.header)
    implementation(libs.sqlite.jdbc)
    implementation(libs.bcprov)
    api(libs.logback.classic)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
