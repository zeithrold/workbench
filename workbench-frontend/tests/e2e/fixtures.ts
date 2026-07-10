import path from 'node:path'
import process from 'node:process'
import { test as base, expect } from '@playwright/test'
import { collectClientCoverage, loadNextcovConfig } from 'nextcov/playwright'

const coverageEnabled = process.env.E2E_COLLECT_COVERAGE === 'true'
const previewBuildDir = '.svelte-kit/output/client'

function transformPreviewChunkUrl(url: string): string {
  try {
    const parsed = new URL(url)
    if (parsed.pathname.startsWith('/_app/')) {
      const chunkPath = path.join(process.cwd(), previewBuildDir, parsed.pathname)
      return `file://${chunkPath}`
    }
  }
  catch {
    // Keep vite dev URLs (for example /@fs/... or /src/...) unchanged.
  }
  return url
}

export const test = base.extend<{
  coverage: void
}>({
  coverage: [
    async ({ page }, use, testInfo) => {
      if (!coverageEnabled) {
        await use()
        return
      }

      const config = await loadNextcovConfig()
      await collectClientCoverage(page, testInfo, use, {
        ...config,
        transformUrl: transformPreviewChunkUrl,
      })
    },
    { scope: 'test', auto: true },
  ],
})

export { expect }
