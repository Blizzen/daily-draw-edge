import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

application {
    // CLI smoke test: ./gradlew :core:run --args="<oddsApiKey>"
    mainClass.set("online.blizzen.dailydraw.CliKt")
}

// Emit Java 17 bytecode so the Android :app can consume :core (compiled with the
// installed JDK 21; no separate JDK 17 toolchain required).
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test { useJUnitPlatform() }
