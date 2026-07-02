plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.pitest) apply false
    alias(libs.plugins.node) apply false
}

group = "doa.ink.workbench"
version = "0.1.0-SNAPSHOT"

val detektToolVersion = libs.versions.detekt.get()
val springBootBom = "org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"
val kotlinReflectDependency = libs.kotlin.reflect
val coroutinesCoreDependency = libs.kotlinx.coroutines.core
val serializationJsonDependency = libs.kotlinx.serialization.json
val kotestRunnerDependency = libs.kotest.runner
val kotestAssertionsDependency = libs.kotest.assertions
val kotestPropertyDependency = libs.kotest.property
val mockkDependency = libs.mockk
val coroutinesTestDependency = libs.kotlinx.coroutines.test

subprojects {
    group = rootProject.group
    version = rootProject.version
}

val backendProjects = listOf(
    project(":workbench-core"),
    project(":workbench-service"),
    project(":workbench-data"),
    project(":workbench-security"),
    project(":workbench-web"),
    project(":workbench-worker"),
)

configure(backendProjects) {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "dev.detekt")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>("kotlin") {
        jvmToolchain(25)
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension>("spotless") {
        kotlin {
            target("src/**/*.kt")
            ktfmt("0.63").googleStyle()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktfmt("0.63").googleStyle()
        }
    }

    extensions.configure<dev.detekt.gradle.extensions.DetektExtension>("detekt") {
        toolVersion = detektToolVersion
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
    }

    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension>("kover") {
        reports {
            filters {
                excludes {
                    classes(
                        "*.WorkbenchApplication*",
                        "*.WorkbenchWorkerApplication*",
                        "*.api.*Configuration",
                        "*.infrastructure.persistence.*Configuration",
                    )
                }
            }
            verify {
                rule("line coverage") {
                    minBound(1)
                }
            }
        }
    }

    dependencies {
        "implementation"(platform(springBootBom))
        "testImplementation"(platform(springBootBom))
        "implementation"(kotlinReflectDependency)
        "implementation"(coroutinesCoreDependency)
        "implementation"(serializationJsonDependency)
        "testImplementation"(kotestRunnerDependency)
        "testImplementation"(kotestAssertionsDependency)
        "testImplementation"(kotestPropertyDependency)
        "testImplementation"(mockkDependency)
        "testImplementation"(coroutinesTestDependency)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.register("dev") {
    group = "application"
    description = "Starts the Spring Boot API and SvelteKit dev server. Run the printed commands in separate terminals for log clarity."
    doLast {
        println("Backend:  ./gradlew :workbench-web:bootRun --args='--spring.profiles.active=local'")
        println("Worker:   ./gradlew :workbench-worker:bootRun --args='--spring.profiles.active=local,worker'")
        println("Frontend: ./gradlew :workbench-frontend:pnpmDev")
    }
}
