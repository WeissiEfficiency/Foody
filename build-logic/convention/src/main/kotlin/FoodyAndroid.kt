import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Android-Versionen aller Module an einer Stelle. Alle Geräte im Haushalt laufen mit dem neuesten Android (ADR 0009). */
object FoodySdk {
    const val COMPILE = 37
    const val MIN = 36
    const val TARGET = 37
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Gemeinsam für App und Bibliotheken: SDK-Stände, Java 17, Kotlin ohne Warnungen. */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.compileSdk = FoodySdk.COMPILE
    android.defaultConfig.minSdk = FoodySdk.MIN
    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    configureKotlin()
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // Der Code ist warnungsfrei – so bleibt er es (z. B. neue Deprecations nach Bibliotheks-Updates).
            allWarningsAsErrors.set(true)
        }
    }
}
