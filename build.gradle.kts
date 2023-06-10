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

dependencies {
    implementation("com.ontotext.graphdb:graphdb-sdk:10.2.0")
    implementation("com.ontotext.graphdb:graphdb-runtime:10.2.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${getKotlinPluginVersion()}")

    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.9.3")
    testImplementation("com.ontotext.graphdb:graphdb-tests-base:10.2.0")
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

application {
    mainClass.set("MainKt")
}