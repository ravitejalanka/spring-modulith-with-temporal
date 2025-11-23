plugins {
    kotlin("jvm")
}

val arrowVersion: String by rootProject.extra
val coroutinesVersion: String by rootProject.extra

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutinesVersion")

    // Arrow-kt for functional programming
    api("io.arrow-kt:arrow-core:$arrowVersion")
    api("io.arrow-kt:arrow-fx-coroutines:$arrowVersion")

    // Jackson for JSON
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.0")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-assertions-arrow:5.8.0")
}
