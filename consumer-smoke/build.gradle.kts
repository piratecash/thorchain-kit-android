import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.3.20"
    id("com.android.library") version "8.12.1"
}

val thorchainKitVersion = providers.gradleProperty("THORCHAIN_KIT_VERSION").getOrElse("0.0.0-SNAPSHOT")

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation("com.github.piratecash.thorchain-kit-android:thorchainkit:$thorchainKitVersion")
            }
        }
    }
}

android {
    namespace = "io.horizontalsystems.thorchainkit.consumersmoke"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
