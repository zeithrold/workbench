package workbench.gradle

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.tasks.Exec
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class RootConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("base")
            pluginManager.apply("org.jetbrains.kotlinx.kover")
            pluginManager.apply("info.solidsoft.pitest.aggregator")

            afterEvaluate {
                subprojects {
                    group = rootProject.group
                    version = rootProject.version
                }
            }

            val backendProjects = backendProjectPaths.map { project(it) }
            backendProjects.forEach { it.pluginManager.apply("workbench.backend-conventions") }
            project(":workbench-test-support").pluginManager.apply("workbench.testing-conventions")

            configureRootKover(backendProjects)
            registerRootVerificationTasks(backendProjects)
        }
    }

    private fun Project.configureRootKover(backendProjects: List<Project>) {
        backendProjects.forEach { backendProject ->
            dependencies.add("kover", backendProject)
        }
        val unitOnly = providers.gradleProperty("workbench.koverUnitOnly").isPresent
        extensions.configure<KoverProjectExtension> {
            reports {
                total {
                    if (unitOnly) {
                        xml {
                            xmlFile.set(layout.buildDirectory.file("reports/kover/unit/report.xml"))
                        }
                    }
                    html {
                        onCheck.set(!unitOnly)
                    }
                    xml {
                        onCheck.set(!unitOnly)
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
    }

    private fun Project.registerRootVerificationTasks(backendProjects: List<Project>) {
        val backendQuickTasks = backendProjects.map { "${it.path}:quickCheck" }
        val backendFuzzTasks = backendProjects.map { "${it.path}:fuzzTest" }
        val pitestProperties = PitestProperties(rootProject)
        val pitestTaskPaths = pitestEnabledProjects(backendProjects, pitestProperties).map { "${it.path}:pitest" }
        val testArchitectureCheck =
            tasks.register("testArchitectureCheck", TestArchitectureCheckTask::class.java) {
                rootDirectory.set(layout.projectDirectory)
                (backendProjects + project(":workbench-test-support")).forEach { module ->
                    listOf("src/test/kotlin", "src/integrationTest/kotlin").forEach { sourceRoot ->
                        testSources.from(
                            module.fileTree(sourceRoot) {
                                include("**/*.kt")
                            }
                        )
                    }
                }
            }
        val moduleArchitectureCheck =
            tasks.register("moduleArchitectureCheck", ModuleArchitectureCheckTask::class.java) {
                rootDirectory.set(layout.projectDirectory)
                backendProjects.forEach { module ->
                    architectureInputs.from(module.layout.projectDirectory.file("build.gradle.kts"))
                    architectureInputs.from(
                        module.fileTree("src/main/kotlin") { include("**/*.kt") }
                    )
                }
            }
        val apiControllerVersionCheck =
            tasks.register("apiControllerVersionCheck", ApiControllerVersionCheckTask::class.java) {
                rootDirectory.set(layout.projectDirectory)
                controllerSources.from(
                    project(":workbench-web").fileTree("src/main/kotlin") {
                        include("**/*.kt")
                    }
                )
            }
        val detektSuppressionCheck =
            tasks.register("detektSuppressionCheck", DetektSuppressionCheckTask::class.java) {
                rootDirectory.set(layout.projectDirectory)
                (backendProjects + project(":workbench-test-support")).forEach { module ->
                    listOf("src/main/kotlin", "src/test/kotlin", "src/integrationTest/kotlin", "src/testFixtures/kotlin")
                        .forEach { sourceRoot ->
                            kotlinSources.from(
                                module.fileTree(sourceRoot) {
                                    include("**/*.kt")
                                }
                            )
                        }
                }
            }
        val agentInfraCheck =
            tasks.register("agentInfraCheck") {
                group = "verification"
                description = "Lints, format-checks, and tests the local-only Infra lease tooling without containers."
                dependsOn(
                    ":workbench-frontend:pythonInfraRuffCheck",
                    ":workbench-frontend:pythonInfraRuffFormatCheck",
                    ":workbench-frontend:pythonInfraTest",
                )
            }
        val ciPythonRuffCheck =
            tasks.register("ciPythonRuffCheck", Exec::class.java) {
                group = "verification"
                description = "Lints the diff coverage Python tooling with Ruff."
                workingDir = rootProject.projectDir
                commandLine("uv", "run", "--directory", "scripts/ci", "ruff", "check", ".")
            }
        val ciPythonRuffFormatCheck =
            tasks.register("ciPythonRuffFormatCheck", Exec::class.java) {
                group = "verification"
                description = "Checks the diff coverage Python tooling format with Ruff."
                workingDir = rootProject.projectDir
                commandLine("uv", "run", "--directory", "scripts/ci", "ruff", "format", "--check", ".")
            }
        val pythonToolingCheck =
            tasks.register("pythonToolingCheck") {
                group = "verification"
                description = "Validates all uv-managed Python tooling."
                dependsOn(agentInfraCheck, ciPythonRuffCheck, ciPythonRuffFormatCheck)
            }

        tasks.register("agentInfraSmokeTest") {
            group = "verification"
            description = "Starts and destroys an isolated compact Infra lease using local Docker."
            dependsOn(":workbench-frontend:pythonInfraSmokeTest")
        }

        tasks.register("quickCheck") {
            group = "verification"
            description = "Fast local verification: Spotless, Detekt, and unit tests."
            dependsOn(testArchitectureCheck)
            dependsOn(moduleArchitectureCheck)
            dependsOn(apiControllerVersionCheck)
            dependsOn(detektSuppressionCheck)
            dependsOn(pythonToolingCheck)
            dependsOn(backendQuickTasks)
            dependsOn(":workbench-test-support:quickCheck")
            dependsOn(":workbench-frontend:quickCheck")
        }

        tasks.named("check") {
            dependsOn(testArchitectureCheck)
            dependsOn(moduleArchitectureCheck)
            dependsOn(apiControllerVersionCheck)
            dependsOn(detektSuppressionCheck)
            dependsOn(pythonToolingCheck)
            dependsOn(subprojects.map { subproject -> subproject.tasks.named("check") })
            dependsOn(tasks.named("koverHtmlReport"), tasks.named("koverXmlReport"))
        }

        tasks.register("koverUnitXmlReport", Exec::class.java) {
            group = "coverage"
            description = "Generates unit-only backend Kover reports."
            workingDir = rootProject.projectDir
            commandLine(
                rootProject.layout.projectDirectory.file("gradlew").asFile.absolutePath,
                *backendProjects
                    .flatMap { module ->
                        listOf("${module.path}:test", "${module.path}:koverXmlReport")
                    }
                    .toTypedArray(),
                "koverXmlReport",
                "-Pworkbench.koverUnitOnly=true",
                "--no-daemon",
                "--no-configuration-cache",
            )
        }

        tasks.register("fuzzTest") {
            group = "verification"
            description = "Runs property-based (fuzz) tests."
            dependsOn(backendFuzzTasks)
        }

        tasks.named("pitestReportAggregate") {
            mustRunAfter(pitestTaskPaths)
        }

        tasks.register("mutationTest") {
            group = "verification"
            description = "Runs PIT mutation testing across backend modules."
            dependsOn(pitestTaskPaths)
            dependsOn("pitestReportAggregate")
        }

        tasks.register("extendedCheck") {
            group = "verification"
            description = "Extended verification: standard check plus fuzz and mutation tests."
            dependsOn("check", "fuzzTest", "mutationTest")
        }

        tasks.register("e2eCheck") {
            group = "verification"
            description = "Full-stack frontend E2E via isolated ephemeral Infra and Playwright."
            dependsOn(":workbench-frontend:e2eCheck")
        }

        tasks.register("frontendUnitCoverage") {
            group = "coverage"
            description = "Generates unit-only frontend Vitest coverage."
            dependsOn(":workbench-frontend:pnpmCoverageUnit")
        }

        tasks.register("frontendFullCoverage") {
            group = "coverage"
            description = "Generates full frontend Vitest coverage (unit + storybook)."
            dependsOn(":workbench-frontend:pnpmCoverageFull")
        }

        tasks.register("frontendStorybookComponentCoverage") {
            group = "coverage"
            description = "Runs Storybook and enforces production Svelte component mount coverage."
            dependsOn(":workbench-frontend:pnpmStorybookTest")
        }

        tasks.register("frontendE2eCoverage") {
            group = "coverage"
            description = "Generates E2E frontend Playwright coverage."
            dependsOn(":workbench-frontend:pnpmCoverageE2e")
        }

        tasks.register("dev") {
            group = "application"
            description =
                "Prints local development commands for the Spring Boot API, worker, and SvelteKit dev server."
            doLast {
                println("Backend:  ./gradlew :workbench-web:bootRun --args='--spring.profiles.active=local'")
                println("Worker:   ./gradlew :workbench-worker:bootRun --args='--spring.profiles.active=local,worker'")
                println("Frontend: ./gradlew :workbench-frontend:pnpmDev")
            }
        }
    }

    private fun pitestEnabledProjects(
        backendProjects: List<Project>,
        pitestProperties: PitestProperties,
    ): List<Project> =
        backendProjects.filter { project ->
            val hasKotlinTests =
                project.fileTree("src/test/kotlin") {
                    include("**/*.kt")
                }.files.isNotEmpty()
            project.name !in pitestProperties.csv("skipModules") &&
                (!pitestProperties.string("autoSkipModulesWithoutTests").toBoolean() || hasKotlinTests)
        }
}
