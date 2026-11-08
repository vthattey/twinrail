// Message codec, rule engine, payment lifecycle, virtual clock, ledger and recorder.
dependencies {
    // ISO 20022 message model and XML (un)marshalling. Part of core's public API (ParsedMessage).
    api(libs.prowide.iso20022)
    // Session files. Already on the classpath through Prowide.
    implementation(libs.gson)
    // Rule packs and scenario files. Safe by default: plain data only, no arbitrary object construction.
    implementation(libs.snakeyaml.engine)
    // Prowide's model is JAXB-annotated and its POM lists the JAXB runtime only at runtime. The glassfish runtime
    // brings the Jakarta XML Bind API, istack and activation that the model needs to compile and run.
    api(libs.jaxb.runtime)

    // Tests load the shipped packs and schemas, and check the loaders against the published JSON Schemas.
    testImplementation(project(":rule-packs"))
    testImplementation(libs.json.schema.validator)
}

tasks.test {
    systemProperty(
        "rulePacksDir",
        project(":rule-packs").projectDir.resolve("src/main/resources/packs").path,
    )
}
