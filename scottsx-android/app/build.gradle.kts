import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.scottsx.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scottsx.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }


    // ── Stable debug signature ─────────────────────────────────────────────
    // Google sign-in only trusts package_name + SHA-1 pairs REGISTERED in the
    // Google Cloud project. Gradle's auto debug key lives under ~/.android and
    // differs on every machine/CI runner, which made every APK look signed by
    // a stranger — Google then returned RESULT_CANCELED the app reported as
    // "Google sign-in cancelled". Pin the shared repo debug key instead (see
    // keystores/README.md) so one OAuth registration covers every debug build.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystores/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// java.io must be imported above: in .kts scripts 'java' resolves to
// the Gradle JavaPluginExtension, not the JDK package.
// Print the debug signing fingerprints during every build (CI log included) so
// the SHA-1/SHA-256 Google needs can be checked without running the APK.
tasks.register("printDebugSigningFingerprint") {
    doLast {
        runCatching {
            val out = ByteArrayOutputStream()
            exec {
                commandLine(
                    "keytool", "-list", "-v",
                    "-keystore", rootProject.file("keystores/debug.keystore").absolutePath,
                    "-storepass", "android", "-alias", "androiddebugkey",
                )
                standardOutput = out
            }
            out.toString().lines()
                .map { it.trimStart() }
                .filter { it.startsWith("SHA1:") || it.startsWith("SHA256:") }
                .forEach { logger.lifecycle("GOOGLE SIGN-IN  $it") }
        }.onFailure { logger.warn("printDebugSigningFingerprint skipped: ${it.message}") }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn("printDebugSigningFingerprint") }


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
