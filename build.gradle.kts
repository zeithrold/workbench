import java.util.Properties
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.kover)
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

val pitestProperties =
    Properties().apply {
        rootProject.file("config/pitest/pitest.properties").inputStream().use { load(it) }
    }

fun pitestProperty(key: String): String =
    pitestProperties.getProperty(key)
        ?: error("Missing pitest property: $key")

fun pitestCsvProperty(key: String): Set<String> =
    pitestProperty(key)
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

subprojects {
    group = rootProject.group
    version = rootProject.version
}

val backendProjects = listOf(
    project(":workbench-core"),
    project(":workbench-service"),
    project(":workbench-agile"),
    project(":workbench-tenant"),
    project(":workbench-data"),
    project(":workbench-security"),
    project(":workbench-web"),
    project(":workbench-worker"),
)

val koverExcludedClasses =
  arrayOf(
      "*.WorkbenchApplication*",
      "*.WorkbenchWorkerApplication*",
      "*.api.*Configuration",
      "*.security.*Configuration",
      "*.infrastructure.persistence.*Configuration",
      "*.data.persistence.*Configuration",
  )

dependencies {
    backendProjects.forEach { kover(it) }
}

extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension>("kover") {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
        filters {
            excludes {
                classes(*koverExcludedClasses)
            }
        }
    }
}

fun pitestEnabledProjects(): List<org.gradle.api.Project> =
    backendProjects.filter { project ->
        val hasKotlinTests =
            project.fileTree("src/test/kotlin") {
                include("**/*.kt")
            }.files.isNotEmpty()
        project.name !in pitestCsvProperty("skipModules") &&
            (!pitestProperty("autoSkipModulesWithoutTests").toBoolean() || hasKotlinTests)
    }

apply(plugin = "info.solidsoft.pitest.aggregator")

configure(backendProjects) {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "dev.detekt")
    apply(plugin = "org.jetbrains.kotlinx.kover")
    apply(plugin = "info.solidsoft.pitest")

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
            total {
                xml {
                    onCheck = true
                }
            }
            filters {
                excludes {
                    classes(*koverExcludedClasses)
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

    tasks.withType<Test>().matching { it.name != "fuzzVerification" }.configureEach {
        useJUnitPlatform()
    }

    tasks.named<Test>("test") {
        useJUnitPlatform {
            excludeTags("fuzz")
        }
    }

    afterEvaluate {
        val hasFuzzTests =
            fileTree("src/test/kotlin") {
                include("**/*.kt")
            }.any { it.readText().contains("@Tag(\"fuzz\")") }

        if (hasFuzzTests) {
            tasks.register<Test>("fuzzVerification") {
                group = "verification"
                description = "Runs property-based (fuzz) tests in this module"
                val testTask = tasks.named<Test>("test").get()
                testClassesDirs = testTask.testClassesDirs
                classpath = testTask.classpath
                failOnNoDiscoveredTests = false
                useJUnitPlatform {
                    includeTags("fuzz")
                }
            }
        } else {
            tasks.register("fuzzVerification") {
                group = "verification"
                description = "No fuzz tests configured for this module"
            }
        }

        if (hasFuzzTests) {
            extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension>("kover") {
                currentProject {
                    instrumentation {
                        disabledForTestTasks.add("fuzzVerification")
                    }
                }
            }
        }

        val skipModules = pitestCsvProperty("skipModules")
        val autoSkipModulesWithoutTests = pitestProperty("autoSkipModulesWithoutTests").toBoolean()
        val hasKotlinTests =
            fileTree("src/test/kotlin") {
                include("**/*.kt")
            }.files.isNotEmpty()
        val skipPitest = name in skipModules || (autoSkipModulesWithoutTests && !hasKotlinTests)

        if (skipPitest) {
            tasks.matching { it.name == "pitest" }.configureEach {
                enabled = false
            }
        } else {
            val moduleSuffix = name.removePrefix("workbench-")
            val packageGlob = "doa.ink.workbench.$moduleSuffix.*"

            extensions.configure<info.solidsoft.gradle.pitest.PitestPluginExtension>("pitest") {
                junit5PluginVersion.set(pitestProperty("junit5PluginVersion"))
                targetClasses.set(setOf(packageGlob))
                targetTests.set(setOf(packageGlob))
                mutationThreshold.set(pitestProperty("mutationThreshold").toInt())
                avoidCallsTo.set(pitestCsvProperty("avoidCallsTo"))
                excludedClasses.set(pitestCsvProperty("excludedClasses"))
                excludedTestClasses.set(pitestCsvProperty("excludedTestClasses"))
                excludedGroups.set(pitestCsvProperty("excludedGroups"))
                outputFormats.set(pitestCsvProperty("perModuleOutputFormats"))
                timestampedReports.set(pitestProperty("timestampedReports").toBoolean())
                exportLineCoverage.set(pitestProperty("exportLineCoverage").toBoolean())
                threads.set(Runtime.getRuntime().availableProcessors())
                if (name == "workbench-core") {
                    reportAggregator {
                        mutationThreshold.set(pitestProperty("mutationThreshold").toInt())
                    }
                }
            }
        }
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

tasks.register("fuzzTest") {
    group = "verification"
    description = "Runs property-based (fuzz) tests"
    dependsOn(backendProjects.map { "${it.path}:fuzzVerification" })
}

val pitestTaskPaths = pitestEnabledProjects().map { "${it.path}:pitest" }

tasks.named("pitestReportAggregate") {
    mustRunAfter(pitestTaskPaths)
}

tasks.register("mutationTest") {
    group = "verification"
    description = "Runs PIT mutation testing across backend modules"
    dependsOn(pitestTaskPaths)
    dependsOn("pitestReportAggregate")
}
