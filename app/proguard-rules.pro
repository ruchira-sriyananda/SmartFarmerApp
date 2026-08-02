# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\Lapmart.lk\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools-proguard.html

# Add any custom keep rules here that are specific to your project libraries.

# Firebase
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Gson/JSON
-keep class org.json.** { *; }
-keep class com.google.gson.** { *; }

# Glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# Stripe
-keep class com.stripe.** { *; }

# Room
-keep class androidx.room.** { *; }

# SpinKit
-keep class com.github.ybq.android.spinkit.** { *; }

# Your Models (IMPORTANT: keep your data classes)
-keep class com.smartfarmers.models.** { *; }
