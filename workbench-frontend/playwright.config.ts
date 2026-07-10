import type { NextcovConfig } from 'nextcov'
import process from 'node:process'
import { defineConfig } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173'

export const nextcov: NextcovConfig = {
  buildDir: '.svelte-kit/output/client',
  outputDir: 'coverage/e2e',
  sourceRoot: './src',
  collectServer: false,
  include: ['src/**/*.{ts,js}'],
  exclude: [
    'src/**/*.test.{ts,js}',
    'src/**/*.spec.{ts,js}',
    'src/**/*.stories.{ts,js}',
    'src/**/*.svelte',
  ],
  reporters: ['html', 'lcov', 'json', 'text-summary'],
}

export default defineConfig({
  testDir: 'tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  globalSetup: 'tests/e2e/global-setup.ts',
  globalTeardown: 'tests/e2e/global-teardown.ts',
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: 'pnpm dev --host 127.0.0.1',
        port: 5173,
        reuseExistingServer: !process.env.CI,
      },
})
