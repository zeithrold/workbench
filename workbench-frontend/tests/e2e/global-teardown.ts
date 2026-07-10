import process from 'node:process'
import { finalizeCoverage, loadNextcovConfig } from 'nextcov/playwright'

export default async function globalTeardown() {
  if (process.env.E2E_COLLECT_COVERAGE !== 'true' || process.env.E2E_STACK === 'true')
    return

  const config = await loadNextcovConfig()
  await finalizeCoverage(config)
}
