import java.util.Properties

plugins {
    id("com.android.application")
}

// Credenciales de firma de release (keystore.properties está en .gitignore).
// Si no existe, el build de release se firma con la clave de debug de Android Studio.
val propiedadesClave = Properties().apply {
    val archivo = rootProject.file("keystore.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

android {
    namespace = "online.sistemahelpdesk.movil"
    compileSdk = 36

    defaultConfig {
        applicationId = "online.sistemahelpdesk.movil"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (propiedadesClave.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(propiedadesClave.getProperty("storeFile", "keystore/release.jks"))
                storePassword = propiedadesClave.getProperty("storePassword")
                keyAlias = propiedadesClave.getProperty("keyAlias", "shelpdesk")
                keyPassword = propiedadesClave.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Minificado + poda de recursos: menos superficie de ataque y sin código muerto.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
