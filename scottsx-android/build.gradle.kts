// ScottsTechX — root build.gradle.kts
plugins {
    // AGP 8.6.0 is the MINIMUM that supports compileSdk 35. On 8.5.2 the build
    // died in :app:checkReleaseAarMetadata (right after :app:preReleaseBuild)
    // because AndroidX artifacts compiled against API 35 reject a consumer whose
    // AGP is below 8.6.0. AGP 8.6.0 requires Gradle >= 8.7; the wrapper is
    // already on 8.7, so no wrapper change is needed.
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
