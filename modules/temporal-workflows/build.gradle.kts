plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

val temporalVersion: String by rootProject.extra
val coroutinesVersion: String by rootProject.extra

dependencies {
    // Shared modules
    api(project(":modules:shared-kernel"))
    implementation(project(":modules:order-management"))

    // Temporal SDK
    api("io.temporal:temporal-sdk:$temporalVersion")
    implementation("io.temporal:temporal-kotlin:$temporalVersion")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter:3.2.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.1")
    testImplementation("io.temporal:temporal-testing:$temporalVersion")
}
