import type { Linter } from 'eslint'
import { ESLint } from 'eslint'
import svelte from 'eslint-plugin-svelte'
import { describe, expect, it } from 'vitest'
import zeithrold, {
  ignoredSvelteFiles,
  productionSvelteFiles,
} from '../../../eslint-plugin-zeithrold.js'

const svelteParserConfig = svelte.configs.base
  .find(config => config.languageOptions?.parser)
const svelteParser = svelteParserConfig?.languageOptions?.parser

async function lint(code: string, filePath = 'src/example.svelte') {
  const eslint = new ESLint({
    overrideConfigFile: true,
    overrideConfig: [
      {
        files: productionSvelteFiles,
        ignores: ignoredSvelteFiles,
        languageOptions: { parser: svelteParser },
        plugins: { zeithrold },
        rules: { 'zeithrold/no-untranslated-literal': 'warn' },
      },
    ] as Linter.Config[],
  })

  return eslint.lintText(code, { filePath })
}

function warnings(results: ESLint.LintResult[]) {
  return results.flatMap(result => result.messages)
    .filter(message => message.ruleId === 'zeithrold/no-untranslated-literal')
}

describe('zeithrold/no-untranslated-literal', () => {
  it('warns for user-visible template literals and UI properties', async () => {
    const results = await lint(`
      <script>
        const navigation = [{ href: '/projects', label: 'Projects' }]
        let saving = false
      </script>
      <h1>Workspace settings</h1>
      <input placeholder="Search projects" aria-label={'Project search'} />
      <p>{saving ? 'Saving…' : 'Save changes'}</p>
      <button onclick={() => confirm(\`Delete \${navigation[0].label}?\`)}>Delete</button>
    `)

    expect(warnings(results).map(message => message.message)).toEqual(expect.arrayContaining([
      expect.stringContaining('Projects'),
      expect.stringContaining('Workspace settings'),
      expect.stringContaining('Search projects'),
      expect.stringContaining('Project search'),
      expect.stringContaining('Saving'),
      expect.stringContaining('Save changes'),
      expect.stringContaining('Delete'),
    ]))
    expect(warnings(results)).toHaveLength(8)
  })

  it('ignores translated expressions and technical literals', async () => {
    const results = await lint(`
      <script>
        const href = '/projects'
        const status = 'ACTIVE'
        const style = 'text-primary'
        const value = status === 'ACTIVE' ? m.active() : m.inactive()
      </script>
      <a class={style} {href} data-status={status}>{m.open_project()}</a>
      <Badge>{status === 'ACTIVE' ? m.active() : m.inactive()}</Badge>
      <img alt="" src="/logo.svg" />
      <input placeholder="https://example.com" />
      <style>.label { color: red; }</style>
      <span>{42} · {value}</span>
    `)

    expect(warnings(results)).toHaveLength(0)
  })

  it('does not inspect conditions while checking rendered branches', async () => {
    const results = await lint(`
      <p>{status === 'ACTIVE' ? 'Enabled' : 'Disabled'}</p>
      <p>{scope ?? 'Entire tenant'}</p>
    `)

    const messages = warnings(results).map(message => message.message)
    expect(messages).toEqual(expect.arrayContaining([
      expect.stringContaining('Enabled'),
      expect.stringContaining('Disabled'),
      expect.stringContaining('Entire tenant'),
    ]))
    expect(messages.some(message => message.includes('ACTIVE'))).toBe(false)
    expect(messages).toHaveLength(3)
  })

  it('excludes Storybook and test components', async () => {
    const [storyResults, testResults] = await Promise.all([
      lint('<p>Story copy</p>', 'src/example.stories.svelte'),
      lint('<p>Fixture copy</p>', 'src/example.test.svelte'),
    ])

    expect(warnings(storyResults)).toHaveLength(0)
    expect(warnings(testResults)).toHaveLength(0)
  })

  it('reports each literal once at its source location', async () => {
    const results = await lint('<p>{ready ? `Ready for $' + '{name}` : "Waiting"}</p>')
    const messages = warnings(results)

    expect(messages).toHaveLength(2)
    expect(messages.map(message => message.line)).toEqual([1, 1])
    expect(new Set(messages.map(message => `${message.line}:${message.column}`)).size).toBe(2)
  })
})
