import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Keys and per-machine settings live in local.properties (never committed); see local.properties.example.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun local(key: String, default: String = "") = (localProps.getProperty(key) ?: System.getenv(key) ?: default).trim()
fun quoted(key: String, default: String = "") = "\"" + local(key, default).replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Release signing: a keystore kept outside the repository, named in local.properties.
val keystorePath = local("MOTIX_KEYSTORE")

android {
    namespace = "com.hyperscopic.motix"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hyperscopic.motix"
        minSdk = 34 // Pleos Connect is Android Automotive OS 14
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en", "ko")

        // Films: Gemini with Grounding with Google Search (Vertex AI when a project is set, else the Gemini API).
        buildConfigField("String", "GEMINI_API_KEY", quoted("GEMINI_API_KEY"))
        buildConfigField("String", "GEMINI_VERTEX_PROJECT", quoted("GEMINI_VERTEX_PROJECT"))
        buildConfigField("String", "GEMINI_VERTEX_LOCATION", quoted("GEMINI_VERTEX_LOCATION", "us-central1"))
        // Theaters: Places API (New). The same key is tried for YouTube Data API search when it's allowed on the key.
        buildConfigField("String", "GOOGLE_API_KEY", quoted("GOOGLE_API_KEY"))
        // Posters, stills and official trailers: TMDB (either the v3 key or the v4 read token).
        buildConfigField("String", "TMDB_API_KEY", quoted("TMDB_API_KEY"))
        buildConfigField("String", "TMDB_READ_TOKEN", quoted("TMDB_READ_TOKEN"))
    }

    signingConfigs {
        if (keystorePath.isNotEmpty()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = local("MOTIX_KEYSTORE_PASSWORD")
                keyAlias = local("MOTIX_KEY_ALIAS", "motix")
                keyPassword = local("MOTIX_KEY_PASSWORD", local("MOTIX_KEYSTORE_PASSWORD"))
            }
        }
    }

    buildTypes {
        release {
            // The Pleos SDKs use kotlinx.serialization and Gson reflection: not shrunk.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    // AAOS Car API (CarUxRestrictionsManager, CarPropertyManager), provided by the platform at runtime.
    useLibrary("android.car")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // Pleos Connect: route guidance through the car's navigation, and the car's fused position.
    implementation(libs.pleos.navi.helper)
    implementation(libs.pleos.fused.location)
}
