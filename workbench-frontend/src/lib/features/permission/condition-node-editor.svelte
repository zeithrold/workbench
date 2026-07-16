<script lang='ts'>
  import type { PermissionCondition } from './permission-document.js'
  import type { PermissionFieldOption } from './permission-editor-model.js'
  import { m } from '$lib/paraglide/messages.js'
  import ConditionGroupEditor from './condition-group-editor.svelte'
  import ConditionNodeEditor from './condition-node-editor.svelte'
  import ConditionRuleEditor from './condition-rule-editor.svelte'

  const { node, fields, depth = 1, editable = true, onChange, onRemove }: {
    node: PermissionCondition
    fields: PermissionFieldOption[]
    depth?: number
    editable?: boolean
    onChange: (node: PermissionCondition) => void
    onRemove?: () => void
  } = $props()
</script>

{#if 'field' in node}
  <ConditionRuleEditor {node} {fields} {editable} {onChange} />
{:else if node.op === 'not'}
  <div class='rounded-md border-l-2 border-amber-500/50 bg-amber-500/5 p-3' data-node-id={node.uiId}><div class='mb-2 text-xs font-semibold uppercase text-amber-700'>{m.permission_not()}</div><ConditionNodeEditor node={node.arg} {fields} depth={depth + 1} {editable} onChange={arg => onChange({ ...node, arg })} {onRemove} /></div>
{:else}
  <ConditionGroupEditor {node} {fields} {depth} {editable} {onChange} {onRemove} />
{/if}
