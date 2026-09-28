plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.samstream.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.samstream.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
        // Optional default API keys injected at build time (e.g. from CI secrets). Users can also enter keys in Settings.
        buildConfigField("String", "TMDB_API_KEY", "\"${System.getenv("TMDB_API_KEY") ?: ""}\"")
        // GitHub repo that hosts the live rights policy (trusted channels, takedowns) and receives rights reports.
        val repoSlug = System.getenv("SAMSTREAM_REPO") ?: "sulemanshehzad560-cloud/SAM-Stream"
        buildConfigField("String", "REPO_SLUG", "\"$repoSlug\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"${System.getenv("YOUTUBE_API_KEY") ?: ""}\"")
    }

    val keystorePath = System.getenv("SAMSTREAM_KEYSTORE")
    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SAMSTREAM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SAMSTREAM_KEY_ALIAS")
                keyPassword = System.getenv("SAMSTREAM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // In-app playback of direct video streams (public-domain / openly licensed files)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Posters
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests (Android's copy is a stub off-device)
    testImplementation("org.json:json:20240303")
}
