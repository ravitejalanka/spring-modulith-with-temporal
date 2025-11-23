plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

val springModulithVersion: String by rootProject.extra
val coroutinesVersion: String by rootProject.extra

dependencies {
    // Shared modules
    api(project(":modules:shared-kernel"))
    implementation(project(":modules:temporal-workflows"))
    implementation(project(":infrastructure:messaging"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web:3.2.1")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:3.2.1")
    implementation("org.springframework.boot:spring-boot-starter-validation:3.2.1")

    // Spring Modulith
    implementation("org.springframework.modulith:spring-modulith-api:$springModulithVersion")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVersion")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.1")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test:$springModulithVersion")
}
