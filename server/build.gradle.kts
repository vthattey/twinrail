// HTTP server, control API and webhooks (M4).
dependencies {
    api(project(":core"))
    implementation(libs.javalin)
    implementation(libs.gson)
    runtimeOnly(libs.slf4j.simple)
    runtimeOnly(project(":rule-packs"))
    runtimeOnly(project(":ui"))
}

tasks.test {
    systemProperty(
        "rulePacksDir",
        project(":rule-packs").projectDir.resolve("src/main/resources/packs").path,
    )
}
