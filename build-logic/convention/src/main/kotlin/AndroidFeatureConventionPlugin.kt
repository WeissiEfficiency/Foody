import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Ein Bereich der App (`:feature:*`): Bibliothek mit Compose und den üblichen Test-Abhängigkeiten.
 * Features hängen nie voneinander ab, nur von `:core:*` und `shared/` (ADR 0009).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("foody.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
            // Robolectric braucht die Android-Ressourcen in Unit-Tests
            testOptions.unitTests.isIncludeAndroidResources = true
        }
        dependencies {
            val bom = platform(libs.findLibrary("compose-bom").get())
            add("implementation", bom)
            add("implementation", libs.findLibrary("compose-ui").get())
            add("implementation", libs.findLibrary("compose-material3").get())
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())

            add("testImplementation", bom)
            add("testImplementation", libs.findLibrary("junit4").get())
            add("testImplementation", libs.findLibrary("kotlin-test-junit").get())
            add("testImplementation", libs.findLibrary("robolectric").get())
            add("testImplementation", libs.findLibrary("compose-ui-test-junit4").get())
            add("testImplementation", project(":core:testing"))
            add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
        }
    }
}
