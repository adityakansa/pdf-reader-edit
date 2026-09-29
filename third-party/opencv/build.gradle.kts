// DS-95: a slim OpenCV 4.14.0 — core, imgproc and imgcodecs (JPEG) only, for arm64-v8a and armeabi-v7a — in place of
// the full Maven package, whose native library was 24.7 MB on arm64 against 9.4 MB here.
//
// Rebuild with scripts/build_opencv.sh (once per ABI) and scripts/install_opencv.sh; see this module's README.
// Apache License 2.0, like upstream OpenCV (listed in Settings → About).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.opencv"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
