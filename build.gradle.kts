// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("com.diffplug.spotless") version "8.0.0" apply false
}

buildscript {
    dependencies {
        // Force a compatible JavaPoet on the buildscript classpath to avoid Hilt plugin runtime errors
        classpath("com.squareup:javapoet:1.13.0")
    }
}

allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "com.diffplug.spotless")

    configurations.all {
        resolutionStrategy.force("com.squareup:javapoet:1.13.0")
    }
}


