import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Kotlin Gradle plugin is already on the build classpath via :thorchainkit; a versioned
// request here would fail as an unresolvable version conflict.
plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":sample-shared"))
    implementation(compose.desktop.currentOs)

    // Supplies Dispatchers.Main on the JVM; without it the controller's launch { } on the
    // main-thread scope throws at startup.
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "io.horizontalsystems.thorchainkit.sample.desktop.MainKt"
    }
}
