plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // Order module only
    implementation(project(":modules:shared-kernel"))
    implementation(project(":modules:order-management"))
    implementation(project(":modules:temporal-workflows"))
    implementation(project(":infrastructure:messaging"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Kafka (for microservices mode)
    implementation("org.springframework.kafka:spring-kafka")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")

    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    enabled = true
}
