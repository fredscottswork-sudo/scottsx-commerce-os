// ScottsTechX — app/build.gradle.kts
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.scottsx.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scottsx.app"
        // minSdk 24, not 30.
        //
        // "There was a problem parsing the package" is what an Android device
        // shows when the APK's minSdk is HIGHER than the device's API level --
        // the package parser rejects it before installing. Nothing in this app
        // actually needs API 30: every version-sensitive call is already
        // guarded (POST_NOTIFICATIONS behind API 33, notification channels
        // behind API 26), and no java.time / java.nio.file API is used, so no
        // desugaring is required.
        //
        // Floor imposed by the dependencies: Compose + Material3 need 21,
        // firebase-bom 33.x needs 23. 24 clears all of them and covers
        // Android 7.0 and up (~99% of active devices) instead of Android 11+.
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.22.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API base URL. Override per build without touching Kotlin:
        //   ./gradlew assembleRelease -PapiBaseUrl=https://api.example.com/api/v1
        // Default is the emulator loopback (10.0.2.2 -> the host machine).
        val apiBaseUrl = (project.findProperty("apiBaseUrl") as String?)
            ?: "http://10.0.2.2:3001/api/v1"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            FileInputStream(keystorePropertiesFile).use { this.load(it) }
        }
    }
    val storeFilePath = keystoreProperties.getProperty("storeFile")
        ?: System.getenv("ANDROID_KEYSTORE_PATH")

    signingConfigs {
        if (storeFilePath != null && file(storeFilePath).exists()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Only sign for release when a keystore was actually supplied;
            // otherwise the build still succeeds (debug-signed).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    // Compose (BOM 2024.06.00)
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Firebase Auth (google-services.json in app/)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    // Push notifications: the backend already fans out to device tokens.
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.android.gms:play-services-base:18.4.0")

    // Coil image loading (2.x, global ImageLoader in ScottsTechXApp)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // HTTP — OkHttp + built-in org.json (NO Retrofit / Moshi)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Material theme parent for Compose dialogs etc.
    implementation("com.google.android.material:material:1.12.0")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
