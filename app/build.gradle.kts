plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.titanpro.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.titanpro.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 80
        versionName = "80.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Only arm64-v8a for modern phones (smaller APK). Add "armeabi-v7a" if needed.
            abiFilters += listOf("arm64-v8a")
        }

        // Chaquopy Python configuration
        python {
            version = "3.11"
            pip {
                // All required packages – installed at build time and bundled inside APK
                install("numpy>=1.24,<2.1")
                install("pandas>=2.0,<2.3")
                install("requests>=2.31,<3")
                install("flask>=3.0,<3.2")
                install("waitress>=3.0,<4")
                install("websocket-client>=1.6,<2")
            }
            // Our main TITAN script lives here
            pyc {
                src = false   // keep source for easier debugging; set true for smaller APK
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Make sure large Python files are not compressed (faster extraction)
    androidResources {
        noCompress += listOf("py", "pyc")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.webkit:webkit:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
