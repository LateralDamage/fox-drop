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
        versionCode = 15
        versionName = "1.5.2"

        // Chat and crew tips run on Firebase. These three values are public identifiers, not secrets
        // (the Firestore rules do the guarding). They live in firebase.properties; without it chat stays hidden.
        val fb = Properties().apply {
            rootProject.file("firebase.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
        }
        buildConfigField("String", "FIREBASE_API_KEY", "\"${fb.getProperty("apiKey", "")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${fb.getProperty("appId", "")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${fb.getProperty("projectId", "")}\"")
        // Player stats come from fortnite-api.com, which needs a free key (dash.fortnite-api.com). It lives in a
        // git-ignored fortnite-api.properties (key=...); without it the Stats screen explains what is missing.
        val fa = Properties().apply {
            rootProject.file("fortnite-api.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
        }
        buildConfigField("String", "FORTNITE_API_KEY", "\"${fa.getProperty("key", "")}\"")
        // Sideloaded builds watch GitHub releases and offer the new APK. Play forbids self-updating, so "play" turns it off.
        buildConfigField("boolean", "SELF_UPDATE", "true")
        // The tip jar links out to a payment page, which Play's payments policy may not allow, so the Play build hides it.
        buildConfigField("boolean", "TIP_JAR", "true")
    }

    // The Play upload key lives outside the repo; without it the "play" build simply isn't signed.
    val keyFile = rootProject.file(providers.gradleProperty("foxdropKeys").getOrElse("C:/Users/james/foxdrop-keys/keystore.properties"))
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
            buildConfigField("boolean", "SELF_UPDATE", "false")
            buildConfigField("boolean", "TIP_JAR", "false")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
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

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.coroutines.play.services)
    implementation(libs.code.scanner)
}

// The Play Console entry was created as foxdrop.myapp, and Play never lets that change. Only the Play
// build uses it; sideloaded APKs keep com.foxdrop.app, so the two install side by side as separate apps.
androidComponents {
    onVariants(selector().withBuildType("play")) { it.applicationId.set("foxdrop.myapp") }
}
