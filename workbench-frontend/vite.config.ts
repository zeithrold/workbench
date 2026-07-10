import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { storybookTest } from '@storybook/addon-vitest/vitest-plugin'
import { sveltekit } from '@sveltejs/kit/vite'
import tailwindcss from '@tailwindcss/vite'
import { playwright } from '@vitest/browser-playwright'
import { defineConfig } from 'vitest/config'

const dirname = path.dirname(fileURLToPath(import.meta.url))

const coverageInclude = ['src/**/*.{ts,js,svelte}']
const coverageExclude = [
  'src/**/*.{test,spec}.{ts,js}',
  'src/**/*.stories.svelte',
  'src/lib/api/generated/**',
]

export default defineConfig({
  build: {
    sourcemap: true,
  },
  plugins: [tailwindcss(), sveltekit()],
  resolve: {
    conditions: process.env.VITEST ? ['browser'] : undefined,
  },
  test: {
    coverage: {
      provider: 'v8',
      include: coverageInclude,
      exclude: coverageExclude,
      reporter: ['text', 'html', 'json', 'lcov'],
    },
    projects: [
      {
        extends: true,
        test: {
          name: 'unit',
          include: ['src/**/*.{test,spec}.{ts,js}'],
          environment: 'jsdom',
        },
      },
      {
        extends: true,
        plugins: [
          storybookTest({ configDir: path.join(dirname, '.storybook') }),
        ],
        test: {
          name: 'storybook',
          server: {
            deps: {
              inline: [
                'bits-ui',
                '@storybook/addon-svelte-csf',
                '@storybook/svelte',
              ],
            },
          },
          browser: {
            enabled: true,
            headless: true,
            provider: playwright({}),
            instances: [{ browser: 'chromium' }],
          },
          setupFiles: ['.storybook/vitest.setup.ts'],
        },
      },
    ],
  },
})
