-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

-dontwarn okhttp3.**
-dontwarn okio.**

-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

-keep class coil3.** { *; }
-dontwarn coil3.**

-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

-keep class org.enchant.core.crypto.** { *; }
-keepclassmembers class org.enchant.core.crypto.** { native <methods>; }

-keepattributes SourceFile,LineNumberTable
-keep class com.google.firebase.crashlytics.** { *; }

-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
