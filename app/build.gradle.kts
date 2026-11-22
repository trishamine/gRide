import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// App version: from the release tag on GitHub (-PappVersion=1.2.0), otherwise the default below.
val appVersion = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "1.2.2"
val appVersionCode = appVersion.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
    .let { p -> p.getOrElse(0) { 0 } * 10000 + p.getOrElse(1) { 0 } * 100 + p.getOrElse(2) { 0 } }

// Release signing: keystore.properties locally (not in git), environment variables on GitHub Actions.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(key)
val releaseStoreFile = signingValue("storeFile", "GRIDE_KEYSTORE")

android {
    namespace = "org.devbucket.gRide"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.devbucket.gRide"
        minSdk = 33
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "GRIDE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "GRIDE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "GRIDE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Debug builds use the same key when it is available, so they install over release versions.
        debug {
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
        release {
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Map (OpenStreetMap tiles)
    implementation(libs.osmdroid)

    // Liquid Glass (github.com/Kyant0/AndroidLiquidGlass)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
