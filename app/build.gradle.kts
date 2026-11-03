plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.aarya.kivya"
    compileSdk = 35
    defaultConfig { applicationId = "com.aarya.kivya"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            // Temporary test signing so the optimized APK can be installed for device testing.
            // Replace with a private production keystore before Play Store distribution.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // LiteRT-LM runtime for on-device offline chat.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    // Sherpa-ONNX Android runtime is downloaded and checksum-verified by CI into app/libs.
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    debugImplementation("androidx.compose.ui:ui-tooling")
}
