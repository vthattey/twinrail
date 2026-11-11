// HTTP server, control API and webhooks (M4).
plugins {
    application
}

application {
    mainClass = "io.github.vthattey.twinrail.server.Main"
    applicationName = "twinrail"
}

dependencies {
    api(project(":core"))
    implementation(libs.javalin)
    implementation(libs.gson)
    runtimeOnly(libs.slf4j.simple)
    runtimeOnly(project(":rule-packs"))
    runtimeOnly(project(":ui"))
}

dependencies {
    // Contract tests parse the OpenAPI file and validate responses against its schemas.
    testImplementation(libs.snakeyaml.engine)
    testImplementation(libs.json.schema.validator)
}

tasks.test {
    systemProperty(
        "rulePacksDir",
        project(":rule-packs").projectDir.resolve("src/main/resources/packs").path,
    )
}

// jaxb-runtime reaches jaxb-core by two paths; the file is identical, so keep one copy in the distribution.
tasks.withType<AbstractCopyTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
