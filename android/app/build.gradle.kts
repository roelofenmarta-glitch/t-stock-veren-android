plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

android {
    namespace = "nl.tstock.veren"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.tstock.veren"
        minSdk = 26
        targetSdk = 35
        versionCode = 10602
        versionName = "10.6.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        manifestPlaceholders["appLabel"] = "T-Stock Veren"
    }

    flavorDimensions += "channel"
    productFlavors {
        create("production") {
            dimension = "channel"
            buildConfigField("boolean", "IS_TEST_BUILD", "false")
            buildConfigField("String", "APP_TITLE", "\"T-Stock Veren\"")
            buildConfigField("String", "UPDATE_CHANNEL", "\"stable\"")
            manifestPlaceholders["appLabel"] = "T-Stock Veren"
        }
        create("beta") {
            dimension = "channel"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            buildConfigField("boolean", "IS_TEST_BUILD", "true")
            buildConfigField("String", "APP_TITLE", "\"T-Stock Veren BETA\"")
            buildConfigField("String", "UPDATE_CHANNEL", "\"beta\"")
            manifestPlaceholders["appLabel"] = "T-Stock Veren BETA"
        }
        create("internal") {
            dimension = "channel"
            applicationIdSuffix = ".test1062"
            versionNameSuffix = "-test"
            buildConfigField("boolean", "IS_TEST_BUILD", "true")
            buildConfigField("String", "APP_TITLE", "\"T-Stock Veren TEST\"")
            buildConfigField("String", "UPDATE_CHANNEL", "\"test\"")
            manifestPlaceholders["appLabel"] = "T-Stock Veren TEST"
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    signingConfigs {
        if (!releaseKeystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
