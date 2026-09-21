plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.callguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.callguard"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        // 64-bit ARM phones only: x86 and 32-bit libraries added ~190 MB for devices we do not target.
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // "offline" is the product: no INTERNET permission, ever. "online" is an optional build that can download a SIGNED scam-number
    // list when the user taps "Update". It installs next to the offline app (different id) and is the only place INTERNET is declared.
    flavorDimensions += "network"
    productFlavors {
        create("offline") { dimension = "network"; buildConfigField("String", "PACK_URL", "\"\""); buildConfigField("String", "PACK_PUBLIC_KEY", "\"\""); buildConfigField("String", "MODEL_BASE_URL", "\"\"") }
        create("online") {
            dimension = "network"; applicationIdSuffix = ".online"; versionNameSuffix = "-online"
            // Set both before shipping the online build: the HTTPS address of the pack and the base64 X.509 ECDSA P-256 public key that signs it.
            // Pass at build time so nothing needs editing:  ./gradlew assembleOnlineRelease -PmodelBaseUrl=https://... -PpackUrl=... -PpackPublicKey=...
            buildConfigField("String", "PACK_URL", "\"${providers.gradleProperty("packUrl").getOrElse("")}\"")
            buildConfigField("String", "PACK_PUBLIC_KEY", "\"${providers.gradleProperty("packPublicKey").getOrElse("")}\"")
            // HTTPS folder holding the three files side by side (flat, so a GitHub release works): <base>gemma3-1b-it-int4.task, <base>model.int8.onnx, <base>tokens.txt.
            // Every file is checked against the built-in SHA-256 before it is used, so a wrong or tampered file is deleted.
            buildConfigField("String", "MODEL_BASE_URL", "\"${providers.gradleProperty("modelBaseUrl").getOrElse("")}\"")
        }
    }
    // Model files are already compressed/quantized; keep them mmap-able and uncompressed.
    androidResources { noCompress += listOf("onnx", "txt") }
}

dependencies {
    // Local AAR: sherpa-onnx v1.13.8 (JNI + onnxruntime, on-device only)
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
    // MediaPipe LLM Inference (on-device Gemma). The model file is NOT bundled; see GemmaClassifier.
    implementation("com.google.mediapipe:tasks-genai:0.10.35")
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
