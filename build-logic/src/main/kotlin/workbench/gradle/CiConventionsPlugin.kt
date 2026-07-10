package workbench.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import workbench.gradle.ci.KoverE2eReportTask
import workbench.gradle.ci.MergeKoverReportsTask
import workbench.gradle.ci.MergePitReportsTask
import workbench.gradle.ci.PrepareKoverE2eAgentTask
import workbench.gradle.ci.RenderQualitySummaryTask

class CiConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            tasks.register("workbenchCiMergeKoverReports", MergeKoverReportsTask::class.java) {
                group = "verification"
                description = "Merges per-module Kover XML reports into the root report path."
                repoRoot.set(layout.projectDirectory)
                outputFile.set(layout.buildDirectory.file("reports/kover/report.xml"))
            }

            tasks.register("workbenchCiMergePitReports", MergePitReportsTask::class.java) {
                group = "verification"
                description = "Merges per-module PIT mutation XML reports into the root report path."
                repoRoot.set(layout.projectDirectory)
                outputFile.set(layout.buildDirectory.file("reports/pitest/mutations.xml"))
            }

            tasks.register("workbenchCiRenderQualitySummary", RenderQualitySummaryTask::class.java) {
                group = "verification"
                description = "Renders Workbench coverage and mutation quality summary markdown."
                repoRoot.set(layout.projectDirectory)
                summaryJson.set(layout.buildDirectory.file("reports/workbench-ci/coverage-summary.json"))
                extendedTests.set(
                    providers.environmentVariable("EXTENDED_TESTS").map { value ->
                        value.equals("true", ignoreCase = true)
                    }.orElse(false),
                )
            }

            registerKoverE2eTasks()
        }
    }

    private fun Project.registerKoverE2eTasks() {
        val koverJvmAgent =
            configurations.findByName("koverJvmAgent")
                ?: configurations.create("koverJvmAgent") {
                    isCanBeConsumed = false
                    isCanBeResolved = true
                }
        val koverCli =
            configurations.findByName("koverCli")
                ?: configurations.create("koverCli") {
                    isCanBeConsumed = false
                    isCanBeResolved = true
                }
        val libs = libs()
        if (koverJvmAgent.dependencies.isEmpty()) {
            dependencies.add(koverJvmAgent.name, libs.findLibrary("kover-jvm-agent").get())
        }
        if (koverCli.dependencies.isEmpty()) {
            dependencies.add(koverCli.name, libs.findLibrary("kover-cli").get())
        }

        val prepareKoverE2eAgent: TaskProvider<PrepareKoverE2eAgentTask> =
            tasks.register("workbenchPrepareKoverE2eAgent", PrepareKoverE2eAgentTask::class.java) {
                group = "verification"
                description = "Resolves the Kover JVM agent and writes E2E runtime args files."
                agentJar.from(koverJvmAgent)
                outputDir.set(layout.buildDirectory.dir("kover-e2e"))
            }

        tasks.register("workbenchKoverE2eReport", KoverE2eReportTask::class.java) {
            group = "verification"
            description = "Generates backend Kover XML from E2E runtime .ic reports."
            dependsOn(prepareKoverE2eAgent)
            dependsOn(":workbench-web:bootJar", ":workbench-worker:bootJar")
            cliJar.from(koverCli)
            repoRoot.set(layout.projectDirectory)
            e2eReportsDir.set(layout.buildDirectory.dir("kover-e2e"))
            outputXml.set(layout.buildDirectory.file("reports/kover/e2e/report.xml"))
        }
    }
}
