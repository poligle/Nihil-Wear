import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Releases are signed with your own key when NIHIL_KEYSTORE points to it, as
// NIHIL for Android's are. Without it, the debug key: enough for your own watch.
val releaseKeystore = System.getenv("NIHIL_KEYSTORE")?.let { file(it) }?.takeIf { it.isFile }

// The version is in gradle.properties, the watch face's too. Android's versionCode
// comes from it, so it always goes up: 0.1.0-rc.1 is 10001, 0.1.0 is 10099,
// 0.1.1-rc.1 is 10101.
val nihilVersion = providers.gradleProperty("nihilVersion").get()

fun versionCodeOf(name: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?""").matchEntire(name)
        ?: error("nihilVersion must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rc.N, not $name")
    val (major, minor, patch, rc) = match.destructured
    return major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + (rc.toIntOrNull() ?: 99)
}

android {
    namespace = "com.nihilshell.wear"
    compileSdk = 35
    // Pinned so a read-only SDK (Nix) doesn't need another build-tools version.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.nihilshell.wear"
        // Wear OS 4: the oldest that installs a Watch Face Format face, so the
        // oldest NIHIL can be whole on.
        minSdk = 33
        targetSdk = 35
        versionCode = versionCodeOf(nihilVersion)
        versionName = nihilVersion
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("NIHIL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NIHIL_KEY_ALIAS")
                keyPassword = System.getenv("NIHIL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // No dependency metadata block in the APK: there is nothing to declare.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Tests only (the pure Kotlin core). The app itself has no libraries.
    testImplementation("junit:junit:4.13.2")
}
