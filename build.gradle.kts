plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.3"
}

group = "io.github.harshmittal"
version = "0.0.1-SNAPSHOT"
description = "url-shortener"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.21.0")
    // OpenAPI document and the local-only Swagger UI (D2, R32); the 3.1 line is built on Boot 4.1.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Unit tests and ArchUnit run without Docker; tests tagged "integration" need Testcontainers.
tasks.test {
    useJUnitPlatform { excludeTags("integration") }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (Testcontainers, needs Docker)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
}

jacoco {
    toolVersion = "0.8.15"
}

// Coverage gate: 80% line coverage on domain packages, from unit and integration tests together.
val domainClasses = sourceSets.main.get().output.classesDirs.asFileTree.matching { include("**/domain/**") }

tasks.jacocoTestReport {
    executionData(tasks.test.get(), integrationTest.get())
    classDirectories.setFrom(domainClasses)
    dependsOn(tasks.test, integrationTest)
}

tasks.jacocoTestCoverageVerification {
    executionData(tasks.test.get(), integrationTest.get())
    classDirectories.setFrom(domainClasses)
    dependsOn(tasks.test, integrationTest)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

spotless {
    java {
        palantirJavaFormat("2.102.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestCoverageVerification)
}
