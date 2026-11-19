# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Gson & Retrofit Data Models
-keep class com.example.fitlog.User { *; }
-keep class com.example.fitlog.SupabaseAuthApi$* { *; }
-keep class com.example.fitlog.WaterLog { *; }
-keep class com.example.fitlog.MealLog { *; }
-keep class com.example.fitlog.MealInsertDto { *; }
-keep class com.example.fitlog.AdminNotification { *; }
-keep class com.example.fitlog.AiMealPlan { *; }
-keep class com.example.fitlog.NutritionData { *; }
-keep class com.example.fitlog.DayHistory { *; }
-keep class com.example.fitlog.WeightLog { *; }
-keep class com.example.fitlog.FavoriteMeal { *; }

# Generic Rules for Retrofit/Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# Release build'lerde debug/verbose/info loglarını tamamen kaldır
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
