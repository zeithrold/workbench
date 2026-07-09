package workbench.gradle.ci

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityReportsTest {
    @Test
    fun `merges root Kover counters from module reports`() {
        val root = Files.createTempDirectory("kover-merge")
        val coreReport = root.resolve("workbench-core/build/reports/kover/report.xml")
        val serviceReport = root.resolve("workbench-service/build/reports/kover/report.xml")
        coreReport.parent.createDirectories()
        serviceReport.parent.createDirectories()
        coreReport.writeText(koverReport(lineMissed = 1, lineCovered = 9))
        serviceReport.writeText(koverReport(lineMissed = 2, lineCovered = 8))

        val output = root.resolve("build/reports/kover/report.xml").toFile()
        QualityReports.mergeKoverReports(QualityReports.discoverModuleKoverReports(root.toFile()), output)

        val metrics = QualityReports.parseKoverReport(output)
        assertEquals(85.0, metrics?.line)
    }

    @Test
    fun `computes unit full coverage warnings`() {
        val root = Files.createTempDirectory("coverage-delta")
        val full = root.resolve("workbench-core/build/reports/kover/report.xml")
        val unit = root.resolve("workbench-core/build/reports/kover/unit/report.xml")
        full.parent.createDirectories()
        unit.parent.createDirectories()
        full.writeText(koverReport(lineMissed = 0, lineCovered = 100))
        unit.writeText(koverReport(lineMissed = 40, lineCovered = 60))

        val deltas = QualityReports.moduleCoverageDeltas(root.toFile(), listOf("workbench-core"))

        assertEquals(40.0, deltas.single().lineDelta)
        assertTrue(deltas.single().warnings.size == 2)
    }

    @Test
    fun `merges pit mutations and preserves partial marker`() {
        val root = Files.createTempDirectory("pit-merge")
        val core = root.resolve("workbench-core/build/reports/pitest/mutations.xml")
        val service = root.resolve("workbench-service/build/reports/pitest/mutations.xml")
        core.parent.createDirectories()
        service.parent.createDirectories()
        core.writeText(
            """
            <mutations partial="true">
              <mutation status="KILLED"><sourceFile>A.kt</sourceFile><lineNumber>1</lineNumber></mutation>
            </mutations>
            """.trimIndent(),
        )
        service.writeText(
            """
            <mutations>
              <mutation status="SURVIVED"><sourceFile>B.kt</sourceFile><lineNumber>2</lineNumber></mutation>
            </mutations>
            """.trimIndent(),
        )

        val output = root.resolve("build/reports/pitest/mutations.xml").toFile()
        val count = QualityReports.mergePitReports(QualityReports.discoverModulePitReports(root.toFile()), output)

        assertEquals(2, count)
        val text = output.readText()
        assertContains(text, "partial=\"true\"")
        assertContains(text, "status=\"KILLED\"")
        assertContains(text, "status=\"SURVIVED\"")
    }

    @Test
    fun `renders coverage summary markdown and json`() {
        val root = Files.createTempDirectory("summary")
        val full = root.resolve("workbench-core/build/reports/kover/report.xml")
        val unit = root.resolve("workbench-core/build/reports/kover/unit/report.xml")
        full.parent.createDirectories()
        unit.parent.createDirectories()
        full.writeText(koverReport(lineMissed = 0, lineCovered = 100))
        unit.writeText(koverReport(lineMissed = 40, lineCovered = 60))

        val (markdown, json) = QualityReports.renderQualitySummary(root.toFile(), extendedTests = false)

        assertContains(markdown, "#### Unit coverage and delta")
        assertContains(markdown, "full-unit line delta")
        assertContains(json, "\"module\": \"workbench-core\"")
        assertContains(json, "\"lineDelta\": 40.0000")
    }

    private fun koverReport(lineMissed: Int, lineCovered: Int): String =
        """
        <report name="test">
          <counter type="INSTRUCTION" missed="$lineMissed" covered="$lineCovered"/>
          <counter type="BRANCH" missed="$lineMissed" covered="$lineCovered"/>
          <counter type="LINE" missed="$lineMissed" covered="$lineCovered"/>
          <counter type="METHOD" missed="0" covered="1"/>
          <counter type="CLASS" missed="0" covered="1"/>
        </report>
        """.trimIndent()
}
