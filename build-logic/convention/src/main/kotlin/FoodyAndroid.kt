import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Android-Versionen aller Module an einer Stelle. Alle Geräte im Haushalt laufen mit dem neuesten Android (ADR 0009). */
object FoodySdk {
    const val COMPILE = 37

    /** Compose Foundation 1.13 (Compose-BOM 2026.11) verlangt compileSdk 37.1 (S14). */
    const val COMPILE_MINOR = 1
    const val MIN = 36
    const val TARGET = 37
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Gemeinsam für App und Bibliotheken: SDK-Stände, Java 17, Kotlin ohne Warnungen. */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.compileSdk = FoodySdk.COMPILE
    android.compileSdkMinor = FoodySdk.COMPILE_MINOR
    android.defaultConfig.minSdk = FoodySdk.MIN
    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    configureKotlin()
}

/**
 * Unit-Tests nur in der Debug-Variante (E1): Release und Vorschau unterscheiden sich in Unit-Tests nicht (R8 läuft dort
 * nicht), `check` ließe sonst jeden Robolectric-Test zwei- bis dreimal laufen.
 */
internal fun Project.unitTestsNurDebug() {
    extensions.getByType(AndroidComponentsExtension::class.java).beforeVariants { variante ->
        if (variante.buildType != "debug") {
            (variante as? HasHostTestsBuilder)?.hostTests?.get(HostTestBuilder.UNIT_TEST_TYPE)?.enable = false
        }
    }
}

/**
 * Robolectric ohne eigenen Download (Stufe 9, S2): Von sich aus lädt Robolectric sein Android (`android-all-instrumented`,
 * gut 200 MB) erst im Test von Maven Central, ohne Wiederholung und an Gradles Cache vorbei. In der CI scheiterte das
 * oft (`MavenArtifactFetcher`). Jetzt lädt Gradle das Jar als gewöhnliche Abhängigkeit (Cache, auch der von
 * `setup-gradle`), und Robolectric liest es offline aus Gradles Cache-Ordner.
 */
internal fun Project.robolectricOffline() {
    val jars = configurations.create("robolectricAndroid") {
        isCanBeConsumed = false
        isTransitive = false
    }
    dependencies.add(jars.name, libs.findLibrary("robolectric-android-all").get())
    val argumente = objects.newInstance<RobolectricOffline>().apply { this.jars.from(jars) }
    tasks.withType<Test>().configureEach { jvmArgumentProviders.add(argumente) }
}

/** Schaltet Robolectric für einen Testlauf offline und zeigt ihm den Ordner mit dem Jar. */
internal abstract class RobolectricOffline : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val jars: ConfigurableFileCollection

    // Robolectric sucht im Ordner nach `<artifactId>-<version>.jar`; so heißt die Datei auch in Gradles Cache.
    override fun asArguments() = listOf(
        "-Drobolectric.offline=true",
        "-Drobolectric.dependency.dir=${jars.singleFile.parentFile.absolutePath}",
    )
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
