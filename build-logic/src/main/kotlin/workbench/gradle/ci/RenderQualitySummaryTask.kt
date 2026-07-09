package workbench.gradle.ci

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class RenderQualitySummaryTask : DefaultTask() {
    @get:InputDirectory
    abstract val repoRoot: DirectoryProperty

    @get:OutputFile
    abstract val summaryJson: RegularFileProperty

    @get:Input
    abstract val extendedTests: Property<Boolean>

    @TaskAction
    fun render() {
        val (markdown, json) =
            QualityReports.renderQualitySummary(
                repoRoot.asFile.get(),
                extendedTests.getOrElse(false),
            )
        summaryJson.asFile.get().apply {
            parentFile.mkdirs()
            writeText(json, Charsets.UTF_8)
        }
        print(markdown)
    }
}
