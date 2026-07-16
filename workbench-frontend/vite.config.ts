import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { paraglideVitePlugin } from '@inlang/paraglide-js'
import { storybookTest } from '@storybook/addon-vitest/vitest-plugin'
import { sveltekit } from '@sveltejs/kit/vite'
import tailwindcss from '@tailwindcss/vite'
import { playwright } from '@vitest/browser-playwright'
import { defineConfig } from 'vitest/config'

const dirname = path.dirname(fileURLToPath(import.meta.url))
const backendOrigin = process.env.WORKBENCH_BACKEND_ORIGIN ?? 'http://127.0.0.1:8080'

const coverageInclude = ['src/**/*.{ts,js}']
const coverageExclude = [
  'src/**/*.{test,spec}.{ts,js}',
  'src/**/*.svelte',
  'src/**/*.stories.svelte',
  'src/lib/api/generated/**',
  'src/lib/paraglide/**',
]

export default defineConfig({
  server: {
    proxy: {
      '/api': backendOrigin,
    },
  },
  preview: {
    proxy: {
      '/api': backendOrigin,
    },
  },
  build: {
    sourcemap: true,
  },
  optimizeDeps: {
    include: [
      '@lucide/svelte',
      '@lucide/svelte/icons/check',
      '@lucide/svelte/icons/chevron-right',
      '@lucide/svelte/icons/eye',
      '@lucide/svelte/icons/eye-off',
      '@lucide/svelte/icons/minus',
      '@lucide/svelte/icons/search',
      '@lucide/svelte/icons/x',
      '@tiptap/core',
      '@tiptap/extension-bubble-menu',
      '@tiptap/extension-code-block-lowlight',
      '@tiptap/extension-placeholder',
      '@tiptap/starter-kit',
      '@tiptap/suggestion',
      'highlight.js/lib/languages/bash',
      'highlight.js/lib/languages/css',
      'highlight.js/lib/languages/java',
      'highlight.js/lib/languages/javascript',
      'highlight.js/lib/languages/json',
      'highlight.js/lib/languages/kotlin',
      'highlight.js/lib/languages/markdown',
      'highlight.js/lib/languages/plaintext',
      'highlight.js/lib/languages/sql',
      'highlight.js/lib/languages/typescript',
      'highlight.js/lib/languages/xml',
      'highlight.js/lib/languages/yaml',
      'lowlight',
    ],
  },
  plugins: [
    paraglideVitePlugin({
      project: './project.inlang',
      outdir: './src/lib/paraglide',
      emitTsDeclarations: true,
    }),
    tailwindcss(),
    sveltekit(),
  ],
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
          setupFiles: ['src/test-setup.ts'],
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
                '@lucide/svelte',
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
