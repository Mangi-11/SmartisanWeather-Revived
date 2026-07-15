# Debug tracing, including network diagnostics, must not survive into release or execute
# from animation draw callbacks.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
}

# Android restores app and AndroidX Parcelable instances by the conventional field name.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Deliberate size trade-off: Java and framework callers must honor Kotlin nullability.
-processkotlinnullchecks remove
