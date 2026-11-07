# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.github.hackyday.irforge.**$$serializer { *; }
-keepclassmembers class io.github.hackyday.irforge.** { *** Companion; }
-keepclasseswithmembers class io.github.hackyday.irforge.** { kotlinx.serialization.KSerializer serializer(...); }
