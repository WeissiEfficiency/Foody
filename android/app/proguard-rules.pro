# Nur für die Variante „vorschau“ (R8); Bibliotheken bringen ihre Regeln selbst mit.
# kotlinx.serialization: typisierte Routen, Sicherung und Sync-DTOs behalten
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class de.foody.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
