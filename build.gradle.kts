import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.spring") version "2.0.21" apply false
    kotlin("plugin.jpa") version "2.0.21" apply false
    id("org.springframework.boot") version "3.2.1" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
}

allprojects {
    group = "com.example.modulith"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs += "-Xjsr305=strict"
            freeCompilerArgs += "-Xcontext-receivers"
            jvmTarget = "21"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

// Define common dependencies
val arrowVersion = "1.2.1"
val temporalVersion = "1.20.1"
val springModulithVersion = "1.1.1"
val coroutinesVersion = "1.8.0"

extra["arrowVersion"] = arrowVersion
extra["temporalVersion"] = temporalVersion
extra["springModulithVersion"] = springModulithVersion
extra["coroutinesVersion"] = coroutinesVersion
