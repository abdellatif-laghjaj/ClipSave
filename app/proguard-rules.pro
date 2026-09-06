# ClipSave — minify disabled by default; keep rules for safety if enabled.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.abdellatif.clipsave.**$$serializer { *; }
-keepclassmembers class com.abdellatif.clipsave.** { *** Companion; }
-keepclasseswithmembers class com.abdellatif.clipsave.** { kotlinx.serialization.KSerializer serializer(...); }

# youtubedl-android registers Apache Commons Compress ZIP extra fields through reflection.
# R8 obfuscation makes that registry fail during YoutubeDL.init().
-keep class org.apache.commons.compress.archivers.zip.** { *; }

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
