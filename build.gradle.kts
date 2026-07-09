import java.util.Properties
import org.gradle.api.tasks.Delete
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

group = "ink.doa.workbench"
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

val backendModuleNames =
    listOf(
        "workbench-core",
        "workbench-service",
        "workbench-agile",
        "workbench-tenant",
        "workbench-data",
        "workbench-security",
        "workbench-web",
        "workbench-worker",
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

// Kover baseline: all backend modules target 90% line coverage minimum (full test suite).
fun moduleLineCoverageFloor(moduleName: String): Int = 90

// Unit-test-only coverage is reported separately; soft target documented in AGENTS.md (70%+).
fun moduleUnitLineCoverageFloor(moduleName: String): Int = 70

dependencies {
    backendProjects.forEach { kover(it) }
}

extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension>("kover") {
    reports {
        total {
            html {
                onCheck = true
            }
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
            rule("aggregate line coverage") {
                minBound(90)
            }
        }
    }
}

tasks.register("quickCheck") {
    group = "verification"
    description = "Fast local verification: Spotless, Detekt, and unit tests (no integration tests or coverage)."
    dependsOn(
        backendProjects.map { "${it.path}:quickCheck" },
        ":workbench-test-support:quickCheck",
        ":workbench-frontend:quickCheck",
    )
}

tasks.register("check") {
    group = "verification"
    description = "Full verification: static analysis, unit and integration tests, and coverage gates."
    dependsOn(subprojects.map { it.tasks.named("check") })
    dependsOn(tasks.named("koverHtmlReport"), tasks.named("koverXmlReport"))
}

val koverProjects = listOf(rootProject) + backendProjects

tasks.register<Delete>("cleanKover") {
    group = "verification"
    description =
        "Deletes Kover instrumentation and report artifacts so unit-only coverage can be regenerated."
    koverProjects.forEach { project ->
        delete(
            project.layout.buildDirectory.dir("kover"),
            project.layout.buildDirectory.dir("reports/kover"),
            project.layout.buildDirectory.dir("tmp/koverXmlReport"),
        )
    }
}

tasks.register("snapshotModuleUnitKoverReports") {
    group = "verification"
    description =
        "Copies per-module and root Kover XML reports into unit/ subdirectories " +
            "(unit-test coverage snapshot before integration tests run)."
    val rootBuildDir = layout.buildDirectory
    val repoDir = layout.projectDirectory
    val moduleNames = backendModuleNames
    doLast {
        val rootReport = rootBuildDir.file("reports/kover/report.xml").get().asFile
        if (rootReport.isFile) {
            val unitDir = rootBuildDir.dir("reports/kover/unit").get().asFile
            unitDir.mkdirs()
            rootReport.copyTo(unitDir.resolve("report.xml"), overwrite = true)
        }
        moduleNames.forEach { moduleName ->
            val moduleReport =
                repoDir.dir(moduleName).dir("build").file("reports/kover/report.xml").asFile
            if (moduleReport.isFile) {
                val unitDir = repoDir.dir(moduleName).dir("build").dir("reports/kover/unit").asFile
                unitDir.mkdirs()
                moduleReport.copyTo(unitDir.resolve("report.xml"), overwrite = true)
            }
        }
    }
}

tasks.register("koverXmlReportUnit") {
    group = "verification"
    description =
        "Copies the aggregated Kover XML into build/reports/kover/unit/report.xml. " +
            "Requires -Pkover.unitOnly (see koverUnitCoverage)."
    dependsOn(tasks.named("koverXmlReport"))
    onlyIf("kover.unitOnly property must be set") {
        providers.gradleProperty("kover.unitOnly").isPresent
    }
    finalizedBy(tasks.named("snapshotModuleUnitKoverReports"))
}

tasks.register("koverUnitCoverage") {
    group = "verification"
    description =
        "Runs backend unit tests and writes unit-only Kover XML. Invoke with: " +
            "./gradlew koverUnitCoverage -Pkover.unitOnly"
    dependsOn(tasks.named("cleanKover"))
    dependsOn(backendProjects.map { "${it.path}:unitTest" })
    dependsOn(tasks.named("koverXmlReport"))
    finalizedBy(tasks.named("koverXmlReportUnit"))
}

val backendStaticCheckTasks =
    backendProjects.flatMap { project ->
        listOf(
            "${project.path}:spotlessCheck",
            "${project.path}:detektMain",
            "${project.path}:detektTest",
        )
    }
val backendUnitTestTasks = backendProjects.map { "${it.path}:unitTest" }
val backendIntegrationTestTasks = backendProjects.map { "${it.path}:integrationTest" }
val backendKoverVerifyTasks = backendProjects.map { "${it.path}:koverVerify" }

tasks.register("dualCoverageUnitPhase") {
    group = "verification"
    description = "Runs backend unit tests and snapshots unit-only Kover reports."
    dependsOn(tasks.named("cleanKover"))
    dependsOn(backendUnitTestTasks)
    dependsOn(tasks.named("koverXmlReport"))
    finalizedBy(tasks.named("snapshotModuleUnitKoverReports"))
}

tasks.register("dualCoverageFullPhase") {
    group = "verification"
    description = "Runs backend integration tests and regenerates full Kover reports."
    dependsOn(backendIntegrationTestTasks)
    dependsOn(backendKoverVerifyTasks)
    dependsOn(tasks.named("koverXmlReport"), tasks.named("koverHtmlReport"))
    mustRunAfter(tasks.named("dualCoverageUnitPhase"))
}

tasks.register("dualCoverageCheck") {
    group = "verification"
    description =
        "Full verification with dual unit+full coverage snapshots (single unit test run)."
    dependsOn(
        backendStaticCheckTasks,
        ":workbench-test-support:unitTest",
        ":workbench-frontend:pnpmLint",
        tasks.named("dualCoverageUnitPhase"),
        tasks.named("dualCoverageFullPhase"),
        ":workbench-frontend:pnpmTest",
        ":workbench-frontend:snapshotFrontendUnitCoverage",
        tasks.named("koverVerify"),
    )
}

tasks.named("koverVerify") {
    mustRunAfter(tasks.named("dualCoverageFullPhase"))
}

tasks.named("koverHtmlReport") {
    mustRunAfter(backendProjects.map { "${it.path}:check" })
}

tasks.named("koverXmlReport") {
    mustRunAfter(tasks.named("cleanKover"))
    mustRunAfter(backendProjects.map { "${it.path}:check" })
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

    afterEvaluate {
        tasks.named("detekt").configure { enabled = false }
        tasks.named("check").configure {
            dependsOn(tasks.named("detektMain"), tasks.named("detektTest"))
        }

        configureUnitAndIntegrationTests()

        val cleanKoverTask = rootProject.tasks.named("cleanKover")
        tasks.named("unitTest").configure { mustRunAfter(cleanKoverTask) }
        tasks.named("integrationTest").configure { mustRunAfter(cleanKoverTask) }

        tasks.register("quickCheck") {
            group = "verification"
            description = "Spotless, Detekt, and unit tests for this module."
            dependsOn("spotlessCheck", "detektMain", "detektTest", "unitTest")
        }

        tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
            if (
                name == "detektTest" ||
                    name == "detektTestSourceSet" ||
                    name == "detektTestFixtures" ||
                    name == "detektTestFixturesSourceSet"
            ) {
                config.setFrom(
                    rootProject.files("config/detekt/detekt.yml", "config/detekt/detekt-test.yml"),
                )
                buildUponDefaultConfig = true
            }
        }
    }

    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension>("kover") {
        currentProject {
            instrumentation {
                disabledForTestTasks.add("fuzzVerification")
                if (providers.gradleProperty("kover.unitOnly").isPresent) {
                    // Kover wires report tasks through the disabled `test` aggregator, which
                    // otherwise pulls integrationTest back into unit-only runs.
                    disabledForTestTasks.addAll("test", "integrationTest")
                }
            }
        }
        reports {
            total {
                html {
                    onCheck = true
                }
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
                    minBound(moduleLineCoverageFloor(name))
                }
            }
        }
    }

    dependencies {
        "implementation"(platform(springBootBom))
        "testImplementation"(platform(springBootBom))
        "annotationProcessor"(platform(springBootBom))
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

    afterEvaluate {
        val hasFuzzTests =
            fileTree("src/test/kotlin") {
                include("**/*.kt")
            }.any {
                val source = it.readText()
                source.contains("@Tags(\"fuzz\")") || source.contains("@Tag(\"fuzz\")")
            }

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
                systemProperty("kotest.tags", "fuzz")
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
            val packageGlob = "ink.doa.workbench.$moduleSuffix.*"

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

        tasks.register("nightlyModule") {
            group = "verification"
            description = "Nightly per-module verification: check, fuzz, and mutation tests."
            dependsOn("check", "fuzzVerification")
            dependsOn(tasks.matching { it.name == "pitest" })
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

tasks.register("extendedCheck") {
    group = "verification"
    description = "Extended verification: full check plus fuzz and mutation tests"
    dependsOn("check", "fuzzTest", "mutationTest")
}

fun org.gradle.api.Project.configureUnitAndIntegrationTests() {
    val testTaskProvider = tasks.named<Test>("test")
    val testTask = testTaskProvider.get()

    val unitTest =
        tasks.register<Test>("unitTest") {
            group = "verification"
            description = "Runs unit tests (excludes integration and fuzz tags)."
            testClassesDirs = testTask.testClassesDirs
            classpath = testTask.classpath
            failOnNoDiscoveredTests = false
            useJUnitPlatform {
                excludeTags("fuzz", "integration")
            }
            // Kotest specs use @Tags / tags(); JUnit @Tag is not propagated to this filter.
            systemProperty("kotest.tags", "!integration & !fuzz")
        }

    tasks.register<Test>("integrationTest") {
        group = "verification"
        description = "Runs integration-tagged tests."
        testClassesDirs = testTask.testClassesDirs
        classpath = testTask.classpath
        failOnNoDiscoveredTests = false
        mustRunAfter(unitTest)
        useJUnitPlatform {
            includeTags("integration")
        }
        systemProperty("kotest.tags", "integration")
    }

    testTaskProvider.configure {
        description = "Runs unit and integration tests."
        dependsOn(unitTest, tasks.named("integrationTest"))
        enabled = false
    }
}

project(":workbench-test-support") {
    afterEvaluate {
        configureUnitAndIntegrationTests()
        tasks.register("quickCheck") {
            group = "verification"
            description = "Unit tests for workbench-test-support."
            dependsOn("unitTest")
        }
    }
}
