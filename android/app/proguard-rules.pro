# kotlinx.serialization: Backup-DTOs behalten
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class de.foody.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
