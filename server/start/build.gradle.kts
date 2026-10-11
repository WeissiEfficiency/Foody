// Startpunkt des Servers: main() verbindet Sync-Server (:server-sync) und Web-Oberfläche (:server-web).
// installDist erzeugt `foody-server` (Docker: server/start/build/install/foody-server).
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

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

application {
    mainClass = "de.foody.server.MainKt"
    applicationName = "foody-server"
}

dependencies {
    implementation(project(":server-sync"))
    implementation(project(":server-web"))
}
