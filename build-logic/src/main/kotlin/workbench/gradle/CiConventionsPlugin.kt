package workbench.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import workbench.gradle.ci.MergeKoverReportsTask
import workbench.gradle.ci.MergePitReportsTask
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
        }
    }
}
