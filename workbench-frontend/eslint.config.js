import antfu from '@antfu/eslint-config'
import zeithrold, { ignoredSvelteFiles, productionSvelteFiles } from './eslint-plugin-zeithrold.js'

export default antfu(
  {
    svelte: true,
    typescript: true,
    formatters: true,
    ignores: [
      '.svelte-kit/**',
      'coverage/**',
      'playwright-report/**',
      'project.inlang/**',
      'src/lib/api/generated/**',
      'src/lib/paraglide/**',
      'static/mockServiceWorker.js',
      'test-results/**',
    ],
  },
  {
    name: 'workbench/i18n-literals',
    files: productionSvelteFiles,
    ignores: ignoredSvelteFiles,
    plugins: { zeithrold },
    rules: {
      'zeithrold/no-untranslated-literal': 'warn',
    },
  },
)
