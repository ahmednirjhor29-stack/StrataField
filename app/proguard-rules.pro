# Keep Gson models
-keep class com.stratafield.app.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep POI
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.commons.**

# Keep Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**