import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.Properties

// ── API origin resolution ────────────────────────────────────────────────
// -PapiBaseUrl comes from the release workflow / CI dispatch and MUST end
// with /api/v1 (the workflows enforce it). Strip that suffix and any
// trailing slash so V2Client gets a bare origin to prepend route paths to.
// No property → the production origin the website uses.
fun apiBaseUrlOrigin(): String {
    val raw = (project.findProperty("apiBaseUrl") as String?)?.trim().orEmpty()
    val origin = raw.removeSuffix("/api/v1").trimEnd('/')
    return origin.ifEmpty { "https://scottstechx-api.onrender.com" }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

// ── Release keystore resolution ─────────────────────────────────────────
// A release build with NO signingConfig produces an UNSIGNED APK that
// Android refuses to install ("There was a problem parsing the package").
// When the workflow (or a local keystore.properties) supplies a real
// keystore we sign with it; otherwise the build falls back to the shared
// debug key so every release APK is at least installable. Sources, in
// order:
//   1. keystore.properties  (storeFile/storePassword/keyAlias/keyPassword)
//   2. ANDROID_KEYSTORE_PATH + ANDROID_KEYSTORE_PASSWORD +
//      ANDROID_KEY_ALIAS + ANDROID_KEY_PASSWORD   (what CI exports)
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val releaseStorePath: String? = keystoreProperties.getProperty("storeFile")
    ?: System.getenv("ANDROID_KEYSTORE_PATH")
val releaseStoreFile: File? = releaseStorePath?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.scottsx.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scottsx.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.0.4"

        // ── Bake the API origin into the APK ─────────────────────────────
        // The release workflow and CI pass -PapiBaseUrl=<origin>/api/v1.
        // The app's route paths already carry the /api/v1 prefix, so only
        // the ORIGIN is stored. Without this the property was silently
        // ignored and every APK shipped on the hardcoded default no matter
        // what URL the workflow said it was building against.
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"" + apiBaseUrlOrigin() + "\"",
        )
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
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
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
            // Always sign: the real release key when one was supplied, the
            // shared debug key otherwise. Never ship an unsigned APK.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
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
        buildConfig = true
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
    implementation(libs.androidx.compose.ui.text.google.fonts)
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
