plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSpring)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.kapt)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

springBoot {
    buildInfo()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter.thymeleaf)

    compileOnly(libs.spring.boot.starter.data.jpa)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactor)

    annotationProcessor(libs.spring.boot.configuration.processor)
    kapt(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(
        groupId = "com.paleblueapps",
        artifactId = "springadmin",
        version = "1.0.0",
    )

    pom {
        name = "PaleBlueSpringAdmin"
        description = "A Spring Boot Admin UI module for viewing data stored in a database"
        url = "https://github.com/paleblueapps/pale-blue-spring-admin"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "paleblueapps"
                name = "Pale Blue"
            }
        }
        scm {
            url = "https://github.com/paleblueapps/pale-blue-spring-admin"
        }
    }
}
