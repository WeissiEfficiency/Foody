// Web-Oberfläche des Servers (im Server gerendert). Kennt den Sync-Server, nicht umgekehrt.
plugins {
    alias(libs.plugins.kotlin.jvm)
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

dependencies {
    implementation(project(":server-sync"))
    implementation(project(":domain"))
}
