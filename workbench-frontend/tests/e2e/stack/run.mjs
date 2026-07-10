import { spawn } from 'node:child_process'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { DockerComposeEnvironment, Wait } from 'testcontainers'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..')
const frontendRoot = path.join(repoRoot, 'workbench-frontend')
const gradlew = path.join(repoRoot, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew')

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

async function main() {
  let compose
  let webProcess
  let previewProcess

  try {
    compose = await new DockerComposeEnvironment(repoRoot, 'docker-compose.e2e.yaml')
      .withStartupTimeout(180_000)
      .withWaitStrategy('postgres-1', Wait.forHealthCheck())
      .withWaitStrategy('valkey-1', Wait.forHealthCheck())
      .withWaitStrategy('redpanda-1', Wait.forHealthCheck())
      .up()

    const postgres = compose.getContainer('postgres-1')
    const valkey = compose.getContainer('valkey-1')
    const redpanda = compose.getContainer('redpanda-1')

    const postgresPort = postgres.getMappedPort(5432)
    const valkeyPort = valkey.getMappedPort(6379)
    const kafkaPort = redpanda.getMappedPort(19092)

    const springEnv = {
      ...process.env,
      SPRING_PROFILES_ACTIVE: 'local,e2e',
      E2E_POSTGRES_HOST: 'localhost',
      E2E_POSTGRES_PORT: String(postgresPort),
      E2E_VALKEY_HOST: 'localhost',
      E2E_VALKEY_PORT: String(valkeyPort),
      E2E_KAFKA_BOOTSTRAP: `localhost:${kafkaPort}`,
    }

    webProcess = startProcess(gradlew, [
      ':workbench-web:bootRun',
      '--args=--spring.profiles.active=local,e2e',
      '--no-daemon',
    ], {
      cwd: repoRoot,
      env: springEnv,
    })

    await waitForHttp('http://127.0.0.1:8080/api/actuator/health')

    previewProcess = startProcess('pnpm', ['preview', '--host', '127.0.0.1', '--port', '4173'], {
      cwd: frontendRoot,
      env: {
        ...process.env,
        PUBLIC_SESSION_GATEWAY: 'demo',
      },
    })

    await waitForHttp('http://127.0.0.1:4173')

    process.env.E2E_BASE_URL = 'http://127.0.0.1:4173'
    await runProcess('pnpm', ['test:e2e:coverage'], {
      cwd: frontendRoot,
      env: {
        ...process.env,
        E2E_BASE_URL: 'http://127.0.0.1:4173',
        E2E_COLLECT_COVERAGE: 'true',
      },
    })

    await runProcess('pnpm', ['coverage:e2e'], {
      cwd: frontendRoot,
      env: process.env,
    })
  }
  finally {
    if (previewProcess && !previewProcess.killed)
      previewProcess.kill('SIGTERM')
    if (webProcess && !webProcess.killed)
      webProcess.kill('SIGTERM')
    if (compose)
      await compose.down()
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
