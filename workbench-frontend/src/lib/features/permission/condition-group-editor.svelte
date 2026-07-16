<script lang='ts'>
  import type { PermissionCondition } from './permission-document.js'
  import type { PermissionFieldOption } from './permission-editor-model.js'
  import * as AlertDialog from '$lib/components/ui/alert-dialog'
  import { Button } from '$lib/components/ui/button'
  import * as ButtonGroup from '$lib/components/ui/button-group'
  import * as Collapsible from '$lib/components/ui/collapsible'
  import * as Select from '$lib/components/ui/select'
  import * as Tooltip from '$lib/components/ui/tooltip'
  import { m } from '$lib/paraglide/messages.js'
  import { Badge } from '$lib/shared/ui'
  import ChevronDownIcon from '@lucide/svelte/icons/chevron-down'
  import CopyIcon from '@lucide/svelte/icons/copy'
  import PlusIcon from '@lucide/svelte/icons/plus'
  import TrashIcon from '@lucide/svelte/icons/trash-2'
  import ConditionNodeEditor from './condition-node-editor.svelte'
  import { clonePermissionCondition, emptyPermissionPredicate } from './permission-document.js'
  import { permissionConditionErrorCount } from './permission-editor-model.js'

  const { node, fields, depth = 1, editable = true, onChange, onRemove }: {
    node: Extract<PermissionCondition, { args: unknown }>
    fields: PermissionFieldOption[]
    depth?: number
    editable?: boolean
    onChange: (node: PermissionCondition) => void
    onRemove?: () => void
  } = $props()
  let open = $state(true)
  let confirmDelete = $state(false)
  const errorCount = $derived(permissionConditionErrorCount(node, fields))
  const matchItems = [
    { value: 'and', label: m.permission_all_conditions() },
    { value: 'or', label: m.permission_any_condition() },
  ] as const
</script>

<Collapsible.Root bind:open class='rounded-lg border-l-2 border-primary/35 bg-muted/20' data-node-id={node.uiId}>
  <div class='flex flex-wrap items-center justify-between gap-2 border-b border-border/60 px-3 py-2'>
    <div class='flex items-center gap-2'>
      <Collapsible.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='ghost' size='icon-xs' aria-label={open ? m.permission_collapse_group() : m.permission_expand_group()}><ChevronDownIcon class={open ? 'size-3.5' : 'size-3.5 -rotate-90'} /></Button>{/snippet}</Collapsible.Trigger>
      <span class='text-sm font-medium'>{m.permission_match()}</span>
      <Select.Root type='single' items={[...matchItems]} value={node.op} disabled={!editable} onValueChange={value => value && onChange({ ...node, op: value as 'and' | 'or' })}>
        <Select.Trigger size='sm' class='w-36' indicatorClass='size-3'><Select.Value placeholder={m.permission_match()} /></Select.Trigger>
        <Select.Content><Select.Item value='and' label={m.permission_all_conditions()} indicatorClass='size-3'>{m.permission_all_conditions()}</Select.Item><Select.Item value='or' label={m.permission_any_condition()} indicatorClass='size-3'>{m.permission_any_condition()}</Select.Item></Select.Content>
      </Select.Root>
      <span class='text-xs text-muted-foreground'>{m.permission_group_items({ count: node.args.length })}</span>
      {#if errorCount > 0}<Badge variant='destructive'>{m.permission_error_count({ count: errorCount })}</Badge>{/if}
    </div>
    {#if editable}
      <ButtonGroup.Root>
        <Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='outline' size='icon-xs' aria-label={m.permission_add_condition()} onclick={() => onChange({ ...node, args: [...node.args, emptyPermissionPredicate()] })}><PlusIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_add_condition()}</Tooltip.Content></Tooltip.Root>
        {#if depth < 3}<Button type='button' variant='outline' size='xs' onclick={() => onChange({ ...node, args: [...node.args, { op: 'and', args: [emptyPermissionPredicate()] }] })}>{m.permission_add_group()}</Button>{/if}
        {#if onRemove}<Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='outline' size='icon-xs' aria-label={m.permission_delete_group()} onclick={() => node.args.length > 1 ? confirmDelete = true : onRemove()}><TrashIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_delete_group()}</Tooltip.Content></Tooltip.Root>{/if}
      </ButtonGroup.Root>
    {/if}
  </div>
  <Collapsible.Content class='space-y-0 p-3'>
    {#each node.args as condition, index (condition.uiId ?? index)}
      {#if index > 0}<div class='flex h-8 items-center'><span class='rounded-full border bg-background px-2 py-0.5 text-[11px] font-semibold uppercase text-muted-foreground'>{node.op === 'and' ? m.permission_and() : m.permission_or()}</span></div>{/if}
      <div class='rounded-md bg-background/80 p-3'>
        <div class='flex items-start gap-2'>
          <div class='min-w-0 flex-1'><ConditionNodeEditor node={condition} {fields} depth={depth + 1} {editable} onChange={next => onChange({ ...node, args: node.args.map((item, itemIndex) => itemIndex === index ? next : item) })} onRemove={node.args.length > 1 ? () => onChange({ ...node, args: node.args.filter((_, itemIndex) => itemIndex !== index) }) : undefined} /></div>
          {#if editable}<ButtonGroup.Root><Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='ghost' size='icon-xs' aria-label={m.permission_duplicate_condition()} onclick={() => onChange({ ...node, args: [...node.args.slice(0, index + 1), clonePermissionCondition(condition), ...node.args.slice(index + 1)] })}><CopyIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_duplicate()}</Tooltip.Content></Tooltip.Root>{#if node.args.length > 1}<Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='ghost' size='icon-xs' aria-label={m.permission_delete_condition()} onclick={() => onChange({ ...node, args: node.args.filter((_, itemIndex) => itemIndex !== index) })}><TrashIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_delete()}</Tooltip.Content></Tooltip.Root>{/if}</ButtonGroup.Root>{/if}
        </div>
      </div>
    {/each}
  </Collapsible.Content>
</Collapsible.Root>

<AlertDialog.Root bind:open={confirmDelete}>
  <AlertDialog.Content><AlertDialog.Header><AlertDialog.Title>{m.permission_delete_group_title()}</AlertDialog.Title><AlertDialog.Description>{m.permission_delete_group_description({ count: node.args.length })}</AlertDialog.Description></AlertDialog.Header><AlertDialog.Footer><AlertDialog.Cancel>{m.cancel()}</AlertDialog.Cancel><AlertDialog.Action onclick={onRemove}>{m.permission_delete_group()}</AlertDialog.Action></AlertDialog.Footer></AlertDialog.Content>
</AlertDialog.Root>
