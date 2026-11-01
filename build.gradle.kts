plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
}

// ktlint (E7) als Kommandozeilenwerkzeug statt als Plugin: ändert nichts am Build der Module. Regeln in .editorconfig.
val ktlint: Configuration by configurations.creating {
    // JVM-Varianten wählen: ktlints Abhängigkeiten (z. B. clikt) sind Multiplatform-Bibliotheken
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
    }
}
dependencies { ktlint(libs.ktlint.cli) }

val ktlintDateien = listOf("--relative", "**/src/**/*.kt", "**.kts", "!**/build/**")

fun JavaExec.ktlintStarten(vararg zusatz: String) {
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    // ktlint greift per Reflection auf java.lang zu; ohne die Freigabe warnt die JVM bei jedem Lauf
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args(zusatz.toList() + ktlintDateien)
}

tasks.register<JavaExec>("ktlintPruefen") {
    group = "verification"
    description = "Prüft den Kotlin-Stil aller Module (ktlint)."
    ktlintStarten()
}

tasks.register<JavaExec>("ktlintKorrigieren") {
    group = "formatting"
    description = "Behebt die Stilfehler, die ktlint selbst beheben kann."
    ktlintStarten("--format")
}
