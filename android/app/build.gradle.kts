import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// Voice-data contribution ingest endpoint (opt-in feature). Blank in
// local.properties = uploads disabled but the build still compiles.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun ingestProp(name: String) = (localProperties.getProperty(name) ?: "").replace("\"", "")

android {
    namespace = "com.kreativekoala.echonote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kreativekoala.echonote"
        minSdk = 26
        targetSdk = 36
        versionCode = 38
        versionName = "1.9.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "INGEST_URL", "\"${ingestProp("clearvoice.ingestUrl")}\"")
        buildConfigField("String", "INGEST_KEY", "\"${ingestProp("clearvoice.ingestKey")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("/Users/sushanthtiruvaipati/Documents/GitHub/AndroidAppKey")
            storePassword = "KashtePhale!9"
            keyAlias = "androidappkey"
            keyPassword = "KashtePhale!9"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // PaywallKit
    implementation(project(":paywallkit"))
    implementation(project(":crosspromokit"))

    // RatingKit
    implementation(project(":ratingkit"))

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // AndroidX Core
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // Room (database — SwiftData equivalent)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Hilt (dependency injection)
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Media3 / ExoPlayer (audio playback)
    val media3Version = "1.6.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-transformer:$media3Version")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:8.0.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // In-App Review
    implementation("com.google.android.play:review-ktx:2.0.2")

    // DataStore (UserDefaults equivalent)
    implementation("androidx.datastore:datastore-preferences:1.1.4")

    // Vosk (offline speech recognition)
    implementation("com.alphacephei:vosk-android:0.3.75")

    // WorkManager + Hilt-Work (background upload of opt-in voice contributions)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // OkHttp (multipart upload for opt-in voice contributions)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Firebase Analytics (for Google Ads conversion tracking)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")

    // TikTok Events SDK (install attribution & event tracking)
    implementation("com.github.tiktok:tiktok-business-android-sdk:1.6.0")

    // Meta / Facebook SDK (app events for Meta Ads attribution)
    implementation("com.facebook.android:facebook-android-sdk:17.0.1")

    // Glance App Widget
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
}
