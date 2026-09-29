import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// FR-101: All files access is a Play declaration risk. Flip to false to ship the SAF-only build.
val allFilesAccess = (project.findProperty("ALL_FILES_ACCESS") as String?)?.toBoolean() ?: true

android {
    namespace = "com.whats.web.scan.webscan.pdfreaderpdffileedit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.whats.web.scan.webscan.pdfreaderpdffileedit"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["manageStoragePermission"] =
            if (allFilesAccess) "android.permission.MANAGE_EXTERNAL_STORAGE"
            else "android.permission.INTERNET"
        buildConfigField("boolean", "ALL_FILES_ACCESS", allFilesAccess.toString())
        // FR-088: release ad unit ids come from gradle.properties, debug uses Google's test ids.
        buildConfigField("String", "AD_BANNER_UNIT", "\"${property("AD_BANNER_UNIT")}\"")
        buildConfigField("String", "AD_NATIVE_UNIT", "\"${property("AD_NATIVE_UNIT")}\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${property("PRIVACY_POLICY_URL")}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "AD_BANNER_UNIT", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "AD_NATIVE_UNIT", "\"ca-app-pub-3940256099942544/2247696110\"")
        }
        release {
            optimization {
                enable = true
            }
            // Keep rules live in src/main/keepRules/rules.keep (AGP 9 convention).
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // FR-001: phones only — x86 doubles the native payload and an App Bundle splits by ABI anyway.
    defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") } }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    // ML Kit and Play Billing hand back Tasks; this is what awaits them.
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.webkit)
    implementation(libs.pdfbox.android)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.view)

    implementation(project(":third-party:opencv"))
    implementation(libs.litert)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)

    implementation(libs.play.billing)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.play.review.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
