# R8-Regeln für „Foody“ (Release) und „Foody Vorschau“; Bibliotheken bringen ihre Regeln sonst selbst mit.
# kotlinx.serialization: typisierte Routen, Sicherung und Sync-DTOs behalten
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class de.foody.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# ML Kit (Code Scanner, Texterkennung): Die Registrare werden per Reflexion über ihren leeren Konstruktor angelegt.
# firebase-components 16.1.0 behält nur die Klasse; R8 im Vollmodus entfernt dann den Konstruktor, ML Kit bleibt
# ohne Komponenten, und GmsBarcodeScanning.getClient stürzt mit einer NullPointerException ab („Frei“ im Tagebuch).
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
