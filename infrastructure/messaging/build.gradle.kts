plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

val springModulithVersion: String by rootProject.extra
val coroutinesVersion: String by rootProject.extra

dependencies {
    // Shared kernel
    api(project(":modules:shared-kernel"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter:3.2.1")
    implementation("org.springframework:spring-context:6.1.2")

    // Spring Modulith for event publication registry
    implementation("org.springframework.modulith:spring-modulith-events-api:$springModulithVersion")
    implementation("org.springframework.modulith:spring-modulith-events-core:$springModulithVersion")

    // Kafka (optional, only loaded in microservices mode)
    compileOnly("org.springframework.kafka:spring-kafka:3.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVersion")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.1")
    testImplementation("org.springframework.kafka:spring-kafka-test:3.1.1")
}
