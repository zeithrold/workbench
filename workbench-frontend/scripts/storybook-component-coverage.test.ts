import { mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  discoverProductionComponents,
  evaluateComponentCoverage,
  extractModuleSpecifiers,
  hasViolations,
  parseSvelteLcov,
} from './storybook-component-coverage.mjs'

function fixture(files: Record<string, string>): string {
  const root = path.join(tmpdir(), `storybook-components-${crypto.randomUUID()}`)
  for (const [file, content] of Object.entries(files)) {
    const target = path.join(root, file)
    mkdirSync(path.dirname(target), { recursive: true })
    writeFileSync(target, content)
  }
  return root
}

describe('storybook component coverage', () => {
  it('extracts imports, re-exports, and dynamic imports from Svelte scripts', () => {
    const source = `
      <script module lang="ts">export { default as A } from './a.svelte'</script>
      <script lang="ts">
        import B from '$lib/b.svelte'
        const load = () => import('./lazy.svelte')
      </script>
    `

    expect(extractModuleSpecifiers('example.svelte', source)).toEqual([
      './a.svelte',
      '$lib/b.svelte',
      './lazy.svelte',
    ])
  })

  it('discovers production components through aliases, barrels, cycles, and dynamic imports while excluding shadcn UI', () => {
    const root = fixture({
      'src/routes/+layout.svelte': `<script>import { A } from '$lib/features/demo/index.js'</script><A />`,
      'src/lib/features/demo/index.ts': `
        export { default as A } from './a.svelte'
        export { default as Unused } from './unused.svelte'
        export type { default as TypeOnly } from './type-only.svelte'
      `,
      'src/lib/features/demo/a.svelte': `<script>import B from './b.svelte'; import Button from '$lib/components/ui/button/button.svelte'; const load = () => import('./lazy.svelte')</script><B /><Button />`,
      'src/lib/features/demo/b.svelte': `<script>import A from './a.svelte'</script>`,
      'src/lib/features/demo/lazy.svelte': `<p>Lazy</p>`,
      'src/lib/components/ui/button/button.svelte': `<script>import BusinessLabel from '$lib/shared/business-label.svelte'</script><BusinessLabel />`,
      'src/lib/shared/business-label.svelte': `<span>Business label</span>`,
      'src/lib/features/demo/unused.svelte': `<p>Unused</p>`,
      'src/lib/features/demo/type-only.svelte': `<p>Type only</p>`,
      'src/lib/features/demo/a-story-host.svelte': `<p>Story host</p>`,
    })

    expect(discoverProductionComponents(root)).toEqual([
      'src/lib/features/demo/a.svelte',
      'src/lib/features/demo/b.svelte',
      'src/lib/features/demo/lazy.svelte',
      'src/lib/shared/business-label.svelte',
    ])
  })

  it('fails closed when a local import cannot be resolved', () => {
    const root = fixture({
      'src/routes/+page.svelte': `<script>import Missing from '$lib/missing.svelte'</script>`,
    })

    expect(() => discoverProductionComponents(root)).toThrow('Cannot resolve local import')
  })

  it('follows only used members of namespace component imports', () => {
    const root = fixture({
      'src/routes/+page.svelte': `<script>import * as Dialog from '$lib/dialog/index.js'</script><Dialog.Root />`,
      'src/lib/dialog/index.ts': `
        export { default as Root } from './root.svelte'
        export { default as Unused } from './unused.svelte'
      `,
      'src/lib/dialog/root.svelte': `<p>Root</p>`,
      'src/lib/dialog/unused.svelte': `<p>Unused</p>`,
    })

    expect(discoverProductionComponents(root)).toEqual(['src/lib/dialog/root.svelte'])
  })

  it('uses the Svelte component function invocation instead of module import as a mount signal', () => {
    const mounted = parseSvelteLcov(`
SF:src/lib/mounted.svelte
FN:1,Mounted
FNF:1
FNH:1
FNDA:2,Mounted
LF:1
LH:1
end_of_record
SF:src/lib/imported-only.svelte
FN:1,Imported_only
FN:5,module_helper
FNF:2
FNH:1
FNDA:0,Imported_only
FNDA:1,module_helper
LF:2
LH:1
end_of_record
`)

    expect(mounted.get('src/lib/mounted.svelte')).toBe(true)
    expect(mounted.get('src/lib/imported-only.svelte')).toBe(false)
  })

  it('reports regressions, changed debt, stale debt, and forbidden baseline mutations', () => {
    const eligible = ['src/lib/components/a.svelte', 'src/lib/features/b.svelte', 'src/lib/widgets/c.svelte']
    const report = evaluateComponentCoverage({
      eligible,
      mounted: new Map([
        ['src/lib/components/a.svelte', true],
        ['src/lib/features/b.svelte', false],
        ['src/lib/widgets/c.svelte', false],
      ]),
      baseline: {
        uncovered: [
          { path: 'src/lib/components/a.svelte', hash: 'a' },
          { path: 'src/lib/features/b.svelte', hash: 'old-b' },
          { path: 'src/lib/features/new-debt.svelte', hash: 'new' },
        ],
      },
      baseBaseline: {
        uncovered: [
          { path: 'src/lib/components/a.svelte', hash: 'a' },
          { path: 'src/lib/features/b.svelte', hash: 'base-b' },
        ],
      },
      changed: ['src/lib/features/b.svelte'],
      hashes: new Map([
        ['src/lib/components/a.svelte', 'a'],
        ['src/lib/features/b.svelte', 'current-b'],
        ['src/lib/widgets/c.svelte', 'c'],
      ]),
      compareBranch: 'origin/main',
    })

    expect(report.percentage).toBeCloseTo(100 / 3)
    expect(report.violations.newUncovered).toEqual(['src/lib/widgets/c.svelte'])
    expect(report.violations.changedUncovered).toEqual(['src/lib/features/b.svelte'])
    expect(report.violations.staleBaseline).toEqual([
      'src/lib/components/a.svelte',
      'src/lib/features/new-debt.svelte',
    ])
    expect(report.violations.baselineAdditions).toEqual(['src/lib/features/new-debt.svelte'])
    expect(report.violations.baselineRewrites).toEqual(['src/lib/features/b.svelte'])
    expect(hasViolations(report)).toBe(true)
  })

  it('accepts a ratcheted baseline with transitively mounted components', () => {
    const report = evaluateComponentCoverage({
      eligible: ['src/lib/components/a.svelte', 'src/lib/features/b.svelte'],
      mounted: new Map([
        ['src/lib/components/a.svelte', true],
        ['src/lib/features/b.svelte', false],
      ]),
      baseline: { uncovered: [{ path: 'src/lib/features/b.svelte', hash: 'b' }] },
      baseBaseline: { uncovered: [{ path: 'src/lib/features/b.svelte', hash: 'b' }] },
      changed: [],
      hashes: new Map([
        ['src/lib/components/a.svelte', 'a'],
        ['src/lib/features/b.svelte', 'b'],
      ]),
    })

    expect(report.layers.components.mounted).toBe(1)
    expect(hasViolations(report)).toBe(false)
  })
})
