package workbench.gradle.ci

import java.io.File
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.w3c.dom.Node

data class CoverageMetrics(
    val line: Double? = null,
    val branch: Double? = null,
    val instruction: Double? = null,
)

data class MutationMetrics(
    val mutationScore: Double? = null,
    val testStrength: Double? = null,
    val pitLine: Double? = null,
    val killed: Int = 0,
    val survived: Int = 0,
    val noCoverage: Int = 0,
    val total: Int = 0,
)

data class BackendCoverageDelta(
    val full: CoverageMetrics?,
    val unit: CoverageMetrics?,
    val e2e: CoverageMetrics?,
) {
    val fullMinusUnit: Double? =
        if (full?.line != null && unit?.line != null) full.line - unit.line else null

    val e2eMinusFull: Double? =
        if (e2e?.line != null && full?.line != null) e2e.line - full.line else null
}

data class FrontendCoverageDelta(
    val unit: CoverageMetrics?,
    val full: CoverageMetrics?,
    val e2e: CoverageMetrics?,
) {
    val fullMinusUnit: Double? =
        if (full?.line != null && unit?.line != null) full.line - unit.line else null

    val e2eMinusFull: Double? =
        if (e2e?.line != null && full?.line != null) e2e.line - full.line else null

    val warnings: List<String> =
        buildList {
            if (unit?.line != null && unit.line < UNIT_LINE_WARNING_THRESHOLD) {
                add("unit line ${unit.line.formatPct()} < ${UNIT_LINE_WARNING_THRESHOLD.formatPct()}")
            }
            if (fullMinusUnit != null && fullMinusUnit > INTEGRATION_LIFT_WARNING_THRESHOLD) {
                add(
                    "full-unit line delta ${fullMinusUnit.formatPct()} > " +
                        "${INTEGRATION_LIFT_WARNING_THRESHOLD.formatPct()}",
                )
            }
        }
}

data class ModuleCoverageDelta(
    val module: String,
    val full: CoverageMetrics?,
    val unit: CoverageMetrics?,
) {
    val lineDelta: Double? =
        if (full?.line != null && unit?.line != null) full.line - unit.line else null

    val warnings: List<String> =
        buildList {
            if (unit?.line != null && unit.line < UNIT_LINE_WARNING_THRESHOLD) {
                add("unit line ${unit.line.formatPct()} < ${UNIT_LINE_WARNING_THRESHOLD.formatPct()}")
            }
            if (lineDelta != null && lineDelta > INTEGRATION_LIFT_WARNING_THRESHOLD) {
                add(
                    "full-unit line delta ${lineDelta.formatPct()} > " +
                        "${INTEGRATION_LIFT_WARNING_THRESHOLD.formatPct()}",
                )
            }
        }
}

object QualityReports {
    private val counterTypes = listOf("INSTRUCTION", "BRANCH", "LINE", "METHOD", "CLASS")

    fun discoverModuleKoverReports(repoRoot: File): List<File> =
        discoverWorkbenchModules(repoRoot).mapNotNull { module ->
            module.resolve("build/reports/kover/report.xml").takeIf(File::isFile)
        }

    fun discoverModulePitReports(repoRoot: File): List<File> =
        discoverWorkbenchModules(repoRoot).mapNotNull { module ->
            module.resolve("build/reports/pitest/mutations.xml").takeIf(File::isFile)
        }

    fun mergeKoverReports(reports: List<File>, output: File) {
        require(reports.isNotEmpty()) {
            "No module Kover reports found under workbench-*/build/reports/kover/"
        }
        val counters = mergeKoverCounters(reports)
        require(counters.isNotEmpty()) {
            "Module Kover reports contained no aggregate counters."
        }

        val document = newDocument()
        val report = document.createElement("report")
        report.setAttribute("name", "Kover aggregated report (Workbench CI module merge)")
        document.appendChild(report)
        counterTypes.forEach { type ->
            counters[type]?.let { counter ->
                report.appendChild(
                    document.createElement("counter").apply {
                        setAttribute("type", type)
                        setAttribute("missed", counter.missed.toString())
                        setAttribute("covered", counter.covered.toString())
                    },
                )
            }
        }
        output.parentFile.mkdirs()
        writeXml(document.documentElement, output)
    }

    fun mergePitReports(reports: List<File>, output: File): Int {
        require(reports.isNotEmpty()) {
            "No module PIT reports found under workbench-*/build/reports/pitest/mutations.xml"
        }
        val document = newDocument()
        val merged = document.createElement("mutations")
        document.appendChild(merged)

        var partial = false
        reports.forEach { report ->
            val root = parseXml(report)
            require(root.tagName == "mutations") {
                "Expected <mutations> root in $report, found <${root.tagName}>"
            }
            partial = partial || root.getAttribute("partial").equals("true", ignoreCase = true)
            root.childElements("mutation").forEach { mutation ->
                merged.appendChild(document.importNode(mutation, true))
            }
        }
        if (partial) {
            merged.setAttribute("partial", "true")
        }

        val mutationCount = merged.childElements("mutation").size
        require(mutationCount > 0) { "Module PIT reports contained no mutations." }
        output.parentFile.mkdirs()
        writeXml(merged, output)
        return mutationCount
    }

    fun parseKoverReport(path: File): CoverageMetrics? {
        if (!path.isFile) return null
        val counters = mutableMapOf<String, Counter>()
        parseXml(path).descendantElements("counter").forEach { counter ->
            val type = counter.getAttribute("type").takeIf(String::isNotBlank) ?: return@forEach
            counters.merge(
                type,
                Counter(
                    missed = counter.getAttribute("missed").toIntOrNull() ?: 0,
                    covered = counter.getAttribute("covered").toIntOrNull() ?: 0,
                ),
                Counter::plus,
            )
        }

        fun pct(type: String): Double? = counters[type]?.coveredPercentage()
        return CoverageMetrics(
            line = pct("LINE"),
            branch = pct("BRANCH"),
            instruction = pct("INSTRUCTION"),
        ).takeIf { it.hasData() }
    }

    fun discoverBackendModulesWithCoverage(repoRoot: File): List<String> =
        discoverWorkbenchModules(repoRoot)
            .filter { it.resolve("build/reports/kover/report.xml").isFile }
            .map { it.name }

    fun moduleCoverageDeltas(repoRoot: File, modules: List<String>): List<ModuleCoverageDelta> =
        modules.map { module ->
            ModuleCoverageDelta(
                module = module,
                full = parseKoverReport(repoRoot.resolve("$module/build/reports/kover/report.xml")),
                unit = parseKoverReport(repoRoot.resolve("$module/build/reports/kover/unit/report.xml")),
            )
        }

    fun aggregateCoverage(repoRoot: File, modules: List<String>, unit: Boolean = false): CoverageMetrics? {
        val counters = mutableMapOf<String, Counter>()
        modules.forEach { module ->
            val reportPath =
                if (unit) {
                    repoRoot.resolve("$module/build/reports/kover/unit/report.xml")
                } else {
                    repoRoot.resolve("$module/build/reports/kover/report.xml")
                }
            if (!reportPath.isFile) return@forEach
            parseXml(reportPath).childElements("counter").forEach { counter ->
                val type = counter.getAttribute("type").takeIf(String::isNotBlank) ?: return@forEach
                counters.merge(
                    type,
                    Counter(
                        missed = counter.getAttribute("missed").toIntOrNull() ?: 0,
                        covered = counter.getAttribute("covered").toIntOrNull() ?: 0,
                    ),
                    Counter::plus,
                )
            }
        }
        if (counters.isEmpty()) return null
        fun pct(type: String): Double? = counters[type]?.coveredPercentage()
        return CoverageMetrics(
            line = pct("LINE"),
            branch = pct("BRANCH"),
            instruction = pct("INSTRUCTION"),
        ).takeIf { it.hasData() }
    }

    fun parseLcovLineCoverage(path: File): CoverageMetrics? {
        if (!path.isFile) return null
        var totalLines = 0
        var hitLines = 0
        path.forEachLine { line ->
            when {
                line.startsWith("LF:") -> totalLines += line.removePrefix("LF:").toIntOrNull() ?: 0
                line.startsWith("LH:") -> hitLines += line.removePrefix("LH:").toIntOrNull() ?: 0
            }
        }
        if (totalLines == 0) return null
        return CoverageMetrics(line = hitLines.toDouble() / totalLines * 100.0)
    }

    fun backendCoverageDelta(repoRoot: File): BackendCoverageDelta =
        BackendCoverageDelta(
            full = parseKoverReport(repoRoot.resolve("build/reports/kover/report.xml")),
            unit = parseKoverReport(repoRoot.resolve("build/reports/kover/unit/report.xml")),
            e2e = parseKoverReport(repoRoot.resolve("build/reports/kover/e2e/report.xml")),
        )

    fun frontendCoverageDelta(repoRoot: File): FrontendCoverageDelta {
        val frontendRoot = repoRoot.resolve("workbench-frontend")
        return FrontendCoverageDelta(
            unit = parseLcovLineCoverage(frontendRoot.resolve("coverage/unit/lcov.info")),
            full = parseLcovLineCoverage(frontendRoot.resolve("coverage/full/lcov.info")),
            e2e = parseLcovLineCoverage(frontendRoot.resolve("coverage/e2e/lcov.info")),
        )
    }

    fun loadMutationMetrics(path: File): MutationMetrics? {
        val mutations = parsePitMutations(path.resolve("mutations.xml")) ?: return null
        return mutations.copy(pitLine = parsePitLineCoverage(path))
    }

    fun aggregateMutationMetrics(metrics: List<MutationMetrics>): MutationMetrics? {
        if (metrics.isEmpty()) return null
        val killed = metrics.sumOf { it.killed }
        val survived = metrics.sumOf { it.survived }
        val noCoverage = metrics.sumOf { it.noCoverage }
        val total = metrics.sumOf { it.total }
        if (total == 0) return null
        val detected = killed + survived
        return MutationMetrics(
            mutationScore = killed.toDouble() / total * 100.0,
            testStrength = if (detected > 0) killed.toDouble() / detected * 100.0 else null,
            killed = killed,
            survived = survived,
            noCoverage = noCoverage,
            total = total,
        )
    }

    fun renderQualitySummary(repoRoot: File, extendedTests: Boolean): Pair<String, String> {
        val modules = discoverBackendModulesWithCoverage(repoRoot)
        val fullTotal =
            parseKoverReport(repoRoot.resolve("build/reports/kover/report.xml"))
                ?: aggregateCoverage(repoRoot, modules)
        val unitTotal =
            parseKoverReport(repoRoot.resolve("build/reports/kover/unit/report.xml"))
                ?: aggregateCoverage(repoRoot, modules, unit = true)
        val deltas = moduleCoverageDeltas(repoRoot, modules)
        val backendDelta = backendCoverageDelta(repoRoot)
        val frontendDelta = frontendCoverageDelta(repoRoot)

        val markdown =
            buildString {
                appendLine("## Quality Gate Report")
                appendLine()
                appendCoverageSection(modules, repoRoot, fullTotal, unitTotal, deltas)
                appendBackendE2eCoverageSection(backendDelta)
                appendFrontendCoverageSection(frontendDelta)
                val mutationResult = appendMutationSection(repoRoot, extendedTests)
                appendCorrelationSection(repoRoot, extendedTests, mutationResult)
            }
        return markdown to coverageSummaryJson(fullTotal, unitTotal, deltas, backendDelta, frontendDelta)
    }

    private fun StringBuilder.appendBackendE2eCoverageSection(backendDelta: BackendCoverageDelta) {
        appendLine("### Backend E2E Coverage (Kover runtime)")
        appendLine()
        if (backendDelta.full == null && backendDelta.unit == null && backendDelta.e2e == null) {
            appendLine("_No backend Kover reports found. Run `./gradlew e2eCheck` first._")
            appendLine()
            return
        }

        appendLine("| Layer | Line | Branch | Instruction |")
        appendLine("|-------|------|--------|-------------|")
        appendLine(
            "| Full (unit + integration) | ${backendDelta.full?.line.fmt()} | " +
                "${backendDelta.full?.branch.fmt()} | ${backendDelta.full?.instruction.fmt()} |",
        )
        appendLine(
            "| Unit | ${backendDelta.unit?.line.fmt()} | ${backendDelta.unit?.branch.fmt()} | " +
                "${backendDelta.unit?.instruction.fmt()} |",
        )
        appendLine(
            "| E2E runtime | ${backendDelta.e2e?.line.fmt()} | ${backendDelta.e2e?.branch.fmt()} | " +
                "${backendDelta.e2e?.instruction.fmt()} |",
        )
        appendLine()
        appendLine("| Δ Full-Unit | Δ E2E-Full |")
        appendLine("|-------------|-------------|")
        appendLine(
            "| ${backendDelta.fullMinusUnit.fmt(signed = true)} | " +
                "${backendDelta.e2eMinusFull.fmt(signed = true)} |",
        )
        appendLine()
    }

    private fun StringBuilder.appendCoverageSection(
        modules: List<String>,
        repoRoot: File,
        fullTotal: CoverageMetrics?,
        unitTotal: CoverageMetrics?,
        deltas: List<ModuleCoverageDelta>,
    ) {
        appendLine("### Coverage (Kover)")
        appendLine()
        if (modules.isEmpty() && fullTotal == null) {
            appendLine("_No Kover report found. Run `./gradlew check` first._")
            appendLine()
            return
        }

        appendLine("#### Full coverage (unit + integration)")
        appendLine()
        appendLine("| Module | Line | Branch | Instruction |")
        appendLine("|--------|------|--------|-------------|")
        modules.forEach { module ->
            val metrics = parseKoverReport(repoRoot.resolve("$module/build/reports/kover/report.xml"))
            if (metrics != null) {
                appendLine(
                    "| $module | ${metrics.line.fmt()} | ${metrics.branch.fmt()} | " +
                        "${metrics.instruction.fmt()} |",
                )
            }
        }
        if (fullTotal != null) {
            appendLine(
                "| **Total** | ${fullTotal.line.fmt(bold = true)} | " +
                    "${fullTotal.branch.fmt(bold = true)} | ${fullTotal.instruction.fmt(bold = true)} |",
            )
        }
        appendLine()

        appendLine("#### Unit coverage and delta")
        appendLine()
        if (deltas.none { it.unit != null } && unitTotal == null) {
            appendLine("_Unit-only coverage not generated. Run `./gradlew koverUnitXmlReport`._")
            appendLine()
            return
        }

        appendLine("| Module | Unit Line | Full Line | Δ Full-Unit | Warning |")
        appendLine("|--------|-----------|-----------|-------------|---------|")
        deltas.forEach { delta ->
            val warning = delta.warnings.joinToString("<br>").ifBlank { "" }
            appendLine(
                "| ${delta.module} | ${delta.unit?.line.fmt()} | ${delta.full?.line.fmt()} | " +
                    "${delta.lineDelta.fmt(signed = true)} | $warning |",
            )
        }
        if (unitTotal != null || fullTotal != null) {
            val totalDelta =
                if (fullTotal?.line != null && unitTotal?.line != null) {
                    fullTotal.line - unitTotal.line
                } else {
                    null
                }
            appendLine(
                "| **Total** | ${unitTotal?.line.fmt(bold = true)} | " +
                    "${fullTotal?.line.fmt(bold = true)} | ${totalDelta.fmt(signed = true, bold = true)} | |",
            )
        }
        appendLine()
    }

    private fun StringBuilder.appendFrontendCoverageSection(frontendDelta: FrontendCoverageDelta) {
        appendLine("### Frontend Coverage (Vitest / Playwright)")
        appendLine()
        if (frontendDelta.unit == null && frontendDelta.full == null && frontendDelta.e2e == null) {
            appendLine("_No frontend LCOV reports found. Run frontend coverage tasks first._")
            appendLine()
            return
        }

        appendLine("| Layer | Line |")
        appendLine("|-------|------|")
        appendLine("| Unit | ${frontendDelta.unit?.line.fmt()} |")
        appendLine("| Full | ${frontendDelta.full?.line.fmt()} |")
        appendLine("| E2E | ${frontendDelta.e2e?.line.fmt()} |")
        appendLine()
        appendLine("| Δ Full-Unit | Δ E2E-Full | Warning |")
        appendLine("|-------------|-------------|---------|")
        val warning = frontendDelta.warnings.joinToString("<br>").ifBlank { "" }
        appendLine(
            "| ${frontendDelta.fullMinusUnit.fmt(signed = true)} | " +
                "${frontendDelta.e2eMinusFull.fmt(signed = true)} | $warning |",
        )
        appendLine()
    }

    private data class MutationSectionResult(
        val moduleMetrics: Map<String, MutationMetrics>,
        val aggregate: MutationMetrics?,
    )

    private fun StringBuilder.appendMutationSection(
        repoRoot: File,
        extendedTests: Boolean,
    ): MutationSectionResult {
        appendLine("### Mutation Testing (PIT)")
        appendLine()
        if (!extendedTests) {
            appendLine("_Skipped; mutation tests run only on Nightly CI._")
            appendLine()
            return MutationSectionResult(emptyMap(), null)
        }

        val moduleMetrics =
            discoverWorkbenchModules(repoRoot)
                .mapNotNull { module ->
                    val metrics = loadMutationMetrics(module.resolve("build/reports/pitest"))
                    if (metrics == null) null else module.name to metrics
                }
                .toMap()
        val aggregate =
            loadMutationMetrics(repoRoot.resolve("build/reports/pitest"))
                ?: aggregateMutationMetrics(moduleMetrics.values.toList())

        if (moduleMetrics.isEmpty() && aggregate == null) {
            appendLine("_No PIT report found. Run `./gradlew mutationTest` first._")
            appendLine()
            return MutationSectionResult(emptyMap(), null)
        }

        appendLine("| Module | Mutation | Strength | PIT Line | Killed | Survived | No Cov |")
        appendLine("|--------|----------|----------|----------|--------|----------|--------|")
        moduleMetrics.toSortedMap().forEach { (module, metrics) ->
            appendLine(
                "| $module | ${metrics.mutationScore.fmt()} | ${metrics.testStrength.fmt()} | " +
                    "${metrics.pitLine.fmt()} | ${metrics.killed} | ${metrics.survived} | " +
                    "${metrics.noCoverage} |",
            )
        }
        if (aggregate != null) {
            appendLine(
                "| **Total** | ${aggregate.mutationScore.fmt(bold = true)} | " +
                    "${aggregate.testStrength.fmt(bold = true)} | ${aggregate.pitLine.fmt(bold = true)} | " +
                    "**${aggregate.killed}** | **${aggregate.survived}** | " +
                    "**${aggregate.noCoverage}** |",
            )
        }
        appendLine()
        return MutationSectionResult(moduleMetrics, aggregate)
    }

    private fun StringBuilder.appendCorrelationSection(
        repoRoot: File,
        extendedTests: Boolean,
        mutationResult: MutationSectionResult,
    ) {
        if (!extendedTests) return
        appendLine("### Coverage <-> Mutation")
        appendLine()
        val modules =
            (discoverBackendModulesWithCoverage(repoRoot) + mutationResult.moduleMetrics.keys)
                .toSortedSet()
                .toList()
        if (modules.isEmpty()) {
            appendLine("_No module data available for correlation._")
            appendLine()
            return
        }

        appendLine("| Module | Full Line | PIT Line | Δ | Mutation | Strength |")
        appendLine("|--------|-----------|----------|---|----------|----------|")
        modules.forEach { module ->
            val kover = parseKoverReport(repoRoot.resolve("$module/build/reports/kover/report.xml"))
            val mutation = mutationResult.moduleMetrics[module]
            val delta =
                if (kover?.line != null && mutation?.pitLine != null) {
                    mutation.pitLine - kover.line
                } else {
                    null
                }
            appendLine(
                "| $module | ${kover?.line.fmt()} | ${mutation?.pitLine.fmt()} | " +
                    "${delta.fmt(signed = true)} | ${mutation?.mutationScore.fmt()} | " +
                    "${mutation?.testStrength.fmt()} |",
            )
        }
        appendLine()
    }

    private fun coverageSummaryJson(
        fullTotal: CoverageMetrics?,
        unitTotal: CoverageMetrics?,
        deltas: List<ModuleCoverageDelta>,
        backendDelta: BackendCoverageDelta,
        frontendDelta: FrontendCoverageDelta,
    ): String =
        buildString {
            appendLine("{")
            appendLine("  \"full\": ${fullTotal.toJson(2)},")
            appendLine("  \"unit\": ${unitTotal.toJson(2)},")
            appendLine("  \"backend\": {")
            appendLine("    \"full\": ${backendDelta.full.toJson(4)},")
            appendLine("    \"unit\": ${backendDelta.unit.toJson(4)},")
            appendLine("    \"e2e\": ${backendDelta.e2e.toJson(4)},")
            appendLine(
                "    \"fullMinusUnit\": ${backendDelta.fullMinusUnit.toJsonNumber()}," +
                    "\n    \"e2eMinusFull\": ${backendDelta.e2eMinusFull.toJsonNumber()}",
            )
            appendLine("  },")
            appendLine("  \"frontend\": {")
            appendLine("    \"unit\": ${frontendDelta.unit.toJson(4)},")
            appendLine("    \"full\": ${frontendDelta.full.toJson(4)},")
            appendLine("    \"e2e\": ${frontendDelta.e2e.toJson(4)},")
            appendLine(
                "    \"fullMinusUnit\": ${frontendDelta.fullMinusUnit.toJsonNumber()}," +
                    "\n    \"e2eMinusFull\": ${frontendDelta.e2eMinusFull.toJsonNumber()}",
            )
            appendLine("  },")
            appendLine("  \"modules\": [")
            deltas.forEachIndexed { index, delta ->
                appendLine("    {")
                appendLine("      \"module\": \"${delta.module}\",")
                appendLine("      \"full\": ${delta.full.toJson(6)},")
                appendLine("      \"unit\": ${delta.unit.toJson(6)},")
                appendLine("      \"lineDelta\": ${delta.lineDelta.toJsonNumber()},")
                appendLine(
                    "      \"warnings\": [" +
                        delta.warnings.joinToString(", ") { "\"${it.jsonEscape()}\"" } +
                        "]",
                )
                append("    }")
                if (index < deltas.lastIndex) append(",")
                appendLine()
            }
            appendLine("  ]")
            appendLine("}")
        }

    private fun mergeKoverCounters(reports: List<File>): Map<String, Counter> =
        reports.fold(mutableMapOf()) { merged, report ->
            parseXml(report).childElements("counter").forEach { counter ->
                val type = counter.getAttribute("type").takeIf(String::isNotBlank) ?: return@forEach
                merged.merge(
                    type,
                    Counter(
                        missed = counter.getAttribute("missed").toIntOrNull() ?: 0,
                        covered = counter.getAttribute("covered").toIntOrNull() ?: 0,
                    ),
                    Counter::plus,
                )
            }
            merged
        }

    private fun parsePitMutations(path: File): MutationMetrics? {
        if (!path.isFile) return null
        val statusCounts =
            parseXml(path)
                .descendantElements("mutation")
                .groupingBy { it.getAttribute("status").ifBlank { "UNKNOWN" } }
                .eachCount()
        val killed = statusCounts["KILLED"] ?: 0
        val survived = statusCounts["SURVIVED"] ?: 0
        val noCoverage = statusCounts["NO_COVERAGE"] ?: 0
        val total = statusCounts.values.sum()
        if (total == 0) return null
        val detected = killed + survived
        return MutationMetrics(
            mutationScore = killed.toDouble() / total * 100.0,
            testStrength = if (detected > 0) killed.toDouble() / detected * 100.0 else null,
            killed = killed,
            survived = survived,
            noCoverage = noCoverage,
            total = total,
        )
    }

    private fun parsePitLineCoverage(path: File): Double? {
        val fromMutations = parsePitLineFromMutations(path.resolve("mutations.xml"))
        if (fromMutations != null) return fromMutations
        val lineCoverage = path.resolve("linecoverage.xml")
        if (!lineCoverage.isFile) return null

        val blocks = parseXml(lineCoverage).descendantElements("block")
        if (blocks.isEmpty()) return null
        val covered = blocks.count { block -> block.childElements("tests").firstOrNull()?.hasChildNodes() == true }
        return covered.toDouble() / blocks.size * 100.0
    }

    private fun parsePitLineFromMutations(path: File): Double? {
        if (!path.isFile) return null
        val allLines = mutableSetOf<Pair<String, String>>()
        val coveredLines = mutableSetOf<Pair<String, String>>()
        parseXml(path).descendantElements("mutation").forEach { mutation ->
            val source = mutation.childText("sourceFile") ?: return@forEach
            val line = mutation.childText("lineNumber") ?: return@forEach
            val key = source to line
            allLines.add(key)
            if (mutation.getAttribute("status") != "NO_COVERAGE") {
                coveredLines.add(key)
            }
        }
        if (allLines.isEmpty()) return null
        return coveredLines.size.toDouble() / allLines.size * 100.0
    }

    private fun discoverWorkbenchModules(repoRoot: File): List<File> =
        repoRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("workbench-") && it.name != "workbench-frontend" }
            ?.sortedBy { it.name }
            .orEmpty()
}

private const val UNIT_LINE_WARNING_THRESHOLD = 70.0
private const val INTEGRATION_LIFT_WARNING_THRESHOLD = 15.0

private data class Counter(val missed: Int, val covered: Int) {
    operator fun plus(other: Counter): Counter =
        Counter(missed = missed + other.missed, covered = covered + other.covered)

    fun coveredPercentage(): Double? {
        val total = missed + covered
        return if (total == 0) null else covered.toDouble() / total * 100.0
    }
}

private fun CoverageMetrics?.toJson(indent: Int): String {
    if (this == null) return "null"
    val prefix = " ".repeat(indent)
    val nextPrefix = " ".repeat(indent + 2)
    return buildString {
        appendLine("{")
        appendLine("$nextPrefix\"line\": ${line.toJsonNumber()},")
        appendLine("$nextPrefix\"branch\": ${branch.toJsonNumber()},")
        appendLine("$nextPrefix\"instruction\": ${instruction.toJsonNumber()}")
        append("$prefix}")
    }
}

private fun CoverageMetrics.hasData(): Boolean =
    line != null || branch != null || instruction != null

private fun Double?.toJsonNumber(): String = this?.let { "%.4f".formatInvariant(it) } ?: "null"

private fun Double?.fmt(signed: Boolean = false, bold: Boolean = false): String {
    if (this == null) return "N/A"
    val sign = if (signed && this > 0) "+" else ""
    val value = "$sign${"%.1f".formatInvariant(this)}%"
    return if (bold) "**$value**" else value
}

private fun Double.formatPct(): String = "${"%.1f".formatInvariant(this)}pp"

private fun String.formatInvariant(value: Double): String =
    java.lang.String.format(java.util.Locale.US, this, value)

private fun String.jsonEscape(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

private fun parseXml(path: File): Element =
    DocumentBuilderFactory.newInstance()
        .apply {
            isNamespaceAware = false
        }
        .newDocumentBuilder()
        .parse(path)
        .documentElement

private fun newDocument() = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

private fun writeXml(element: Element, output: File) {
    val transformer =
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }
    val writer = StringWriter()
    transformer.transform(DOMSource(element.ownerDocument), StreamResult(writer))
    output.writeText(writer.toString(), Charsets.UTF_8)
}

private fun Element.childElements(tagName: String): List<Element> =
    childNodes.asSequence()
        .filterIsInstance<Element>()
        .filter { it.tagName == tagName }
        .toList()

private fun Element.descendantElements(tagName: String): List<Element> =
    getElementsByTagName(tagName).asSequence().filterIsInstance<Element>().toList()

private fun Element.childText(tagName: String): String? =
    childElements(tagName).firstOrNull()?.textContent

private fun org.w3c.dom.NodeList.asSequence(): Sequence<Node> =
    (0 until length).asSequence().map { item(it) }
