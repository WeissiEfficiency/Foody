import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android-Bibliotheken ohne Oberfläche (`:core:*`). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            testOptions.targetSdk = FoodySdk.TARGET
            lint.targetSdk = FoodySdk.TARGET
        }
    }
}
