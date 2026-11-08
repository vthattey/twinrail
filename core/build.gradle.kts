// Message codec, rule engine, payment lifecycle, virtual clock, ledger and recorder.
dependencies {
    // ISO 20022 message model and XML (un)marshalling. Part of core's public API (ParsedMessage).
    api(libs.prowide.iso20022)
    // Session files. Already on the classpath through Prowide.
    implementation(libs.gson)
    // Prowide's model is JAXB-annotated and its POM lists the JAXB runtime only at runtime. The glassfish runtime
    // brings the Jakarta XML Bind API, istack and activation that the model needs to compile and run.
    api(libs.jaxb.runtime)
}
