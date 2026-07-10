package workbench.gradle

import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import info.solidsoft.gradle.pitest.PitestPluginExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class BackendConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            pluginManager.apply("com.diffplug.spotless")
            pluginManager.apply("dev.detekt")
            pluginManager.apply("org.jetbrains.kotlinx.kover")
            pluginManager.apply("info.solidsoft.pitest")
            pluginManager.apply("workbench.testing-conventions")

            configureKotlin()
            configureSpotless()
            configureDetekt()
            configureKover()
            configureSharedDependencies()
            configurePitest()
        }
    }

    private fun Project.configureKotlin() {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(25)
            compilerOptions {
                freeCompilerArgs.addAll("-Xjsr305=strict")
            }
        }
    }

    private fun Project.configureSpotless() {
        extensions.configure<SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                ktfmt("0.63").googleStyle()
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktfmt("0.63").googleStyle()
            }
        }
    }

    private fun Project.configureDetekt() {
        extensions.configure<DetektExtension> {
            toolVersion.set(libs().findVersion("detekt").get().requiredVersion)
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            buildUponDefaultConfig.set(true)
            parallel.set(true)
        }
        tasks.withType(Detekt::class.java).configureEach {
            if (
                name == "detektTest" ||
                    name == "detektTestSourceSet" ||
                    name == "detektTestFixtures" ||
                    name == "detektTestFixturesSourceSet"
            ) {
                config.setFrom(
                    rootProject.files("config/detekt/detekt.yml", "config/detekt/detekt-test.yml"),
                )
                buildUponDefaultConfig.set(true)
            }
        }
    }

    private fun Project.configureKover() {
        val unitOnly = providers.gradleProperty("workbench.koverUnitOnly").isPresent

        extensions.configure<KoverProjectExtension> {
            currentProject {
                instrumentation {
                    disabledForTestTasks.add("fuzzTest")
                    if (unitOnly) {
                        disabledForTestTasks.add("integrationTest")
                    }
                }
            }
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
                    rule("line coverage") {
                        minBound(moduleLineCoverageFloor(name))
                    }
                }
            }
        }
    }

    private fun Project.configureSharedDependencies() {
        val libs = libs()
        dependencies.add("implementation", dependencies.platform(springBootBomNotation()))
        dependencies.add("testImplementation", dependencies.platform(springBootBomNotation()))
        dependencies.add("annotationProcessor", dependencies.platform(springBootBomNotation()))
        dependencies.add("implementation", libs.findLibrary("kotlin-reflect").get())
        dependencies.add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
        dependencies.add("implementation", libs.findLibrary("kotlinx-serialization-json").get())
        dependencies.add("testImplementation", libs.findLibrary("kotest-runner").get())
        dependencies.add("testImplementation", libs.findLibrary("kotest-assertions").get())
        dependencies.add("testImplementation", libs.findLibrary("kotest-property").get())
        dependencies.add("testImplementation", libs.findLibrary("mockk").get())
        dependencies.add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
    }

    private fun Project.configurePitest() {
        val pitestProperties = PitestProperties(rootProject)
        afterEvaluate {
            val skipModules = pitestProperties.csv("skipModules")
            val autoSkipModulesWithoutTests =
                pitestProperties.string("autoSkipModulesWithoutTests").toBoolean()
            val hasKotlinTests =
                fileTree("src/test/kotlin") {
                    include("**/*.kt")
                }.files.isNotEmpty()
            val skipPitest = name in skipModules || (autoSkipModulesWithoutTests && !hasKotlinTests)

            if (skipPitest) return@afterEvaluate

            val moduleSuffix = name.removePrefix("workbench-")
            val packageGlob = "ink.doa.workbench.$moduleSuffix.*"
            extensions.configure<PitestPluginExtension> {
                junit5PluginVersion.set(pitestProperties.string("junit5PluginVersion"))
                targetClasses.set(setOf(packageGlob))
                targetTests.set(setOf(packageGlob))
                mutationThreshold.set(pitestProperties.string("mutationThreshold").toInt())
                avoidCallsTo.set(pitestProperties.csv("avoidCallsTo"))
                excludedClasses.set(pitestProperties.csv("excludedClasses"))
                excludedTestClasses.set(pitestProperties.csv("excludedTestClasses"))
                excludedGroups.set(pitestProperties.csv("excludedGroups"))
                outputFormats.set(pitestProperties.csv("perModuleOutputFormats"))
                timestampedReports.set(pitestProperties.string("timestampedReports").toBoolean())
                exportLineCoverage.set(pitestProperties.string("exportLineCoverage").toBoolean())
                threads.set(Runtime.getRuntime().availableProcessors())
                if (name == "workbench-core") {
                    reportAggregator {
                        mutationThreshold.set(pitestProperties.string("mutationThreshold").toInt())
                    }
                }
            }
            tasks.named("ciNightlyCheck") {
                dependsOn("pitest")
            }
        }
    }
}
