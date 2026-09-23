plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.protobuf)
}

dependencies {
    api(libs.protobuf.javalite)
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                named("java") {
                    option("lite")
                }
            }
        }
    }
}

// 17, not 21: the pre-KMP AAR shipped these classes as Java 17 bytecode, which D8 accepts.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "thorchainkit-proto"
            from(components["java"])
        }
    }
}
