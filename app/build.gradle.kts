import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.foxdrop.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.foxdrop.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"
    }

    // The Play upload key lives outside the repo; without it the "play" build simply isn't signed.
    val keyFile = rootProject.file(providers.gradleProperty("foxdropKeys").getOrElse("C:/Users/densonjr/foxdrop-keys/keystore.properties"))
    val playKey = if (keyFile.exists()) Properties().apply { keyFile.inputStream().use(::load) } else null
    signingConfigs {
        if (playKey != null) create("upload") {
            storeFile = file(playKey.getProperty("storeFile"))
            storePassword = playKey.getProperty("storePassword")
            keyAlias = playKey.getProperty("keyAlias")
            keyPassword = playKey.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // R8 strips the unused material-icons-extended code (30 MB of dex otherwise).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the APK can be sideloaded without a release keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
        // Google Play: same as release, signed with the upload key. `gradlew bundlePlay` -> app-play.aab.
        // Sideloaded copies stay on the debug key, so the GitHub link keeps updating existing installs.
        create("play") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.findByName("upload")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.okhttp)
    implementation(libs.work.runtime)
}

// The Play Console entry was created as foxdrop.myapp, and Play never lets that change. Only the Play
// build uses it; sideloaded APKs keep com.foxdrop.app, so the two install side by side as separate apps.
androidComponents {
    onVariants(selector().withBuildType("play")) { it.applicationId.set("foxdrop.myapp") }
}
