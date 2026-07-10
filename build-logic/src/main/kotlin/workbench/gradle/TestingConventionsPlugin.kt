package workbench.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.testing.base.TestingExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

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
        testTaskProvider.configure {
            useJUnitPlatform {
                excludeTags("fuzz")
            }
            systemProperty("kotest.tags", "!fuzz")
        }

        pluginManager.apply("jvm-test-suite")
        extensions.configure<TestingExtension> {
            suites.register("integrationTest", JvmTestSuite::class.java) {
                dependencies {
                    implementation.add(project.dependencies.create(project))
                }
                targets.configureEach {
                    testTask.configure {
                        failOnNoDiscoveredTests.set(false)
                        maxParallelForks = 1
                        mustRunAfter(testTaskProvider)
                    }
                }
            }
        }
        configurations.named("integrationTestImplementation") {
            extendsFrom(configurations.getByName("testImplementation"))
        }
        configurations.named("integrationTestRuntimeOnly") {
            extendsFrom(configurations.getByName("testRuntimeOnly"))
        }
        val integrationTest = tasks.named("integrationTest", Test::class.java)
        tasks.named("check") {
            dependsOn(integrationTest)
        }

        afterEvaluate {
            if (pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")) {
                extensions.configure<KotlinJvmProjectExtension> {
                    target.compilations.named("integrationTest") {
                        associateWith(target.compilations.getByName("main"))
                    }
                }
            }
            registerFuzzTest()
            registerQuickCheck(testTaskProvider)
            registerCiNightlyCheck()
        }
    }

    private fun Project.registerFuzzTest() {
        val hasFuzzTests =
            fileTree("src/test/kotlin") {
                include("**/*.kt")
            }.any {
                val source = it.readText()
                source.contains("@Tags(\"fuzz\")") || source.contains("@Tag(\"fuzz\")")
            }

        if (hasFuzzTests) {
            val testTask = tasks.named("test", Test::class.java)
            tasks.register("fuzzTest", Test::class.java) {
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
            tasks.register("fuzzTest") {
                group = "verification"
                description = "No fuzz tests configured for this module."
            }
        }
    }

    private fun Project.registerQuickCheck(unitTest: TaskProvider<Test>) {
        tasks.register("quickCheck") {
            group = "verification"
            description = "Fast Workbench checks for this module."
            listOf("spotlessCheck", "detektMain", "detektTest")
                .mapNotNull { taskName -> tasks.findByName(taskName) }
                .forEach { task -> dependsOn(task) }
            dependsOn(unitTest)
        }
    }

    private fun Project.registerCiNightlyCheck() {
        tasks.register("ciNightlyCheck") {
            group = "ci"
            description = "Nightly per-module verification: check, fuzz, and configured mutation tests."
            dependsOn("check", "fuzzTest")
            tasks.findByName("koverXmlReport")?.let { koverXml -> dependsOn(koverXml) }
        }
    }
}
