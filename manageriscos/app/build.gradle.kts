plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.leandro.manageriscos"
    compileSdk = 35 // Mantenha 35 se você já estiver testando com esta API, ou use 34 que é a estável mais recente.

    defaultConfig {
        applicationId = "com.leandro.manageriscos"
        minSdk = 24
        targetSdk = 35 // Mantenha 35 ou use 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
        // compose = false // Se você não usa Compose, pode remover esta linha ou mantê-la como false.
    }
}

dependencies {
    // Core Android & UI
    implementation("androidx.core:core-ktx:1.13.1") // Versão atualizada
    implementation("androidx.appcompat:appcompat:1.7.0") // Versão atualizada
    implementation("com.google.android.material:material:1.12.0") // Versão atualizada
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Firebase
    // Importa o Firebase BoM (Bill of Materials), que gerencia as versões das bibliotecas Firebase.
    // Use a versão estável mais recente recomendada pelo Firebase.
    implementation(platform("com.google.firebase:firebase-bom:33.1.0")) // Versão corrigida e estável
    implementation("com.google.firebase:firebase-database-ktx") // Usando a versão KTX para Kotlin
    implementation("com.google.firebase:firebase-storage-ktx") // Usando a versão KTX
    // implementation("com.google.firebase:firebase-appcheck-debug") // Mantenha se estiver usando AppCheck para debug

    // Mapa e localização
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0") // Versão atualizada

    // Glide (para imagens)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.androidx.activity) // Versão atualizada
    // annotationProcessor("com.github.bumptech.glide:compiler:4.16.0") // Se usar anotações do Glide

    implementation("androidx.gridlayout:gridlayout:1.0.0")

    // Testes
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1") // Versão atualizada
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1") // Versão atualizada
}


