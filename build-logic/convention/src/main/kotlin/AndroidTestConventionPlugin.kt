import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Test-Module gegen die App (`:baselineprofile`: Baseline-Profil, Startmessung, Kurztest der R8-Variante). */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.test")
        extensions.configure<TestExtension> {
            configureAndroid(this)
            defaultConfig.targetSdk = FoodySdk.TARGET
        }
    }
}
