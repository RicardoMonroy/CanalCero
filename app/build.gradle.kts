import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// La keystore y sus contraseñas viven fuera del repo (es público). Sin este archivo la release sale sin firmar.
val signingProps =
    file(
        providers
            .gradleProperty("canalcero.signing")
            .getOrElse("${System.getProperty("user.home")}/.config/canalcero/signing.properties"),
    )

android {
    namespace = "io.github.ricardomonroy.canalcero"
    compileSdk = 37

    defaultConfig {
        // No se puede cambiar una vez que alguien instale la app.
        applicationId = "io.github.ricardomonroy.canalcero"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (signingProps.isFile) {
            val p = Properties().apply { signingProps.inputStream().use(::load) }
            create("release") {
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
