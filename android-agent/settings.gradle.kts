pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.11.1"
        id("org.jetbrains.kotlin.android") version "2.4.20"
        id("org.jetbrains.kotlin.jvm") version "2.4.20"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "voice-agent"

// The planning loop lives in a plain JVM module so it can be compiled and unit
// tested on any machine with a JDK. The Android app module needs the Android SDK,
// so it is only included when one is configured (Android Studio always sets it).
include(":core")

val localProps = File(rootDir, "local.properties")
val hasSdkDir = localProps.exists() && localProps.readText().contains("sdk.dir")
val hasSdkEnv = !System.getenv("ANDROID_HOME").isNullOrBlank() ||
    !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank()
if (hasSdkDir || hasSdkEnv) {
    include(":app")
} else {
    logger.lifecycle("Android SDK not found: skipping :app (only :core is configured).")
}
