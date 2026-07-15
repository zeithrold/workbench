import antfu from '@antfu/eslint-config'

export default antfu({
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
    'test-results/**',
  ],
})
