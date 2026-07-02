import process from 'node:process'
import { defineConfig } from '@playwright/test'

export default defineConfig({
  webServer: {
    command: 'pnpm dev --host 127.0.0.1',
    port: 5173,
    reuseExistingServer: !process.env.CI,
  },
  testDir: 'tests/e2e',
})
