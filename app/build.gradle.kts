import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.navigation.safeargs)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.fitlog"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.fitlog"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Yalnızca herkese açık (public) değerler APK'ya girer.
        // SUPABASE_API_KEY = Supabase "anon"/"publishable" anahtarı (service_role ASLA!).
        // Gemini anahtarı uygulamada değil, Supabase Edge Function secret'ında tutulur.
        val supabaseUrl = localProperties.getProperty("SUPABASE_URL", "").trim('"')
        val supabaseKey = localProperties.getProperty("SUPABASE_API_KEY", "").trim('"')
        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) {
            logger.warn("UYARI: local.properties içinde SUPABASE_URL / SUPABASE_API_KEY tanımlı değil.")
        }
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_API_KEY", "\"$supabaseKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Release imzası: keystore ve şifreler repoya girmez. local.properties ya da
    // ortam değişkenlerinden (CI secret) okunur; tanımlı değilse release imzasız kalır.
    fun signingValue(key: String): String? =
        (System.getenv(key) ?: localProperties.getProperty(key))?.trim('"')?.takeIf { it.isNotEmpty() }
    val releaseStoreFile = signingValue("RELEASE_STORE_FILE")
    val hasReleaseSigning = releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = signingValue("RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("RELEASE_KEY_ALIAS")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("UYARI: RELEASE_STORE_FILE tanımlı değil; release APK/AAB imzasız üretilecek.")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)

    implementation(libs.lottie)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.code.scanner)
    implementation(libs.browser)
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation(libs.security.crypto)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.arch.core.testing)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}