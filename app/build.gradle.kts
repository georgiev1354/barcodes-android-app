plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gg.barcodes"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gg.barcodes"
        minSdk = 29
        targetSdk = 37
        // Every release published to the update server needs a higher versionCode (docs/self-update.md).
        // -PversionCode=N overrides it, for testing updates between two local builds.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(9)
        versionName = "4.4"

        // PLAY-STORE: no update server on Google Play (docs/play-store.md).
        // // One folder per package on the update server, so several apps (or variants) never mix.
        // buildConfigField("String", "UPDATE_URL", "\"https://apks.gg-webapp.com/$applicationId/check-update\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // PLAY-STORE: no update server on Google Play (docs/play-store.md).
        // debug {
        //     // A debug build is signed with the debug key and can't update from the release server.
        //     // Pass -PupdateUrl=http://10.0.2.2:8000/<applicationId>/check-update to test against a local one.
        //     buildConfigField("String", "UPDATE_URL", "\"${providers.gradleProperty("updateUrl").getOrElse("")}\"")
        // }

        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// PLAY-STORE: leaves the self-update code (update/ and its test) out of the build, so the Play
// bundle has no code that installs APKs. The sources stay untouched (docs/play-store.md).
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude("com/gg/barcodes/update/**")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.zxing.cpp)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}