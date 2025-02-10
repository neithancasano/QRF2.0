plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.Upenn.qrf"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.Upenn.qrf"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // for connecting with piehost
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    // for using Gson
    implementation("com.google.code.gson:gson:2.8.9") // Use the latest version available
    // for passing JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1") // Use the latest version available

    // for uploading audio feedback over ftp
    implementation("commons-net:commons-net:3.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")
    implementation("com.jcraft:jsch:0.1.55")

    // for runtime permissions check
    implementation("com.google.android.material:material:1.3.0")
}