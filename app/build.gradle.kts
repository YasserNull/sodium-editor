plugins {
    id("com.android.application")
}

android {
    namespace = "com.yn.sodiumeditordemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yn.sodiumeditordemo"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        multiDexEnabled = true

        
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    
}

configurations.all {
    // Project is Java-only; drop Kotlin stdlib variants pulled transitively.
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation(project(":sodium-editor"))
}
