import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// AGP and the Kotlin Gradle plugin are already on the build classpath via the root's
// android.library and kotlin.multiplatform plugins, so requesting explicit versions here would
// fail as an unresolvable version conflict.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.horizontalsystems.thorchainkit.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.horizontalsystems.thorchainkit"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}

dependencies {
    implementation(project(":sample-shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.multiplatform.material3)
}
