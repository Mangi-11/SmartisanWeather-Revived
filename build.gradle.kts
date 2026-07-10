// AGP 9 uses built-in Kotlin. Declaring a newer KGP on the buildscript classpath is
// the Android-documented way to override AGP's bundled compiler without reapplying
// the incompatible org.jetbrains.kotlin.android plugin.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
}
