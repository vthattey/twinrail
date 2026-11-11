// Testcontainers module (M8): a container class for the Twinrail image and a typed Java client for its
// control API. Published as io.github.vthattey.twinrail:twinrail-testcontainers.
plugins {
    `maven-publish`
    signing
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    // Part of the public API: TwinrailContainer extends GenericContainer.
    api(libs.testcontainers)
    // JSON for the control API client. Already a dependency of core.
    implementation(libs.gson)

    // The client is tested against the real server running in-process (no Docker needed).
    testImplementation(project(":server"))
    testImplementation(project(":rule-packs"))
}

// The default image tag is the module's own version, so a release of the module pairs with its image.
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("twinrail-testcontainers.properties") {
        expand("version" to project.version)
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "twinrail-testcontainers"
            from(components["java"])
            pom {
                name = "Twinrail Testcontainers module"
                description = "Starts the Twinrail payment-rail emulator in a Testcontainers container and " +
                    "drives it through a typed Java client."
                url = "https://github.com/vthattey/twinrail"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "vthattey"
                        name = "vthattey"
                        url = "https://github.com/vthattey"
                    }
                }
                scm {
                    url = "https://github.com/vthattey/twinrail"
                    connection = "scm:git:https://github.com/vthattey/twinrail.git"
                    developerConnection = "scm:git:ssh://git@github.com/vthattey/twinrail.git"
                }
            }
        }
    }
}

// Signing is for releases only: it needs a key, supplied through the environment, never the repository.
signing {
    val key = providers.environmentVariable("SIGNING_KEY")
    val password = providers.environmentVariable("SIGNING_PASSWORD")
    isRequired = false
    if (key.isPresent) {
        useInMemoryPgpKeys(key.get(), password.orNull)
        sign(publishing.publications["maven"])
    }
}
