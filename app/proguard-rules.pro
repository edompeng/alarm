# ProGuard & R8 Optimization Rules for Minimal APK Size
-repackageclasses 'com.edom.alarm.a'
-allowaccessmodification
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Preserve Native JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve Android Components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
