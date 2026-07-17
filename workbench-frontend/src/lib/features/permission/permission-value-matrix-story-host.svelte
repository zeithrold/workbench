<script lang='ts'>
  import type { PermissionCondition, PermissionConditionOperator, PermissionConditionValue } from './permission-document.js'
  import type { PermissionFieldOption } from './permission-editor-model.js'
  import { untrack } from 'svelte'
  import ConditionRuleEditor from './condition-rule-editor.svelte'

  interface ValueMatrixCase {
    id: string
    label: string
    field: PermissionFieldOption
    operator: PermissionConditionOperator
    initialOperator: PermissionConditionOperator
    initialValue?: PermissionConditionValue
  }

  const { cases, fields }: { cases: ValueMatrixCase[], fields: PermissionFieldOption[] } = $props()

  const nodes: Extract<PermissionCondition, { field: unknown }>[] = $state(untrack(() => cases.map(matrixCase => ({
    uiId: matrixCase.id,
    field: matrixCase.field.field,
    op: matrixCase.initialOperator,
    ...(matrixCase.initialValue === undefined ? {} : { value: matrixCase.initialValue }),
  }))))

  function update(index: number, node: PermissionCondition) {
    if ('field' in node)
      nodes[index] = node
  }
</script>

<div class='mx-auto w-[min(96rem,calc(100vw-2rem))] space-y-4 rounded-xl bg-background p-4 text-foreground'>
  <header class='space-y-1'>
    <h1 class='text-xl font-semibold'>Permission Value matrix</h1>
    <p class='text-sm text-muted-foreground'>Every supported field and operator combination uses the production Value control.</p>
  </header>
  <div class='grid gap-4'>
    {#each cases as matrixCase, index (matrixCase.id)}
      <section class='space-y-3 rounded-lg border p-4' aria-labelledby={`${matrixCase.id}-title`} data-testid={`value-case-${matrixCase.id}`}>
        <div class='flex flex-wrap items-center justify-between gap-2'>
          <h2 id={`${matrixCase.id}-title`} class='font-medium'>{matrixCase.label}</h2>
          <code class='text-xs text-muted-foreground'>{matrixCase.field.type} · {matrixCase.operator}</code>
        </div>
        <ConditionRuleEditor node={nodes[index]} {fields} onChange={node => update(index, node)} />
        <pre class='sr-only' data-testid={`value-json-${matrixCase.id}`}>{JSON.stringify(nodes[index])}</pre>
      </section>
    {/each}
  </div>
</div>
