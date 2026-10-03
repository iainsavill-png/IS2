# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class uk.railboard.app.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
