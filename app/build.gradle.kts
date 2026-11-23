plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingStore = providers.environmentVariable("READER_SIGNING_STORE_FILE").orNull
val signingStorePassword = providers.environmentVariable("READER_SIGNING_STORE_PASSWORD").orNull
val signingAlias = providers.environmentVariable("READER_SIGNING_KEY_ALIAS").orNull
val signingKeyPassword = providers.environmentVariable("READER_SIGNING_KEY_PASSWORD").orNull
val signingInputs = listOf(signingStore, signingStorePassword, signingAlias, signingKeyPassword)
val productionSigning = signingInputs.any { it != null }
require(!productionSigning || signingInputs.all { !it.isNullOrBlank() }) {
    "Set all four READER_SIGNING environment variables to build with a production certificate."
}

android {
    namespace = "cz.jan.conversationreader"
    compileSdk = 36
    defaultConfig {
        applicationId = "cz.jan.conversationreader"
        minSdk = 26
        targetSdk = 36
        versionCode = 48
        versionName = "0.10.11"
        buildConfigField("String", "UPDATED_DATE", "\"2026-11-22\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    val releaseSigning = if (productionSigning) signingConfigs.create("production") {
        storeFile = file(checkNotNull(signingStore))
        storePassword = signingStorePassword
        keyAlias = signingAlias
        keyPassword = signingKeyPassword
    } else signingConfigs.getByName("debug")
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
