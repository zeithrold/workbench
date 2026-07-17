import { spawn } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..')
const frontendRoot = path.join(repoRoot, 'workbench-frontend')
const playwrightConfigPath = path.join(frontendRoot, 'playwright.config.ts')
const koverE2eDir = path.join(repoRoot, 'build/kover-e2e')
const webJar = path.join(repoRoot, 'workbench-web/build/libs/workbench-web.jar')
const workerJar = path.join(repoRoot, 'workbench-worker/build/libs/workbench-worker.jar')
const infraCommand = 'uv'
const infraArgs = ['run', '--directory', 'scripts/dev', 'ephemeral-infra']

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

async function waitForHttp(url, timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok)
        return
    }
    catch {
      // retry until timeout
    }
    await sleep(2_000)
  }
  throw new Error(`Timed out waiting for ${url}`)
}

async function waitForConnector(url, timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok) {
        const status = await response.json()
        const connectorRunning = status.connector?.state === 'RUNNING'
        const tasksRunning = status.tasks?.length > 0 && status.tasks.every(task => task.state === 'RUNNING')
        if (connectorRunning && tasksRunning)
          return
      }
    }
    catch {
      // retry until timeout
    }
    await sleep(2_000)
  }
  throw new Error(`Timed out waiting for a RUNNING Debezium connector at ${url}`)
}

async function restartConnector(url) {
  const response = await fetch(`${url}/restart?includeTasks=true&onlyFailed=false`, {
    method: 'POST',
  })
  if (!response.ok)
    throw new Error(`Failed to restart Debezium connector at ${url}: HTTP ${response.status}`)
}

function runProcess(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      stdio: 'inherit',
      ...options,
    })
    child.on('error', reject)
    child.on('exit', (code) => {
      if (code === 0)
        resolve(child)
      else
        reject(new Error(`${command} ${args.join(' ')} exited with ${code}`))
    })
  })
}

function captureProcess(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      stdio: ['ignore', 'pipe', 'pipe'],
      ...options,
    })
    let stdout = ''
    let stderr = ''
    child.stdout.on('data', (chunk) => {
      stdout += chunk
    })
    child.stderr.on('data', (chunk) => {
      stderr += chunk
    })
    child.on('error', reject)
    child.on('exit', (code) => {
      if (code === 0)
        resolve(stdout)
      else
        reject(new Error(`${command} ${args.join(' ')} exited with ${code}: ${stderr.trim()}`))
    })
  })
}

function startProcess(command, args, options = {}) {
  const child = spawn(command, args, {
    stdio: 'inherit',
    ...options,
  })
  child.on('error', (error) => {
    console.error(error)
  })
  return child
}

async function stopProcess(child, label) {
  if (!child || child.killed)
    return

  child.kill('SIGTERM')
  await new Promise((resolve) => {
    const timeout = setTimeout(() => {
      if (!child.killed)
        child.kill('SIGKILL')
      resolve()
    }, 10_000)
    child.on('exit', () => {
      clearTimeout(timeout)
      resolve()
    })
  }).catch(() => {
    console.warn(`Failed to stop ${label}`)
  })
}

async function readKoverAgentPaths() {
  const manifestPath = path.join(koverE2eDir, 'agent-manifest.json')
  const { readFile } = await import('node:fs/promises')
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'))
  return {
    agentJar: manifest.agentJar,
    webArgs: manifest.webArgs,
    workerArgs: manifest.workerArgs,
  }
}

function javaLaunchArgs(agentJar, agentArgsFile, jarPath, springProfiles) {
  return [
    `-javaagent:${agentJar}=file:${agentArgsFile}`,
    '-jar',
    jarPath,
    `--spring.profiles.active=${springProfiles}`,
  ]
}

async function loadFrontendNextcovConfig() {
  const { loadNextcovConfig } = await import('nextcov/playwright')
  const config = await loadNextcovConfig(playwrightConfigPath)
  return {
    ...config,
    projectRoot: frontendRoot,
    outputDir: path.join(frontendRoot, config.outputDir),
    buildDir: path.join(frontendRoot, config.buildDir),
    sourceRoot: path.join(frontendRoot, config.sourceRoot),
  }
}

async function prepareFrontendCoverage() {
  const { initCoverage } = await import('nextcov/playwright')
  const config = await loadFrontendNextcovConfig()
  await initCoverage(config)
}

async function finalizeFrontendCoverage() {
  const { access } = await import('node:fs/promises')
  const { finalizeCoverage } = await import('nextcov/playwright')
  const config = await loadFrontendNextcovConfig()
  const result = await finalizeCoverage(config)
  const lcovPath = path.join(config.outputDir, 'lcov.info')
  try {
    await access(lcovPath)
  }
  catch {
    throw new Error(`Frontend E2E coverage missing at ${lcovPath}${result ? '' : ' (no coverage data collected)'}`)
  }
}

async function main() {
  let infraLease
  let webProcess
  let workerProcess
  let previewProcess

  try {
    const { agentJar, webArgs, workerArgs } = await readKoverAgentPaths()
    infraLease = JSON.parse(await captureProcess(
      infraCommand,
      [...infraArgs, 'up', '--profile', 'distributed', '--ttl', '2h', '--json'],
      { cwd: repoRoot },
    ))
    const manifest = infraLease
    const connectorUrl = `${manifest.endpoints.debezium}/connectors/workbench-outbox`

    const springEnv = {
      ...process.env,
      ...manifest.environment,
      SPRING_PROFILES_ACTIVE: 'local,e2e',
    }

    await mkdir(koverE2eDir, { recursive: true })

    workerProcess = startProcess(
      'java',
      javaLaunchArgs(agentJar, workerArgs, workerJar, 'local,e2e'),
      { cwd: repoRoot, env: springEnv },
    )

    webProcess = startProcess(
      'java',
      javaLaunchArgs(agentJar, webArgs, webJar, 'local,e2e'),
      { cwd: repoRoot, env: springEnv },
    )

    await waitForHttp(`${manifest.endpoints.web}/api/actuator/health`)
    await restartConnector(connectorUrl)
    await waitForConnector(`${connectorUrl}/status`)

    previewProcess = startProcess('pnpm', [
      'preview',
      '--host',
      '127.0.0.1',
      '--port',
      String(manifest.ports.frontend),
    ], {
      cwd: frontendRoot,
      env: {
        ...process.env,
        ...manifest.environment,
        PUBLIC_SESSION_GATEWAY: 'api',
      },
    })

    await waitForHttp(manifest.endpoints.frontend)

    await prepareFrontendCoverage()

    await runProcess('pnpm', ['test:e2e:coverage'], {
      cwd: frontendRoot,
      env: {
        ...process.env,
        E2E_BASE_URL: manifest.endpoints.frontend,
        E2E_COLLECT_COVERAGE: 'true',
        E2E_STACK: 'true',
      },
    })

    await finalizeFrontendCoverage()
  }
  finally {
    await stopProcess(previewProcess, 'frontend preview')
    await stopProcess(webProcess, 'workbench-web')
    await stopProcess(workerProcess, 'workbench-worker')
    if (infraLease)
      await runProcess(infraCommand, [...infraArgs, 'down', infraLease.leaseId], { cwd: repoRoot })
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
