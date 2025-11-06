plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("com.google.devtools.ksp") version "2.2.21-2.0.4"
    id("com.google.dagger.hilt.android") version "2.57.2"
}

android {
    namespace = Coordinates.APP_ID
    compileSdk = Coordinates.COMPILE_SDK

    defaultConfig {
        applicationId = Coordinates.APP_ID
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
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
    implementation("androidx.startup:startup-runtime:1.2.0")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Material Design
    implementation("com.google.android.material:material:1.13.0")

    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // Room Database
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Gson for JSON persistence
    implementation("com.google.code.gson:gson:2.13.2")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Activity & Fragment KTX
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:5.0.5")
    ksp("com.github.bumptech.glide:compiler:5.0.5")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Compose Activity
    implementation("androidx.activity:activity-compose:1.11.0")

    // Compose Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    // Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Accompanist for system UI controller
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0")

    // Coil for image loading (Compose compatible)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-compiler:2.57.2")
    // Hilt Work
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    // Compose Testing
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Static Analysis
    // detekt formatting plugin handled at root
}
