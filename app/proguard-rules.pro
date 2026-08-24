# R8 strips what it cannot see called. These two are only reached reflectively.

# Navigation routes and their generated serializers: resolved by type at runtime,
# so R8 sees no call site and would rename them. Renamed route = crash on navigate.
-keep class com.cyberrin.giswrap.ui.navigation.** { *; }

-keepclassmembers class com.cyberrin.giswrap.** {
    *** Companion;
}
-keepclasseswithmembers class com.cyberrin.giswrap.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Readable stack traces from release crashes.
-keepattributes SourceFile,LineNumberTable

