import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)

    // Supabase decodes database rows into @Serializable classes.
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")

    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use {
            load(it)
        }
    }
}

val supabaseUrl =
    localProperties.getProperty("SUPABASE_URL")
        ?: error("SUPABASE_URL is missing from local.properties")

val supabasePublishableKey =
    localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY")
        ?: error("SUPABASE_PUBLISHABLE_KEY is missing from local.properties")

android {
    namespace = "com.example.spotapp"

    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.spotapp"

        minSdk = 24
        targetSdk = 36

        versionCode = 16
        versionName = "5.5"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"$supabaseUrl\""
        )

        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"$supabasePublishableKey\""
        )
    }

    buildTypes {
        release {
            // R8 shrinks and optimizes the release APK: smaller download, faster start.
            // Extra keep rules live in src/main/keepRules/spot.keep.
            isMinifyEnabled = true
            isShrinkResources = true

            // APKs shared directly with testers are signed with this PC's debug key: its SHA-1 is the one
            // registered for Google sign-in, and updates install over earlier builds from this PC.
            // Use a dedicated release key before publishing to Google Play.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        // SPOT uses minSdk 24.
        // Supabase normally targets API 26+, so enable desugaring.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    // ---------------------------------------------------------
    // Compose
    // ---------------------------------------------------------

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.material:material-icons-extended")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(
        "androidx.lifecycle:lifecycle-runtime-compose:2.11.0"
    )

    // ---------------------------------------------------------
    // DataStore
    // ---------------------------------------------------------

    implementation(
        "androidx.datastore:datastore-preferences:1.1.1"
    )

    // ---------------------------------------------------------
    // SUPABASE
    //
    // We use Supabase for:
    // - Authentication
    // - PostgreSQL database
    // - Realtime
    // - Edge Functions (AI Assist in the post composer)
    //
    // We are NOT using Supabase Storage.
    // ---------------------------------------------------------

    implementation(
        platform(
            "io.github.jan-tennert.supabase:bom:3.2.6"
        )
    )

    implementation(
        "io.github.jan-tennert.supabase:auth-kt"
    )

    implementation(
        "io.github.jan-tennert.supabase:postgrest-kt"
    )

    implementation(
        "io.github.jan-tennert.supabase:realtime-kt"
    )

    implementation(
        "io.github.jan-tennert.supabase:functions-kt"
    )

    // Required networking engine.
    // Supports WebSockets for Supabase Realtime.
    implementation(
        "io.ktor:ktor-client-okhttp:3.3.0"
    )

    coreLibraryDesugaring(
        "com.android.tools:desugar_jdk_libs:2.1.5"
    )

    // ---------------------------------------------------------
    // Google Sign-In
    // ---------------------------------------------------------

    implementation(
        "androidx.credentials:credentials:1.6.0"
    )

    implementation(
        "androidx.credentials:credentials-play-services-auth:1.6.0"
    )

    implementation(
        "com.google.android.libraries.identity.googleid:googleid:1.2.0"
    )

    // ---------------------------------------------------------
    // Firebase Cloud Messaging - new message notifications.
    // Settings come from res/values/firebase_config.xml.
    // ---------------------------------------------------------

    implementation(
        platform("com.google.firebase:firebase-bom:34.19.0")
    )

    implementation(
        "com.google.firebase:firebase-messaging"
    )

    // ---------------------------------------------------------
    // Splash Screen
    // ---------------------------------------------------------

    implementation(
        "androidx.core:core-splashscreen:1.2.0"
    )

    // ---------------------------------------------------------
    // Coil - displaying images
    // ---------------------------------------------------------

    implementation(
        "io.coil-kt.coil3:coil-compose:3.4.0"
    )

    implementation(
        "io.coil-kt.coil3:coil-network-okhttp:3.4.0"
    )

    // ---------------------------------------------------------
    // Media3 - videos / reels
    // ---------------------------------------------------------

    implementation(
        "androidx.media3:media3-exoplayer:1.4.1"
    )

    implementation(
        "androidx.media3:media3-ui:1.4.1"
    )

    // Re-encodes post and story videos to 720p before they're uploaded to Cloudinary.
    implementation(
        "androidx.media3:media3-transformer:1.4.1"
    )

    implementation(
        "androidx.media3:media3-effect:1.4.1"
    )

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // ---------------------------------------------------------
    // CLOUDINARY
    //
    // KEEP THIS.
    // Images + videos + reels stay on Cloudinary.
    // ---------------------------------------------------------

    implementation(
        "com.cloudinary:cloudinary-android:3.1.2"
    )

    // ---------------------------------------------------------
    // Testing
    // ---------------------------------------------------------

    testImplementation(libs.junit)

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}