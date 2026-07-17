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
        val coreReport = root.resolve("workbench-kernel/build/reports/kover/report.xml")
        val serviceReport = root.resolve("workbench-application/build/reports/kover/report.xml")
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
        val full = root.resolve("workbench-kernel/build/reports/kover/report.xml")
        val unit = root.resolve("workbench-kernel/build/reports/kover/unit/report.xml")
        full.parent.createDirectories()
        unit.parent.createDirectories()
        full.writeText(koverReport(lineMissed = 0, lineCovered = 100))
        unit.writeText(koverReport(lineMissed = 40, lineCovered = 60))

        val deltas = QualityReports.moduleCoverageDeltas(root.toFile(), listOf("workbench-kernel"))

        assertEquals(40.0, deltas.single().lineDelta)
        assertTrue(deltas.single().warnings.size == 2)
    }

    @Test
    fun `merges pit mutations and preserves partial marker`() {
        val root = Files.createTempDirectory("pit-merge")
        val core = root.resolve("workbench-kernel/build/reports/pitest/mutations.xml")
        val service = root.resolve("workbench-application/build/reports/pitest/mutations.xml")
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
    fun `parses frontend lcov line coverage`() {
        val root = Files.createTempDirectory("lcov")
        val lcov = root.resolve("workbench-frontend/coverage/full/lcov.info")
        lcov.parent.createDirectories()
        lcov.writeText(
            """
            TN:
            SF:src/example.ts
            LF:10
            LH:7
            end_of_record
            """.trimIndent(),
        )

        val delta = QualityReports.frontendCoverageDelta(root.toFile())

        assertEquals(70.0, delta.full?.line)
    }

    @Test
    fun `parses and renders Storybook component mount coverage`() {
        val root = Files.createTempDirectory("storybook-component-coverage")
        val report =
            root.resolve("workbench-frontend/coverage/storybook-components/component-coverage.json")
        report.parent.createDirectories()
        report.writeText(
            """
            {
              "version": 1,
              "total": 10,
              "mounted": 8,
              "percentage": 80.0,
              "targetPercentage": 100,
              "layers": {
                "components": { "total": 6, "mounted": 5, "percentage": 83.3333 },
                "features": { "total": 4, "mounted": 3, "percentage": 75.0 }
              },
              "uncoveredComponents": []
            }
            """.trimIndent(),
        )

        val coverage = QualityReports.parseStorybookComponentCoverage(report.toFile())
        val (markdown, json) = QualityReports.renderQualitySummary(root.toFile(), extendedTests = false)

        assertEquals(80.0, coverage?.percentage)
        assertEquals(5, coverage?.layers?.get("components")?.mounted)
        assertContains(markdown, "Storybook Component Mount Coverage")
        assertContains(markdown, "8/10 components mounted")
        assertContains(json, "\"storybookComponents\": {")
        assertContains(json, "\"percentage\": 80.0000")
    }

    @Test
    fun `renders coverage summary markdown and json`() {
        val root = Files.createTempDirectory("summary")
        val full = root.resolve("workbench-kernel/build/reports/kover/report.xml")
        val unit = root.resolve("workbench-kernel/build/reports/kover/unit/report.xml")
        full.parent.createDirectories()
        unit.parent.createDirectories()
        full.writeText(koverReport(lineMissed = 0, lineCovered = 100))
        unit.writeText(koverReport(lineMissed = 40, lineCovered = 60))

        val (markdown, json) = QualityReports.renderQualitySummary(root.toFile(), extendedTests = false)

        assertContains(markdown, "#### Unit coverage and delta")
        assertContains(markdown, "full-unit line delta")
        assertContains(json, "\"module\": \"workbench-kernel\"")
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
