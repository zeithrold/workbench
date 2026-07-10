import process from 'node:process'
import { initCoverage, loadNextcovConfig } from 'nextcov/playwright'

export default async function globalSetup() {
  if (process.env.E2E_COLLECT_COVERAGE !== 'true' || process.env.E2E_STACK === 'true')
    return

  const config = await loadNextcovConfig()
  await initCoverage(config)
}
