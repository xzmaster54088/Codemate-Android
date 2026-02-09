# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# CodeMate specific ProGuard rules

# Keep application class
-keep public class com.codemate.CodeMateApplication { *; }

# Keep all activities
-keep public class * extends android.app.Activity

# Keep all fragments
-keep public class * extends androidx.fragment.app.Fragment

# Keep ViewModels and LiveData
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.LiveData { *; }
-keep class * extends androidx.lifecycle.MutableLiveData { *; }

# Keep Hilt classes
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class * extends dagger.hilt.android.components.*
-keep class * extends dagger.hilt.android.scopes.*

# Keep Room database classes
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep Retrofit interfaces
-keep interface * extends retrofit2.Call { *; }
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep OkHttp classes
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }
-keep @kotlinx.coroutines.InternalCoroutinesApi class * { *; }
-keep @kotlinx.coroutines.ExperimentalCoroutinesApi class * { *; }
-keep @kotlinx.coroutines.FlowPreview class * { *; }
-keep @kotlinx.coroutines.FlowInternal class * { *; }

# Keep CodeMirror classes
-keep class io.github.ahmmedrejowan.codemirror.android.** { *; }
-keep class com.ahmmedrejowan.codemirror.** { *; }

# Keep Material Design components
-keep class com.google.android.material.** { *; }
-keep class androidx.compose.** { *; }

# Keep Compose classes
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.material3.** { *; }

# Keep Work Manager classes
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.InputMerger

# Keep DataStore classes
-keep class androidx.datastore.** { *; }

# Keep Security Crypto classes
-keep class androidx.security.crypto.** { *; }

# Keep file picker classes
-keep class com.github.hedzr.android.** { *; }

# Keep Lottie classes
-keep class com.airbnb.lottie.** { *; }

# Keep Accompanist classes
-keep class com.google.accompanist.** { *; }

# Keep Coil classes
-keep class io.coil.** { *; }

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep annotation classes
-keep @androidx.annotation.Keep class * { *; }
-keep class * {
    @androidx.annotation.Keep *;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable implementation
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

# Keep reflection classes
-keep class java.lang.reflect.** { *; }

# Keep serialization classes
-keep class * implements java.io.Serializable { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Remove logging statements
-assumenosideeffects class java.util.logging.Logger {
    public void log(java.lang.String);
    public void log(java.lang.String, java.lang.Throwable);
}

# Remove System.out.println statements
-assumenosideeffects class java.lang.System {
    public static java.io.PrintStream out;
}

# Keep custom application fields and methods
-keepclassmembers class * {
    public <fields>;
    public <methods>;
}

# Keep FileProvider paths
-keep class androidx.core.content.FileProvider { *; }

# Keep BuildConfig fields
-keep class com.codemate.BuildConfig { *; }

# Keep model/data classes
-keep class com.codemate.data.model.** { *; }
-keep class com.codemate.domain.model.** { *; }

# Keep repository interfaces
-keep interface com.codemate.data.repository.** { *; }
-keep interface com.codemate.domain.repository.** { *; }

# Keep use case classes
-keep class com.codemate.domain.usecase.** { *; }

# Keep API service interfaces
-keep interface com.codemate.data.remote.** { *; }

# Keep utility classes
-keep class com.codemate.utils.** { *; }

# Keep constants
-keepclassmembers class com.codemate.** {
    public static final <fields>;
}

# Keep database related classes
-keep class com.codemate.data.local.** { *; }

# Keep preference related classes
-keep class com.codemate.data.preference.** { *; }

# CodeMate specific optimizations

# Optimize access modifiers
-allowaccessmodification

# Enable aggressive optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Merge classes with same package and similar structure
-optimizationpasses 5

# Allow multiple optimizations at once
-allowobfuscation

# Remove dead code and unused methods
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses

# Preserve generics
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Preserve annotations
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-keepattributes AnnotationDefault

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Preserve generic signatures
-keepattributes Signature

# Preserve deprecated methods
-dontwarn java.lang.instrument.ClassFileTransformer

# Keep JavaScript interface for WebView (if used)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep XML parsers
-keep class org.xmlpull.v1.** { *; }
-keep class org.kxml2.** { *; }

# Keep JSON parsers
-keep class org.json.** { *; }

# Specific rules for CodeMate

# Code editor specific
-keep class com.codemate.ui.editor.** { *; }
-keep class com.codemate.ui.components.** { *; }

# File operations specific
-keep class com.codemate.data.files.** { *; }

# Project management specific
-keep class com.codemate.data.projects.** { *; }

# Theme and settings specific
-keep class com.codemate.data.settings.** { *; }

# Navigation specific
-keep class androidx.navigation.** { *; }

# Hilt specific
-keep class dagger.hilt.** { *; }

# Coroutines specific
-keep class kotlinx.coroutines.** { *; }

# Retrofit specific
-keep class retrofit2.** { *; }
-keep class com.squareup.okhttp3.** { *; }

# Gson specific
-keep class com.google.gson.** { *; }

# Room specific
-keep class androidx.room.** { *; }

# AndroidX specific
-keep class androidx.** { *; }

# Material Design specific
-keep class com.google.android.material.** { *; }

# Compose specific
-keep class androidx.compose.** { *; }