import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

// Release signing precedence (replicated from s17labs/pebbledo):
// 1. A keystore.properties file at the repo root (private/local key, or CI
//    injecting one from repository secrets) wins when present.
// 2. Otherwise releases are signed with the PUBLIC keystore committed at
//    signing/release.keystore — FOSS-style public signing so every published
//    build shares one consistent, updatable signature.
//    alias: koda · password: koda-public (public by design)
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.s17labs.koda"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.s17labs.koda"
        minSdk = 26
        targetSdk = 33
        versionCode = 3
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storeType = keystoreProps.getProperty("storeType") ?: "PKCS12"
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        } else {
            create("release") {
                storeFile = rootProject.file("signing/release.keystore")
                storeType = "PKCS12"
                storePassword = "koda-public"
                keyAlias = "koda"
                keyPassword = "koda-public"
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            isEnable = true
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
    implementation("androidx.core:core-ktx:1.3.2")

    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("com.google.android.material:material:1.2.1")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("androidx.recyclerview:recyclerview:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}

