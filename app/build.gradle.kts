plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"

}

android {
    namespace = "com.codecademy.comicreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codecademy.comicreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export location
        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas")
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_19
        targetCompatibility = JavaVersion.VERSION_19
    }

    buildFeatures {
        viewBinding = true
    }
    //  New Kotlin compilerOptions block
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_19)
        }
    }
}

//  Pass schema location to Room via KSP (instead of annotationProcessorOptions)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.legacy.support.v4)
    implementation(libs.activity.ktx) // Adjust version as needed
    implementation(libs.fragment.ktx)
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
    implementation(libs.preference.ktx)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Room Database dependencies
    implementation(libs.room.runtime)  // Core Room library
    implementation(libs.room.ktx)     // Room Kotlin extensions
    ksp(libs.room.compiler.v250) // Annotation processor to generate Room code

    implementation (libs.commons.compress) // For CBZ handling
    implementation (libs.junrar) // For CBR handling
    implementation (libs.photoview) // Enables pinch-to-zoom and drag support on images.
    implementation (libs.viewpager2) // Allows swiping between pages/fragments, often used to flip through images.


    implementation (libs.gson) // JSON parsing and serialization/deserialization using Gson
    implementation (libs.documentfile) // Helps manage files and folders via the Storage Access Framework (DocumentFile API), especially with scoped storage



}