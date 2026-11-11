plugins {
    id("com.android.application")
}

// NIHIL's watch face: Watch Face Format, its XML and two fonts, no code. Drawn
// by tools/watchface.py; signed and numbered as the app is.
val releaseKeystore = System.getenv("NIHIL_KEYSTORE")?.let { file(it) }?.takeIf { it.isFile }
val nihilVersion = providers.gradleProperty("nihilVersion").get()

fun versionCodeOf(name: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?""").matchEntire(name)
        ?: error("nihilVersion must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rc.N, not $name")
    val (major, minor, patch, rc) = match.destructured
    return major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() * 100 + (rc.toIntOrNull() ?: 99)
}

android {
    namespace = "com.nihilshell.wear.watchface"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.nihilshell.wear.watchface"
        // Watch Face Format 1: Wear OS 4.
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
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}
