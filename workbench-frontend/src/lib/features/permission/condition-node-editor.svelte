<script lang='ts'>
  import type { PermissionCondition } from './permission-document.js'
  import type { PermissionFieldOption } from './permission-editor-model.js'
  import { Button } from '$lib/components/ui/button'
  import * as ButtonGroup from '$lib/components/ui/button-group'
  import * as Tooltip from '$lib/components/ui/tooltip'
  import { m } from '$lib/paraglide/messages.js'
  import CopyIcon from '@lucide/svelte/icons/copy'
  import TrashIcon from '@lucide/svelte/icons/trash-2'
  import ConditionGroupEditor from './condition-group-editor.svelte'
  import ConditionNodeEditor from './condition-node-editor.svelte'
  import ConditionRuleEditor from './condition-rule-editor.svelte'

  const { node, fields, depth = 1, editable = true, forceGroupInline = false, onChange, onDuplicate, onRemove }: {
    node: PermissionCondition
    fields: PermissionFieldOption[]
    depth?: number
    editable?: boolean
    forceGroupInline?: boolean
    onChange: (node: PermissionCondition) => void
    onDuplicate?: () => void
    onRemove?: () => void
  } = $props()
</script>

{#if 'field' in node}
  <ConditionRuleEditor {node} {fields} {editable} {onChange} />
{:else if node.op === 'not'}
  <div class='min-w-0 rounded-md border border-amber-500/35 bg-amber-500/5' data-node-id={node.uiId} data-slot='permission-not-condition'>
    <div class='flex items-center justify-between gap-2 border-b border-amber-500/20 px-3 py-1.5'><span class='text-xs font-semibold uppercase text-amber-700'>{m.permission_not()}</span>{#if editable && (onDuplicate || onRemove)}<ButtonGroup.Root>{#if onDuplicate}<Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='ghost' size='icon-xs' aria-label={m.permission_duplicate_condition()} onclick={onDuplicate}><CopyIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_duplicate()}</Tooltip.Content></Tooltip.Root>{/if}{#if onRemove}<Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='ghost' size='icon-xs' aria-label={m.permission_delete_condition()} onclick={onRemove}><TrashIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_delete()}</Tooltip.Content></Tooltip.Root>{/if}</ButtonGroup.Root>{/if}</div>
    <div class='grid min-w-0 grid-cols-[1rem_minmax(0,1fr)] gap-2 p-3'>
      <div class='relative min-h-12' aria-hidden='true'><span class='absolute bottom-1/2 left-1/2 top-0 border-l border-amber-500/45'></span><span class='absolute left-1/2 top-1/2 w-1/2 border-t border-amber-500/45'></span></div>
      <div class={`min-w-0 ${'field' in node.arg ? 'rounded-md border bg-background p-3' : ''}`}><ConditionNodeEditor node={node.arg} {fields} depth={depth + 1} {editable} onChange={arg => onChange({ ...node, arg })} {onRemove} /></div>
    </div>
  </div>
{:else}
  <ConditionGroupEditor {node} {fields} {depth} {editable} forceInline={forceGroupInline} {onChange} {onDuplicate} {onRemove} />
{/if}
