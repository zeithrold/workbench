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
        val backendQuickTasks = backendProjects.map { "${it.path}:workbenchQuickCheck" }
        val backendFuzzTasks = backendProjects.map { "${it.path}:workbenchFuzzVerification" }
        val pitestProperties = PitestProperties(rootProject)
        val pitestTaskPaths = pitestEnabledProjects(backendProjects, pitestProperties).map { "${it.path}:pitest" }
        val testArchitectureCheck =
            tasks.register("workbenchTestArchitectureCheck", TestArchitectureCheckTask::class.java) {
                rootDirectory.set(layout.projectDirectory)
                (backendProjects + project(":workbench-test-support")).forEach { module ->
                    testSources.from(
                        module.fileTree("src/test/kotlin") {
                            include("**/*.kt")
                        }
                    )
                }
            }

        tasks.register("workbenchQuickCheck") {
            group = "verification"
            description = "Fast local verification: Spotless, Detekt, and unit tests."
            dependsOn(testArchitectureCheck)
            dependsOn(backendQuickTasks)
            dependsOn(":workbench-test-support:workbenchQuickCheck")
            dependsOn(":workbench-frontend:workbenchQuickCheck")
        }

        tasks.named("check") {
            dependsOn(testArchitectureCheck)
            dependsOn(subprojects.map { subproject -> subproject.tasks.named("check") })
            dependsOn(tasks.named("koverHtmlReport"), tasks.named("koverXmlReport"))
        }

        tasks.register("workbenchCiCheck") {
            group = "verification"
            description = "CI verification: static analysis, tests, and full coverage gates."
            dependsOn(tasks.named("check"))
        }

        tasks.register("workbenchCiFullCoverage") {
            group = "verification"
            description = "Generates full backend Kover reports."
            dependsOn(tasks.named("koverHtmlReport"), tasks.named("koverXmlReport"))
        }

        tasks.register("workbenchCiUnitCoverage", Exec::class.java) {
            group = "verification"
            description = "Generates unit-only backend Kover reports."
            workingDir = rootProject.projectDir
            commandLine(
                rootProject.layout.projectDirectory.file("gradlew").asFile.absolutePath,
                *backendProjects
                    .flatMap { module ->
                        listOf("${module.path}:workbenchUnitTest", "${module.path}:koverXmlReport")
                    }
                    .toTypedArray(),
                "koverXmlReport",
                "-Pworkbench.koverUnitOnly=true",
                "--no-daemon",
                "--no-configuration-cache",
            )
        }

        tasks.register("workbenchFuzzTest") {
            group = "verification"
            description = "Runs property-based (fuzz) tests."
            dependsOn(backendFuzzTasks)
        }

        tasks.named("pitestReportAggregate") {
            mustRunAfter(pitestTaskPaths)
        }

        tasks.register("workbenchMutationTest") {
            group = "verification"
            description = "Runs PIT mutation testing across backend modules."
            dependsOn(pitestTaskPaths)
            dependsOn("pitestReportAggregate")
        }

        tasks.register("workbenchExtendedCheck") {
            group = "verification"
            description = "Extended verification: CI check plus fuzz and mutation tests."
            dependsOn("workbenchCiCheck", "workbenchFuzzTest", "workbenchMutationTest")
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
