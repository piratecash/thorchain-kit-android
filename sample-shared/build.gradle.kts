import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidLibrary {
        namespace = "io.horizontalsystems.thorchainkit.sample.shared"
        compileSdk = 36
        minSdk = 27

        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":thorchainkit"))
                implementation(libs.hd.wallet.kit)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.compose.multiplatform.runtime)
                implementation(libs.compose.multiplatform.foundation)
                implementation(libs.compose.multiplatform.material3)
                implementation(libs.compose.multiplatform.ui)
                implementation(libs.compose.multiplatform.ui.tooling.preview)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}
