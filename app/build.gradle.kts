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

// ── Kendi kurulumuna ait değerler (docs/en/setup.md) ──────────────────────
// Hepsi local.properties'ten (git'e girmez) ya da aynı adlı ortam değişkenlerinden okunur.
fun localValue(key: String): String =
    (System.getenv(key) ?: localProperties.getProperty(key, "")).trim().trim('"')

// Yalnızca herkese açık (public) değerler APK'ya girer.
// SUPABASE_API_KEY = Supabase "anon"/"publishable" anahtarı (service_role ASLA!).
// Gemini anahtarı uygulamada değil, Supabase Edge Function secret'ında tutulur.
val supabaseUrl = localValue("SUPABASE_URL")
val supabaseKey = localValue("SUPABASE_API_KEY")
val privacyPolicyUrl = localValue("PRIVACY_POLICY_URL")
    .ifEmpty { "https://github.com/KaanKaya17/FitLog/blob/master/PRIVACY.md" }

// Firebase isteğe bağlı: app/google-services.json yoksa, derleme kırılmasın diye
// sahte değerlerle bir yer tutucu üretilir. Bildirim, analitik ve hata raporları
// o durumda çalışmaz; uygulamanın geri kalanı normal çalışır.
val appId = "com.example.fitlog"
val googleServicesFile = file("google-services.json")
val placeholderMarker = "fitlog-placeholder"
if (!googleServicesFile.exists()) {
    logger.warn("FitLog: app/google-services.json bulunamadı; Firebase kapalı (yer tutucu oluşturuldu).")
    googleServicesFile.writeText(
        """
        {
          "project_info": { "project_number": "000000000000", "project_id": "$placeholderMarker",
                            "storage_bucket": "$placeholderMarker.appspot.com" },
          "client": [{
            "client_info": { "mobilesdk_app_id": "1:000000000000:android:0000000000000000",
                             "android_client_info": { "package_name": "$appId" } },
            "oauth_client": [],
            "api_key": [{ "current_key": "placeholder" }],
            "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
          }],
          "configuration_version": "1"
        }
        """.trimIndent()
    )
}
val firebaseConfigured = !googleServicesFile.readText().contains(placeholderMarker)

android {
    namespace = "com.example.fitlog"
    compileSdk = 36

    defaultConfig {
        applicationId = appId
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Yalnızca herkese açık (public) değerler APK'ya girer.
        // SUPABASE_API_KEY = Supabase "anon"/"publishable" anahtarı (service_role ASLA!).
        // Gemini anahtarı uygulamada değil, Supabase Edge Function secret'ında tutulur.
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_API_KEY", "\"$supabaseKey\"")
        // Kendi google-services.json dosyası yoksa yer tutucuyla derlenir; Firebase özellikleri kapalı kalır.
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        // Uygulama içindeki "Gizlilik politikası" bağlantısı.
        resValue("string", "privacy_policy_url", privacyPolicyUrl)
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        animationsDisabled = true
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

// Supabase ayarları olmadan derlenen uygulama açılışta çöker; bunun yerine derleme
// net bir mesajla dursun. (Gradle senkronizasyonu yine çalışır.)
val checkFitLogConfig by tasks.registering {
    val url = supabaseUrl
    val key = supabaseKey
    doLast {
        if (url.isEmpty() || key.isEmpty()) {
            throw GradleException(
                "FitLog: SUPABASE_URL / SUPABASE_API_KEY tanımlı değil (not set).\n" +
                "local.properties dosyasına kendi Supabase projenin değerlerini ekle / " +
                "add your own Supabase project's values to local.properties:\n" +
                "  SUPABASE_URL=https://<project-ref>.supabase.co\n" +
                "  SUPABASE_API_KEY=<anon-or-publishable-key>\n" +
                "Ayrıntılar: docs/en/setup.md (EN) · docs/tr/kurulum.md (TR)"
            )
        }
        if (!url.startsWith("https://")) {
            throw GradleException("FitLog: SUPABASE_URL https:// ile başlamalı / must start with https:// (got: $url)")
        }
    }
}
tasks.named("preBuild") { dependsOn(checkFitLogConfig) }
