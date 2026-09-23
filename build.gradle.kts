import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.protobuf) apply false
}

// JitPack builds a tag or a commit and passes it in; local builds fall back to a snapshot.
val publicationVersion: String = providers.environmentVariable("JITPACK_VERSION")
    .orElse(providers.environmentVariable("VERSION"))
    .orElse(providers.gradleProperty("VERSION_NAME"))
    .orElse("0.0.0-SNAPSHOT")
    .get()

val publishedModules = setOf("thorchainkit", "thorchainkit-proto")

subprojects {
    if (name in publishedModules) {
        group = "com.github.piratecash.thorchain-kit-android"
        version = publicationVersion
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> { jvmToolchain(21) }
    }
}
