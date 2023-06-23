import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
    kotlin("jvm") version "1.8.21"
    java
    application
}


group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://maven.ontotext.com/repository/owlim-releases")
    }
}

val graphdbVersion = "10.2.0"

dependencies {
    implementation("com.ontotext.graphdb:graphdb-sdk:$graphdbVersion")
    implementation("com.ontotext.graphdb:graphdb-runtime:$graphdbVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${getKotlinPluginVersion()}")

    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.9.3")
    testImplementation("com.ontotext.graphdb:graphdb-tests-base:$graphdbVersion")
    testImplementation(kotlin("test"))
}


tasks.test {
    useJUnitPlatform {
        includeEngines("junit-vintage")
    }
}

kotlin {
    jvmToolchain(11)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}

application {
    mainClass.set("MainKt")
}