# ClearVoice Recorder ProGuard rules

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Hilt
-keep class dagger.hilt.** { *; }

# Keep data model classes
-keep class com.kreativekoala.echonote.data.model.** { *; }

# Google Play Billing
-keep class com.android.vending.billing.** { *; }

# Vosk speech recognition
-keep class org.vosk.** { *; }

# Keep service classes used by Hilt injection
-keep class com.kreativekoala.echonote.service.** { *; }

# TikTok SDK
-dontwarn com.android.installreferrer.api.InstallReferrerClient$Builder
-dontwarn com.android.installreferrer.api.InstallReferrerClient
-dontwarn com.android.installreferrer.api.InstallReferrerStateListener
