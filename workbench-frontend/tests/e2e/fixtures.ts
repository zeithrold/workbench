import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { test as base, expect } from '@playwright/test'

const coverageEnabled = process.env.E2E_COLLECT_COVERAGE === 'true'
const rawCoveragePath = path.join(process.cwd(), 'coverage/e2e/raw-coverage.json')
const collectedEntries: Array<Record<string, unknown>> = []

export const test = base.extend({
  page: async ({ page }, use) => {
    if (coverageEnabled)
      await page.coverage.startJSCoverage({ resetOnNavigation: false })

    await use(page)

    if (coverageEnabled) {
      const coverage = await page.coverage.stopJSCoverage()
      collectedEntries.push(...coverage)
      await mkdir(path.dirname(rawCoveragePath), { recursive: true })
      await writeFile(rawCoveragePath, JSON.stringify(collectedEntries))
    }
  },
})

export { expect }
