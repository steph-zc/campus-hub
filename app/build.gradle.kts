plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    // Kover (JetBrains): mede quanto do código os testes unitários executam
    alias(libs.plugins.kover)
}

android {
    namespace = "br.com.uri.campushub"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "br.com.uri.campushub"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        // o Robolectric lê os recursos do app (textos, layouts) nos testes unitários
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // o Robolectric simulando o Android 16 (SDK 36) precisa acessar uma parte interna do Java 21
            test.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }
}

// relatório de cobertura dos testes unitários: ./gradlew koverHtmlReportDebug
kover {
    reports {
        filters {
            excludes {
                // a interface (telas, adapters e diálogos) é testada no aparelho pelos testes de ponta a ponta;
                // o gravador de áudio depende do microfone, que não existe nos testes unitários
                packages("br.com.uri.campushub.ui")
                classes("br.com.uri.campushub.data.media.AudioRecorder")
                // classes geradas pelo build
                classes("*.R", "*.R$*", "*.BuildConfig")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.osmdroid)
    implementation(libs.glide)
    // testes unitários: JUnit 4, MockK (simula o Firebase) e Robolectric (simula o Android na JVM)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}