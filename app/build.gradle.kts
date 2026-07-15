plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

android {
    namespace = "com.smartisan.weather"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.smartisanweather.revived"
        minSdk = 27
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            optimization {
                keepRules {
                    // The app has no XML onClick, JNI, JS bridge, reflected enum, or
                    // custom string-based View property. Required Parcelable entry
                    // points are retained explicitly in proguard-rules.pro.
                    includeDefault = false
                }
            }
            proguardFiles("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { versionName ->
                    "SmartisanWeather-Revived-$versionName.apk"
                }
            )
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room3.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
