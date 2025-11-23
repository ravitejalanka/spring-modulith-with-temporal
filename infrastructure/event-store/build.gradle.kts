plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

val springModulithVersion: String by rootProject.extra
val coroutinesVersion: String by rootProject.extra

dependencies {
    // Shared kernel
    api(project(":modules:shared-kernel"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:3.2.1")
    implementation("org.springframework:spring-tx:6.1.2")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql:42.7.1")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")

    // Flyway for migrations
    implementation("org.flywaydb:flyway-core:10.4.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVersion")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.1")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
}
