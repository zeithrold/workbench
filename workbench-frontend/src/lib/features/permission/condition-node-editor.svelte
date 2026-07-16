<script lang='ts'>
  import type { PermissionCondition, PermissionConditionOperator, PermissionConditionValue } from './permission-document.js'
  import { Button, Input, Label } from '$lib/shared/ui'
  import ConditionNodeEditor from './condition-node-editor.svelte'
  import { emptyPermissionPredicate, permissionConditionOperators, permissionSystemFields } from './permission-document.js'

  const { node, editable = true, onChange, onRemove }: {
    node: PermissionCondition
    editable?: boolean
    onChange: (node: PermissionCondition) => void
    onRemove?: () => void
  } = $props()

  const kind = $derived('field' in node ? 'predicate' : node.op)
  const fieldValue = $derived('field' in node ? (typeof node.field === 'string' ? node.field : `property.${node.field.apiId ?? node.field.code ?? ''}`) : '')
  const valueText = $derived('field' in node && node.value !== undefined ? JSON.stringify(node.value) : '')

  function changeKind(next: string) {
    if (next === 'predicate')
      onChange(emptyPermissionPredicate())
    else if (next === 'not')
      onChange({ op: 'not', arg: emptyPermissionPredicate() })
    else onChange({ op: next as 'and' | 'or', args: [emptyPermissionPredicate()] })
  }

  function parseField(raw: string) {
    if (!('field' in node))
      return
    onChange({ ...node, field: raw.startsWith('property.') ? { kind: 'property', code: raw.slice('property.'.length) } : raw })
  }

  function parseValue(raw: string): PermissionConditionValue {
    try {
      return JSON.parse(raw) as PermissionConditionValue
    }
    catch {
      return raw
    }
  }
</script>

<div class='space-y-3 rounded-lg border bg-muted/20 p-3' data-condition-kind={kind}>
  <div class='flex flex-wrap items-center gap-2'>
    <select class='h-9 rounded-md border bg-background px-3 text-sm' value={kind} disabled={!editable} onchange={event => changeKind(event.currentTarget.value)} aria-label='Condition kind'>
      <option value='predicate'>Condition</option><option value='and'>All of</option><option value='or'>Any of</option><option value='not'>Not</option>
    </select>
    <div class='flex-1'></div>
    {#if editable && onRemove}<Button type='button' variant='ghost' size='sm' onclick={onRemove}>Remove</Button>{/if}
  </div>

  {#if 'field' in node}
    <div class='grid gap-3 md:grid-cols-[1.25fr_0.8fr_1.25fr]'>
      <div class='space-y-1'><Label>Field</Label><Input list='permission-system-fields' value={fieldValue} disabled={!editable} onchange={event => parseField(event.currentTarget.value)} placeholder='issue.reporter or property.code' /></div>
      <div class='space-y-1'><Label>Operator</Label><select class='h-9 w-full rounded-md border bg-background px-3 text-sm' value={node.op} disabled={!editable} onchange={event => onChange({ ...node, op: event.currentTarget.value as PermissionConditionOperator })}>{#each permissionConditionOperators as operator (operator)}<option value={operator}>{operator}</option>{/each}</select></div>
      {#if node.op !== 'is_empty' && node.op !== 'is_not_empty'}
        <div class='space-y-1'><Label>Value (JSON or text)</Label><Input value={valueText} disabled={!editable} onblur={event => onChange({ ...node, value: parseValue(event.currentTarget.value) })} /></div>
      {/if}
    </div>
    <datalist id='permission-system-fields'>{#each permissionSystemFields as field (field)}<option value={field}></option>{/each}<option value='property.'></option></datalist>
  {:else if node.op === 'not'}
    <ConditionNodeEditor node={node.arg} {editable} onChange={arg => onChange({ op: 'not', arg })} />
  {:else}
    <div class='space-y-2'>
      {#each node.args as child, index (index)}
        <ConditionNodeEditor node={child} {editable} onChange={next => onChange({ ...node, args: node.args.map((item, itemIndex) => itemIndex === index ? next : item) })} onRemove={node.args.length > 1 ? () => onChange({ ...node, args: node.args.filter((_, itemIndex) => itemIndex !== index) }) : undefined} />
      {/each}
      {#if editable}<Button type='button' variant='outline' size='sm' onclick={() => onChange({ ...node, args: [...node.args, emptyPermissionPredicate()] })}>Add condition</Button>{/if}
    </div>
  {/if}
</div>
