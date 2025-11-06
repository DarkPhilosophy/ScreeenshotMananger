plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ko.app.core"
    compileSdk = Coordinates.COMPILE_SDK

    defaultConfig {
        minSdk = Coordinates.MIN_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")

    // Room runtime annotations (no kapt/ksp in core) - provides @Entity etc.
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Gson
    implementation("com.google.code.gson:gson:2.13.2")

    // WorkManager (used by util scheduler if needed)
    implementation("androidx.work:work-runtime-ktx:2.11.0")
}
