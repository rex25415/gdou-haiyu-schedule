-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all kotlinx.serialization models & sync DTOs from R8 obfuscation
-keep,includedescriptorclasses class cn.gdou.haiyu.schedule.model.** { *; }
-keep,includedescriptorclasses class cn.gdou.haiyu.schedule.sync.** { *; }

# Keep JavascriptInterface bridge methods for GDOU WebView sync
-keepclassmembers class cn.gdou.haiyu.schedule.sync.** {
    @android.webkit.JavascriptInterface <methods>;
}
