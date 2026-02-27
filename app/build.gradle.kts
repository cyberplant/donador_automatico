import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "uy.roar.donadorautomatico"
    compileSdk = 35

    defaultConfig {
        applicationId = "uy.roar.donadorautomatico"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Load signing configuration
    val signingPropsFile = rootProject.file("signing.properties")
    val signingProps = Properties().apply {
        if (signingPropsFile.exists()) {
            load(signingPropsFile.inputStream())
        }
    }

    signingConfigs {
        create("release") {
            // Priorizar variables de entorno (GitHub Secrets) sobre archivo properties
            val keystorePath = System.getenv("KEYSTORE")?.let { "keystore.jks" }
                ?: signingProps.getProperty("storeFile", "keystore.jks")

            storeFile = rootProject.file(keystorePath)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                ?: signingProps.getProperty("storePassword")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                ?: signingProps.getProperty("keyAlias")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                ?: signingProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use signing config if available (environment variables or properties file)
            if (System.getenv("SIGNING_KEY_ALIAS") != null ||
                System.getenv("SIGNING_STORE_PASSWORD") != null ||
                signingPropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}