import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath, pathToFileURL } from 'node:url'
import ts from 'typescript'

const SOURCE_EXTENSIONS = ['.svelte', '.ts', '.js', '.svelte.ts', '.svelte.js']
const COMPONENT_COVERAGE_VERSION = 1

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function toPosix(value) {
  return value.split(path.sep).join('/')
}

function walkFiles(directory) {
  if (!existsSync(directory))
    return []
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const target = path.join(directory, entry.name)
    return entry.isDirectory() ? walkFiles(target) : [target]
  })
}

function scriptSource(file, source) {
  if (!file.endsWith('.svelte'))
    return source
  return [...source.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/gi)]
    .map(match => match[1])
    .join('\n')
}

function requestedImports(importClause, source) {
  if (!importClause)
    return null
  if (importClause.isTypeOnly)
    return undefined
  if (importClause.namedBindings && ts.isNamespaceImport(importClause.namedBindings)) {
    const namespace = escapeRegExp(importClause.namedBindings.name.text)
    if (new RegExp(`\\b${namespace}\\s*\\[`).test(source))
      return null
    const members = new Set(
      [...source.matchAll(new RegExp(`\\b${namespace}\\.([A-Za-z_$][\\w$]*)`, 'g'))]
        .map(match => match[1]),
    )
    return members.size > 0 ? members : null
  }
  const requested = new Set()
  if (importClause.name)
    requested.add('default')
  if (importClause.namedBindings && ts.isNamedImports(importClause.namedBindings)) {
    for (const element of importClause.namedBindings.elements) {
      if (!element.isTypeOnly)
        requested.add(element.propertyName?.text ?? element.name.text)
    }
  }
  return requested
}

function exportRequest(exportClause, requestedExports) {
  if (!exportClause)
    return requestedExports
  if (ts.isNamespaceExport(exportClause))
    return requestedExports === null || requestedExports.has(exportClause.name.text) ? null : undefined

  const requested = new Set()
  for (const element of exportClause.elements) {
    if (element.isTypeOnly)
      continue
    const exportedName = element.name.text
    if (requestedExports === null || requestedExports.has(exportedName))
      requested.add(element.propertyName?.text ?? exportedName)
  }
  return requested.size > 0 ? requested : undefined
}

function extractModuleEdges(file, source, requestedExports = null) {
  const parsed = ts.createSourceFile(
    file,
    scriptSource(file, source),
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TS,
  )
  const edges = []

  function visit(node) {
    if (ts.isImportDeclaration(node) && ts.isStringLiteralLike(node.moduleSpecifier)) {
      const requested = requestedImports(node.importClause, source)
      if (requested === null || (requested && requested.size > 0))
        edges.push({ specifier: node.moduleSpecifier.text, requested })
    }
    if (ts.isExportDeclaration(node)
      && !node.isTypeOnly
      && node.moduleSpecifier
      && ts.isStringLiteralLike(node.moduleSpecifier)) {
      const requested = exportRequest(node.exportClause, requestedExports)
      if (requested !== undefined)
        edges.push({ specifier: node.moduleSpecifier.text, requested })
    }
    if (ts.isCallExpression(node)
      && node.expression.kind === ts.SyntaxKind.ImportKeyword
      && node.arguments.length === 1
      && ts.isStringLiteralLike(node.arguments[0])) {
      edges.push({ specifier: node.arguments[0].text, requested: null })
    }
    ts.forEachChild(node, visit)
  }

  visit(parsed)
  return edges
}

export function extractModuleSpecifiers(file, source) {
  return [...new Set(extractModuleEdges(file, source).map(edge => edge.specifier))]
}

function resolutionCandidates(base) {
  const candidates = [base]
  const extension = path.extname(base)
  if (!extension) {
    SOURCE_EXTENSIONS.forEach(candidate => candidates.push(`${base}${candidate}`))
    SOURCE_EXTENSIONS.forEach(candidate => candidates.push(path.join(base, `index${candidate}`)))
  }
  if (base.endsWith('.svelte.js'))
    candidates.push(base.replace(/\.svelte\.js$/, '.svelte.ts'))
  else if (base.endsWith('.js'))
    candidates.push(base.replace(/\.js$/, '.ts'))
  return [...new Set(candidates)]
}

function resolveLocalSpecifier(frontendRoot, importer, specifier) {
  let base
  if (specifier === '$lib')
    base = path.join(frontendRoot, 'src/lib')
  else if (specifier.startsWith('$lib/'))
    base = path.join(frontendRoot, 'src/lib', specifier.slice(5))
  else if (specifier.startsWith('.'))
    base = path.resolve(path.dirname(importer), specifier)
  else return null

  const resolved = resolutionCandidates(base).find(candidate => existsSync(candidate) && statSync(candidate).isFile())
  if (!resolved) {
    const relativeImporter = toPosix(path.relative(frontendRoot, importer))
    throw new Error(`Cannot resolve local import ${JSON.stringify(specifier)} from ${relativeImporter}`)
  }
  return path.resolve(resolved)
}

function isRouteEntry(file) {
  return /\+(?:page|layout)\.(?:svelte|ts|js)$/.test(file)
    && !/\.(?:test|spec)\.(?:ts|js)$/.test(file)
}

function isEligibleComponent(frontendRoot, file) {
  const relative = toPosix(path.relative(frontendRoot, file))
  return relative.startsWith('src/lib/')
    && !relative.startsWith('src/lib/components/ui/')
    && relative.endsWith('.svelte')
    && !relative.endsWith('.stories.svelte')
    && !/(?:^|\/)[^/]*(?:test|story)-(?:host|target)\.svelte$/.test(relative)
}

export function discoverProductionComponents(frontendRoot) {
  const normalizedRoot = path.resolve(frontendRoot)
  const roots = walkFiles(path.join(normalizedRoot, 'src/routes')).filter(isRouteEntry)
  const requests = new Map()
  const queue = []
  const components = new Set()

  function enqueue(file, requested) {
    const previous = requests.get(file)
    if (previous === null)
      return
    if (requested === null) {
      requests.set(file, null)
      queue.push(file)
      return
    }
    const combined = new Set(previous ?? [])
    const size = combined.size
    requested.forEach(name => combined.add(name))
    if (combined.size !== size || previous === undefined) {
      requests.set(file, combined)
      queue.push(file)
    }
  }

  roots.forEach(root => enqueue(root, null))

  while (queue.length > 0) {
    const file = queue.pop()
    if (isEligibleComponent(normalizedRoot, file))
      components.add(file)
    const source = readFileSync(file, 'utf8')
    for (const edge of extractModuleEdges(file, source, requests.get(file))) {
      const resolved = resolveLocalSpecifier(normalizedRoot, file, edge.specifier)
      if (resolved && SOURCE_EXTENSIONS.some(extension => resolved.endsWith(extension)))
        enqueue(resolved, edge.requested)
    }
  }

  return [...components]
    .map(file => toPosix(path.relative(normalizedRoot, file)))
    .sort()
}

export function parseSvelteLcov(lcovText) {
  const mounted = new Map()
  for (const record of lcovText.split('end_of_record')) {
    const lines = record.split(/\r?\n/).filter(Boolean)
    const source = lines.find(line => line.startsWith('SF:'))?.slice(3)
    if (!source?.endsWith('.svelte'))
      continue
    const componentFunction = lines.find(line => line.startsWith('FN:'))?.split(',').slice(1).join(',')
    const invocation = componentFunction
      ? lines.find(line => line.startsWith('FNDA:') && line.slice(5).split(',').slice(1).join(',') === componentFunction)
      : null
    const count = invocation ? Number.parseInt(invocation.slice(5).split(',')[0], 10) : 0
    mounted.set(toPosix(source.replace(/^.*?(?=src\/)/, '')), Number.isFinite(count) && count > 0)
  }
  return mounted
}

export function componentHash(frontendRoot, component) {
  return createHash('sha256').update(readFileSync(path.join(frontendRoot, component))).digest('hex')
}

function layerFor(component) {
  if (component.startsWith('src/lib/components/'))
    return 'components'
  if (component.startsWith('src/lib/shared/'))
    return 'shared'
  if (component.startsWith('src/lib/features/'))
    return 'features'
  if (component.startsWith('src/lib/widgets/'))
    return 'widgets'
  return 'other'
}

function summarizeLayer(components, covered) {
  const total = components.length
  const mountedCount = components.filter(component => covered.has(component)).length
  return {
    total,
    mounted: mountedCount,
    percentage: total === 0 ? 100 : mountedCount / total * 100,
  }
}

export function evaluateComponentCoverage({
  eligible,
  mounted,
  baseline,
  changed = [],
  baseBaseline = null,
  hashes,
  compareBranch = null,
}) {
  const eligibleSet = new Set(eligible)
  const covered = new Set(eligible.filter(component => mounted.get(component) === true))
  const uncovered = eligible.filter(component => !covered.has(component))
  const baselineEntries = new Map((baseline?.uncovered ?? []).map(entry => [entry.path, entry.hash]))
  const baseEntries = new Map((baseBaseline?.uncovered ?? []).map(entry => [entry.path, entry.hash]))
  const changedSet = new Set(changed)

  const newUncovered = uncovered.filter(component => !baselineEntries.has(component))
  const staleBaseline = [...baselineEntries.keys()].filter(component => !eligibleSet.has(component) || covered.has(component))
  const changedUncovered = uncovered.filter(component => changedSet.has(component)
    && baselineEntries.get(component) !== hashes.get(component))
  const baselineAdditions = baseBaseline
    ? [...baselineEntries.keys()].filter(component => !baseEntries.has(component))
    : []
  const baselineRewrites = baseBaseline
    ? [...baselineEntries.entries()]
        .filter(([component, hash]) => baseEntries.has(component) && baseEntries.get(component) !== hash)
        .map(([component]) => component)
    : []

  const layers = {}
  for (const layer of ['components', 'shared', 'features', 'widgets', 'other']) {
    layers[layer] = summarizeLayer(eligible.filter(component => layerFor(component) === layer), covered)
  }

  return {
    version: COMPONENT_COVERAGE_VERSION,
    metric: 'storybook-component-mount-coverage',
    total: eligible.length,
    mounted: covered.size,
    percentage: eligible.length === 0 ? 100 : covered.size / eligible.length * 100,
    targetPercentage: 100,
    compareBranch,
    layers,
    uncoveredComponents: uncovered,
    violations: {
      newUncovered,
      changedUncovered,
      staleBaseline,
      baselineAdditions,
      baselineRewrites,
    },
  }
}

function git(frontendRoot, arguments_, allowFailure = false) {
  try {
    return execFileSync('git', arguments_, {
      cwd: frontendRoot,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    }).trim()
  }
  catch (error) {
    if (allowFailure)
      return ''
    throw error
  }
}

function discoverCompareBranch(frontendRoot) {
  if (process.env.COMPARE_BRANCH)
    return process.env.COMPARE_BRANCH
  return git(frontendRoot, ['rev-parse', '--verify', 'origin/main'], true) ? 'origin/main' : null
}

function changedComponents(frontendRoot, compareBranch) {
  const repoRoot = git(frontendRoot, ['rev-parse', '--show-toplevel'])
  const paths = new Set()
  const commands = []
  if (compareBranch)
    commands.push(['diff', '--name-only', '--diff-filter=ACMR', `${compareBranch}...HEAD`])
  commands.push(['diff', '--name-only', '--diff-filter=ACMR'])
  commands.push(['diff', '--cached', '--name-only', '--diff-filter=ACMR'])
  commands.push(['ls-files', '--others', '--exclude-standard'])
  for (const command of commands) {
    for (const file of git(frontendRoot, command, true).split(/\r?\n/).filter(Boolean)) {
      const absolute = path.resolve(repoRoot, file)
      const relative = toPosix(path.relative(frontendRoot, absolute))
      if (relative.startsWith('src/lib/') && relative.endsWith('.svelte'))
        paths.add(relative)
    }
  }
  return [...paths]
}

function loadJson(file, required = true) {
  if (!existsSync(file)) {
    if (required)
      throw new Error(`Required file does not exist: ${file}`)
    return null
  }
  return JSON.parse(readFileSync(file, 'utf8'))
}

function loadBaseBaseline(frontendRoot, compareBranch, baselinePath) {
  if (!compareBranch)
    return null
  const repoRoot = git(frontendRoot, ['rev-parse', '--show-toplevel'])
  const relative = toPosix(path.relative(repoRoot, baselinePath))
  const content = git(frontendRoot, ['show', `${compareBranch}:${relative}`], true)
  return content ? JSON.parse(content) : null
}

function writeBaseline(file, frontendRoot, uncovered) {
  const baseline = {
    version: COMPONENT_COVERAGE_VERSION,
    uncovered: uncovered.map(component => ({ path: component, hash: componentHash(frontendRoot, component) })),
  }
  writeFileSync(file, `${JSON.stringify(baseline, null, 2)}\n`)
}

function printViolations(violations) {
  const labels = {
    newUncovered: 'New uncovered production components',
    changedUncovered: 'Changed baseline components that remain uncovered',
    staleBaseline: 'Stale baseline entries that must be removed',
    baselineAdditions: 'Baseline additions are forbidden after bootstrap',
    baselineRewrites: 'Baseline fingerprints must not be rewritten',
  }
  for (const [key, components] of Object.entries(violations)) {
    if (components.length === 0)
      continue
    console.error(`\n${labels[key]}:`)
    components.forEach(component => console.error(`  - ${component}`))
  }
}

export function hasViolations(report) {
  return Object.values(report.violations).some(components => components.length > 0)
}

export function runCli(argv = process.argv.slice(2)) {
  const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  const coverageRoot = path.join(frontendRoot, 'coverage/storybook-components')
  const lcovPath = path.join(coverageRoot, 'lcov.info')
  const reportPath = path.join(coverageRoot, 'component-coverage.json')
  const baselinePath = path.join(frontendRoot, 'storybook-component-coverage-baseline.json')
  const writeBaselineRequested = argv.includes('--write-baseline')
  const eligible = discoverProductionComponents(frontendRoot)
  const mounted = parseSvelteLcov(readFileSync(lcovPath, 'utf8'))
  const hashes = new Map(eligible.map(component => [component, componentHash(frontendRoot, component)]))

  if (writeBaselineRequested) {
    const uncovered = eligible.filter(component => mounted.get(component) !== true)
    writeBaseline(baselinePath, frontendRoot, uncovered)
    console.log(`Wrote ${uncovered.length} baseline entries to ${path.relative(frontendRoot, baselinePath)}`)
    return 0
  }

  const compareBranch = discoverCompareBranch(frontendRoot)
  const baseline = loadJson(baselinePath)
  const report = evaluateComponentCoverage({
    eligible,
    mounted,
    baseline,
    changed: changedComponents(frontendRoot, compareBranch),
    baseBaseline: loadBaseBaseline(frontendRoot, compareBranch, baselinePath),
    hashes,
    compareBranch,
  })
  writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`)
  console.log(
    `Storybook component mount coverage: ${report.mounted}/${report.total} `
    + `(${report.percentage.toFixed(2)}%, target ${report.targetPercentage}%)`,
  )
  console.log(`Report: ${path.relative(frontendRoot, reportPath)}`)
  if (hasViolations(report)) {
    printViolations(report.violations)
    return 1
  }
  return 0
}

const invokedAsScript = process.argv[1]
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href
if (invokedAsScript)
  process.exitCode = runCli()
