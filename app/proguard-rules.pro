# Proguard rules for Local AI Studio
-keepattributes Signature, InnerClasses, EnclosingMethod, Annotation

# Keep native methods and their classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep the JNI Native bridge class
-keep class com.example.data.LlamaCppNative { *; }

# Keep Room entities and custom DB structures
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Keep serialization
-keepattributes *Annotation*,Signature
-dontwarn kotlinx.serialization.**
