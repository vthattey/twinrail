// A standalone example: it depends on the published module, not on this repository's build.
// Run it with Docker available and a Twinrail image (see README.md):  ../../gradlew test
plugins {
    java
}

repositories {
    mavenLocal() // for a locally published snapshot of the module
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val twinrailVersion = providers.gradleProperty("twinrailVersion").getOrElse("0.1.0-SNAPSHOT")

dependencies {
    testImplementation("io.github.vthattey.twinrail:twinrail-testcontainers:$twinrailVersion")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Use your own image, for example one built with `docker build -t twinrail .` in the repository.
    environment("TWINRAIL_IMAGE", providers.environmentVariable("TWINRAIL_IMAGE").getOrElse(""))
}
