// Hilfen nur für Tests der V3-Module; das Feature-Plugin bindet sie als testImplementation ein.
plugins {
    alias(libs.plugins.foody.android.library)
}

android {
    namespace = "de.foody.core.testing"
}

dependencies {
    // ViewModel und ViewModelStore stehen in der Schnittstelle
    api(libs.androidx.lifecycle.viewmodel.compose)
}
