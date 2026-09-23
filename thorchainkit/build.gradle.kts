import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    `maven-publish`
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // 21, not 17: hd-wallet-kit-kmp and secp256k1-kmp-jni-jvm publish Java 21 bytecode only.
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        // Not commonMain: the kit is JVM code shared by the two JVM-backed targets only.
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // Proto classes shipped inside the pre-KMP AAR, so consumers compiled against them.
                api(project(":thorchainkit-proto"))
                implementation(libs.hd.wallet.kit)
                implementation(libs.secp256k1.kmp)
                implementation(libs.retrofit)
                implementation(libs.retrofit.converter.gson)
                implementation(libs.retrofit.converter.scalars)
                implementation(libs.okhttp.logging.interceptor)
                implementation(libs.gson)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.room.runtime)
            }
        }
        androidMain {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.secp256k1.jni.android)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.room.ktx)
                implementation(libs.sqlcipher.android)
            }
        }
        val desktopMain by getting {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.secp256k1.jni.jvm)
                implementation(libs.sqlcipher.driver)
            }
        }

        val jvmCommonTest by creating {
            dependsOn(commonTest.get())
            dependencies {
                implementation(libs.junit)
            }
        }
        val androidUnitTest by getting {
            dependsOn(jvmCommonTest)
            // Host-JVM unit tests cannot load the Android JNI build of secp256k1.
            dependencies {
                runtimeOnly(libs.secp256k1.jni.jvm)
            }
        }
        // Runs on an emulator only; never part of the published AAR.
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
            }
        }
        val desktopTest by getting {
            dependsOn(jvmCommonTest)
            dependencies {
                // Only to write plaintext fixtures; the kit itself opens databases through SQLCipher.
                implementation(libs.sqlite.bundled)
            }
        }
    }
}

// Room rejects blocking DAO functions unless android.content.Context is visible to the processor.
// The DAOs are shared with Android, so KSP gets a bare marker type; it is never packaged.
val roomAndroidMarker = java.sourceSets.create("roomAndroidMarker") {
    java.setSrcDirs(listOf(rootProject.file("gradle/room-android-marker/java")))
}

dependencies {
    "desktopMainCompileOnly"(roomAndroidMarker.output)
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
}

android {
    namespace = "io.horizontalsystems.thorchainkit"
    compileSdk = 36

    defaultConfig {
        minSdk = 27

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        targetSdk = 33
    }
    testOptions {
        targetSdk = 33
    }
}
