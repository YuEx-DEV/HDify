# Proguard / R8 rules for HDify (Production Readiness)

# 1. Keep JNI entry points and NativeBridge
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.example.hdify.engine.NativeBridge {
    *;
}

# 2. Keep Domain and Data Models (JSON / Room / Preferences / JNI reflection)
-keep class com.example.hdify.domain.model.** { *; }
-keep class com.example.hdify.data.** { *; }
-keep class com.example.hdify.video.VideoMetadata { *; }

# 3. Keep Room Database, DAOs, and Entities
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# 4. Keep Custom Views (used in XML layouts)
-keep class com.example.hdify.ui.result.BeforeAfterSliderView { *; }
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 5. Keep Navigation SafeArgs
-keep class * implements androidx.navigation.NavArgs { *; }
-keep class * extends androidx.navigation.NavDirections { *; }

# 6. Keep Hilt / Dagger Components
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# 7. Keep Kotlin Coroutines / Flow internals
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
