package workbench.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test

class TestingConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("java") {
            project.configureWorkbenchTests()
        }
    }

    private fun Project.configureWorkbenchTests() {
        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }

        val testTaskProvider = tasks.named("test", Test::class.java)
        val workbenchUnitTest =
            tasks.register("workbenchUnitTest", Test::class.java) {
                group = "verification"
                description = "Runs unit tests (excludes integration and fuzz tags)."
                testClassesDirs = testTaskProvider.get().testClassesDirs
                classpath = testTaskProvider.get().classpath
                failOnNoDiscoveredTests.set(false)
                useJUnitPlatform {
                    excludeTags("fuzz", "integration")
                }
                systemProperty("kotest.tags", "!integration & !fuzz")
            }

        tasks.register("workbenchIntegrationTest", Test::class.java) {
            group = "verification"
            description = "Runs integration-tagged tests."
            testClassesDirs = testTaskProvider.get().testClassesDirs
            classpath = testTaskProvider.get().classpath
            failOnNoDiscoveredTests.set(false)
            mustRunAfter(workbenchUnitTest)
            useJUnitPlatform {
                includeTags("integration")
            }
            systemProperty("kotest.tags", "integration")
        }

        afterEvaluate {
            registerFuzzVerification()
            registerWorkbenchQuickCheck(workbenchUnitTest.get())
            registerWorkbenchNightlyModule()
        }
    }

    private fun Project.registerFuzzVerification() {
        val hasFuzzTests =
            fileTree("src/test/kotlin") {
                include("**/*.kt")
            }.any {
                val source = it.readText()
                source.contains("@Tags(\"fuzz\")") || source.contains("@Tag(\"fuzz\")")
            }

        if (hasFuzzTests) {
            val testTask = tasks.named("test", Test::class.java)
            tasks.register("workbenchFuzzVerification", Test::class.java) {
                group = "verification"
                description = "Runs property-based (fuzz) tests in this module."
                testClassesDirs = testTask.get().testClassesDirs
                classpath = testTask.get().classpath
                failOnNoDiscoveredTests.set(false)
                useJUnitPlatform {
                    includeTags("fuzz")
                }
                systemProperty("kotest.tags", "fuzz")
            }
        } else {
            tasks.register("workbenchFuzzVerification") {
                group = "verification"
                description = "No fuzz tests configured for this module."
            }
        }
    }

    private fun Project.registerWorkbenchQuickCheck(unitTest: Task) {
        tasks.register("workbenchQuickCheck") {
            group = "verification"
            description = "Fast Workbench checks for this module."
            listOf("spotlessCheck", "detektMain", "detektTest")
                .mapNotNull { taskName -> tasks.findByName(taskName) }
                .forEach { task -> dependsOn(task) }
            dependsOn(unitTest)
        }
    }

    private fun Project.registerWorkbenchNightlyModule() {
        tasks.register("workbenchCiNightlyModule") {
            group = "verification"
            description = "Nightly per-module verification: check, fuzz, and configured mutation tests."
            dependsOn("check", "workbenchFuzzVerification")
            tasks.findByName("koverXmlReport")?.let { koverXml -> dependsOn(koverXml) }
        }
    }
}
