# Add project specific ProGuard rules here.

# kotlinx.serialization: keep @Serializable data classes and their generated serializers
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static ** Companion;
    static ** serializer(...);
    <fields>;
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}

# Tink (via androidx.security.crypto) pulls in Error Prone annotations as compile-only
# dependencies that are absent at runtime — suppress the R8 missing-class warnings.
-dontwarn com.google.errorprone.annotations.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }

# Glance AppWidget components (referenced from XML and instantiated by the launcher)
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.action.ActionCallback { *; }

# WorkManager workers (instantiated by WorkManager via reflection)
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Widget and worker classes (keep all members in case of reflection / RemoteViews)
-keep class io.github.raven_wing.cuview.widget.** { *; }
-keep class io.github.raven_wing.cuview.worker.** { *; }
