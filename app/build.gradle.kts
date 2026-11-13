import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.wire)
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

// Protobufs oficiales de Meshtastic (src/main/proto). Solo se generan los tipos alcanzables desde estas raíces.
wire {
    sourcePath { srcDir("src/main/proto") }
    // nanopb.proto solo se importa (atak.proto usa sus opciones); no se genera código para él.
    protoPath { srcDir("src/main/proto-deps") }
    kotlin {}
    root("meshtastic.FromRadio", "meshtastic.ToRadio", "meshtastic.Routing", "meshtastic.AdminMessage", "meshtastic.ChannelSet")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.maplibre)
    implementation(libs.zxing.core)
    implementation(libs.code.scanner)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
