plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

application {
    // CLI entrypoint: ./gradlew run --args="<oddsApiKey> [hand.json]"
    mainClass.set("online.blizzen.dailydraw.CliKt")
}

kotlin { jvmToolchain(21) }

tasks.test { useJUnitPlatform() }
