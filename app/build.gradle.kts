plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Versión y firma vienen del entorno (el orquestador las pone al publicar). Nada de secretos en el repo.
// APP_VERSION_NAME: semver, p. ej. 1.2.0. APP_VERSION_CODE: entero que sube en cada release.
val versionNameEnv = System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "0.1.0"
val versionCodeEnv = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 1

// Firma de release: un keystore propio de la app, fuera del repo. Sin esto no hay updates posibles.
val releaseStore = System.getenv("RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "app.subscriptiontracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.subscriptiontracker"
        minSdk = 24
        targetSdk = 36
        versionCode = versionCodeEnv
        versionName = versionNameEnv
        // Repo de GitHub de donde salen las actualizaciones. Lo pone create-app al crear la app.
        buildConfigField("String", "UPDATE_REPO", "\"__UPDATE_REPO__\"")
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.16.0")
    testImplementation("junit:junit:4.13.2")
}
