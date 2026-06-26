plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("me.matsumo.pholio.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.json)
    implementation(libs.logback.classic)
    implementation(libs.hikari.cp)
    implementation(libs.metadata.extractor)
    implementation(libs.sqlite.jdbc)
    implementation(libs.ulid.creator)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}
