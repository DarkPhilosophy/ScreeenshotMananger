import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("com.google.devtools.ksp") version "2.2.21-2.0.4"
    id("com.google.dagger.hilt.android") version "2.57.2"
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localFile = file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

android {
    namespace = Coordinates.APP_PACKAGE
    compileSdk = Coordinates.COMPILE_SDK

    defaultConfig {
        applicationId = Coordinates.APP_PACKAGE
        minSdk = Coordinates.MIN_SDK
        targetSdk = Coordinates.TARGET_SDK
        versionCode = Coordinates.APP_VERSION_CODE
        versionName = Coordinates.APP_VERSION_NAME

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword =
                project.properties["storePassword"] as? String ?: "RElyO1UGZvuGFh48IEuqYw=="
            keyAlias = project.properties["keyAlias"] as? String ?: "ko_key"
            keyPassword = project.properties["keyPassword"] as? String ?: "RElyO1UGZvuGFh48IEuqYw=="
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField(
                "String",
                "APP_DISPLAY_NAME",
                "\"${localProperties.getProperty("app.display.name") ?: "Snapify"}\""
            )
            manifestPlaceholders["appName"] =
                localProperties.getProperty("app.display.name") ?: "Snapify"
        }
        debug {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "APP_DISPLAY_NAME",
                "\"${localProperties.getProperty("app.display.name") ?: "Snapify"}\""
            )
            manifestPlaceholders["appName"] =
                localProperties.getProperty("app.display.name") ?: "Snapify"
        }
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
        viewBinding = true
        buildConfig = true
        compose = true
        dataBinding = false
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.7.0"
    }
}

detekt {
    config.setFrom(files("../detekt.yml"))
}

spotless {
    kotlin {
        ktlint("1.3.1").editorConfigOverride(
            mapOf(
                "indent_size" to "4",
                "continuation_indent_size" to "4",
            ),
        )
    }
    kotlinGradle {
        ktlint("1.3.1").editorConfigOverride(
            mapOf(
                "indent_size" to "4",
                "continuation_indent_size" to "4",
            ),
        )
    }
}

dependencies {

    // Core module
    implementation(project(":core"))

    // Jetpack App Startup: Required for InitializationProvider
    implementation(libs.startup.runtime)

    // AndroidX Core
    implementation(libs.core.ktx)
    implementation(libs.appcompat)

    // Material Design
    implementation(libs.material)

    // ConstraintLayout
    implementation(libs.constraintlayout)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    // Firebase
    implementation(libs.firebase.analytics.ktx)
    ksp(libs.room.compiler)

    // Lifecycle & ViewModel
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.service)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Gson for JSON persistence
    implementation(libs.gson)

    // DataStore
    implementation(libs.datastore.preferences)

    // Activity & Fragment KTX
    implementation(libs.activity.ktx)
    implementation(libs.fragment.ktx)

    // RecyclerView
    implementation(libs.recyclerview)

    // Glide for image loading
    implementation(libs.glide)
    ksp(libs.glide.compiler)

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Compose Activity
    implementation(libs.activity.compose)

    // Compose Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)

    // Compose Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Accompanist for system UI controller
    implementation(libs.accompanist.systemuicontroller)

    // Coil for image loading (Compose compatible)
    implementation(libs.coil.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Hilt Work
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    // ExoPlayer for video playback
    implementation(libs.exoplayer)
    implementation(libs.exoplayerUi)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)

    // Compose Testing
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Static Analysis
    // detekt formatting plugin handled at root
}
