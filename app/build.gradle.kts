plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.efeyamann.dizi"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.efeyamann.dizi"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("DIZI_VERSION_CODE") ?: "1").toInt()
        versionName = "0.2.0"
        // Personal TMDB read token from the CI secret; empty locally (then the Settings field is used).
        buildConfigField("String", "TMDB_TOKEN", "\"${System.getenv("TMDB_TOKEN") ?: ""}\"")
    }
    signingConfigs {
        create("personal") {
            val path = System.getenv("DIZI_KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("DIZI_KEYSTORE_PASSWORD")
                keyPassword = System.getenv("DIZI_KEYSTORE_PASSWORD")
                keyAlias = "dizi"
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
