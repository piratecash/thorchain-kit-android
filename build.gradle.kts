import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.protobuf) apply false
}

val publicationVersion = providers.gradleProperty("VERSION_NAME").orElse("0.0.0-SNAPSHOT").get()
if (publicationVersion != "0.0.0-SNAPSHOT" &&
    !publicationVersion.matches(Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)"))
) {
    throw GradleException("VERSION_NAME must be exact numeric SemVer: MAJOR.MINOR.PATCH")
}

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
