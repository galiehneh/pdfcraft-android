import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
}

val signingPropertiesFile = rootProject.file("signing/release-signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.galiehneh.pdfcraft"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.galiehneh.pdfcraft"
        minSdk = 24
        targetSdk = 35
        versionCode = 20261024
        versionName = "2026.10.16.2"
    }

    signingConfigs {
        if (signingPropertiesFile.isFile) {
            create("release") {
                val storeFilePath = signingProperties.getProperty("storeFile")
                storeFile = rootProject.file("signing").resolve(storeFilePath)
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (signingPropertiesFile.isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")

    testImplementation("junit:junit:4.13.2")
}
