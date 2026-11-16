// MerachRide watch app (Wear OS). Reads heart rate with Health Services and sends it to the phone app
// over the Wear OS Data Layer. Shares the phone app's applicationId, which the Data Layer requires
// (both must also be signed with the same key: debug builds from the same PC are).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.merachride.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.merachride"
        minSdk = 30 // Wear OS 3
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.health:health-services-client:1.0.0")
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    // Health Services returns Guava ListenableFutures. The standalone listenablefuture:1.0 artifact gets
    // swapped for Guava's empty "9999.0" placeholder during dependency resolution, leaving the class
    // missing, so depend on Guava itself.
    implementation("com.google.guava:guava:33.3.1-android")
}
