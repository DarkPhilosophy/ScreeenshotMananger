plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    // applicationId not needed for library modules
    namespace = "${Coordinates.APP_PACKAGE}core"
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
    implementation(libs.core.ktx)

    // Room runtime annotations (no kapt/ksp in core) - provides @Entity etc.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.datastore.preferences)

    // Gson
    implementation(libs.gson)

    // WorkManager (used by util scheduler if needed)
    implementation(libs.work.runtime.ktx)
}
