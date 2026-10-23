plugins {
    id("com.android.application")
}

android {
    namespace = "com.iptv.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.iptv.player"
        minSdk = 21
        targetSdk = 34
        versionCode = (project.findProperty("APP_VERSION_CODE") as String?)?.toInt() ?: 2
        versionName = project.findProperty("APP_VERSION_NAME") as String? ?: "1.1"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Debug APK is for testing only; its signing key may differ between CI runs.
        }
        release {
            isMinifyEnabled = false
            val ksPath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
