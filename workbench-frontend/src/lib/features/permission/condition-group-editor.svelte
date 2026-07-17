<script lang='ts'>
  import type { PermissionCondition } from './permission-document.js'
  import type { PermissionFieldOption } from './permission-editor-model.js'
  import * as AlertDialog from '$lib/components/ui/alert-dialog'
  import { Button } from '$lib/components/ui/button'
  import * as ButtonGroup from '$lib/components/ui/button-group'
  import * as Collapsible from '$lib/components/ui/collapsible'
  import * as Drawer from '$lib/components/ui/drawer'
  import * as Select from '$lib/components/ui/select'
  import * as Tooltip from '$lib/components/ui/tooltip'
  import { m } from '$lib/paraglide/messages.js'
  import { Badge } from '$lib/shared/ui'
  import ChevronDownIcon from '@lucide/svelte/icons/chevron-down'
  import CopyIcon from '@lucide/svelte/icons/copy'
  import PlusIcon from '@lucide/svelte/icons/plus'
  import TrashIcon from '@lucide/svelte/icons/trash-2'
  import { MediaQuery } from 'svelte/reactivity'
  import ConditionNodeEditor from './condition-node-editor.svelte'
  import { clonePermissionCondition, emptyPermissionPredicate } from './permission-document.js'
  import { permissionConditionErrorCount } from './permission-editor-model.js'

  const { node, fields, depth = 1, editable = true, forceInline = false, onChange, onDuplicate, onRemove }: {
    node: Extract<PermissionCondition, { args: unknown }>
    fields: PermissionFieldOption[]
    depth?: number
    editable?: boolean
    forceInline?: boolean
    onChange: (node: PermissionCondition) => void
    onDuplicate?: () => void
    onRemove?: () => void
  } = $props()
  let open = $state(true)
  let drawerOpen = $state(false)
  let confirmDelete = $state(false)
  const mobile = new MediaQuery('(max-width: 639px)', false)
  const errorCount = $derived(permissionConditionErrorCount(node, fields))
  const matchItems = [
    { value: 'and', label: m.permission_all_conditions() },
    { value: 'or', label: m.permission_any_condition() },
  ] as const
</script>

{#snippet drawerBody()}
  <Drawer.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='outline' class='h-auto w-full min-w-0 justify-between gap-3 px-3 py-3 text-left' aria-label={m.permission_edit_nested_group()}><span class='min-w-0'><span class='block truncate font-medium'>{m.permission_nested_group_title({ mode: node.op === 'and' ? m.permission_all_conditions() : m.permission_any_condition() })}</span><span class='block text-xs font-normal text-muted-foreground'>{m.permission_group_items({ count: node.args.length })}</span></span>{#if errorCount > 0}<Badge variant='destructive'>{m.permission_error_count({ count: errorCount })}</Badge>{/if}<ChevronDownIcon class='size-4 shrink-0 -rotate-90' /></Button>{/snippet}</Drawer.Trigger>
  <Drawer.Content class='max-h-[90vh]'>
    <Drawer.Header class='text-left'><Drawer.Title>{m.permission_nested_group_title({ mode: node.op === 'and' ? m.permission_all_conditions() : m.permission_any_condition() })}</Drawer.Title><Drawer.Description>{m.permission_nested_group_description()}</Drawer.Description></Drawer.Header>
    <div class='min-h-0 flex-1 overflow-y-auto px-4 pb-4'><ConditionNodeEditor {node} {fields} {depth} {editable} forceGroupInline onChange={onChange} {onDuplicate} {onRemove} /></div>
    <Drawer.Footer><Button type='button' onclick={() => drawerOpen = false}>{m.close()}</Button></Drawer.Footer>
  </Drawer.Content>
{/snippet}

{#if depth > 1 && mobile.current && !forceInline}
  {#if depth > 2}<Drawer.NestedRoot bind:open={drawerOpen} direction='bottom'>{@render drawerBody()}</Drawer.NestedRoot>{:else}<Drawer.Root bind:open={drawerOpen} direction='bottom'>{@render drawerBody()}</Drawer.Root>{/if}
{:else}
  <Collapsible.Root bind:open class='min-w-0 rounded-lg border bg-muted/15' data-node-id={node.uiId} data-slot='permission-condition-group' data-depth={depth}>
    <div class={depth === 1 ? 'flex flex-wrap items-center justify-between gap-2 border-b border-border/60 px-3 py-2' : 'flex min-w-0 flex-wrap items-center justify-between gap-2 border-b border-border/60 px-2 py-2'}>
      <div class='flex min-w-0 flex-wrap items-center gap-2'>
        <Collapsible.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='ghost' size='icon-xs' aria-label={open ? m.permission_collapse_group() : m.permission_expand_group()}><ChevronDownIcon class={open ? 'size-3.5' : 'size-3.5 -rotate-90'} /></Button>{/snippet}</Collapsible.Trigger>
        <span class='text-sm font-medium'>{m.permission_match()}</span>
        <Select.Root type='single' items={[...matchItems]} value={node.op} disabled={!editable} onValueChange={value => value && onChange({ ...node, op: value as 'and' | 'or' })}>
          <Select.Trigger size='sm' class={depth === 1 ? 'w-28 sm:w-36' : 'min-w-0 flex-1'} indicatorClass='size-3'><Select.Value placeholder={m.permission_match()} /></Select.Trigger>
          <Select.Content><Select.Item value='and' label={m.permission_all_conditions()} indicatorClass='size-3'>{m.permission_all_conditions()}</Select.Item><Select.Item value='or' label={m.permission_any_condition()} indicatorClass='size-3'>{m.permission_any_condition()}</Select.Item></Select.Content>
        </Select.Root>
        <span class='text-xs text-muted-foreground'>{m.permission_group_items({ count: node.args.length })}</span>
        {#if errorCount > 0}<Badge variant='destructive'>{m.permission_error_count({ count: errorCount })}</Badge>{/if}
      </div>
      {#if editable}
        <ButtonGroup.Root>
          <Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='outline' size='icon-xs' aria-label={m.permission_add_condition()} onclick={() => onChange({ ...node, args: [...node.args, emptyPermissionPredicate()] })}><PlusIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_add_condition()}</Tooltip.Content></Tooltip.Root>
          {#if depth < 3}<Button type='button' variant='outline' size='xs' onclick={() => onChange({ ...node, args: [...node.args, { op: 'and', args: [emptyPermissionPredicate()] }] })}>{m.permission_add_group()}</Button>{/if}
          {#if onDuplicate}<Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='outline' size='icon-xs' aria-label={m.permission_duplicate_condition()} onclick={onDuplicate}><CopyIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_duplicate()}</Tooltip.Content></Tooltip.Root>{/if}
          {#if onRemove}<Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='outline' size='icon-xs' aria-label={m.permission_delete_group()} onclick={() => node.args.length > 1 ? confirmDelete = true : onRemove()}><TrashIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_delete_group()}</Tooltip.Content></Tooltip.Root>{/if}
        </ButtonGroup.Root>
      {/if}
    </div>
    <Collapsible.Content class={depth === 1 ? 'space-y-2 px-3 py-3' : 'space-y-2 py-2'} data-slot='permission-logic-tree'>
      {#each node.args as condition, index (condition.uiId ?? index)}
        <div class='grid min-w-0 grid-cols-[1rem_minmax(0,1fr)] items-stretch gap-2' data-slot='permission-logic-tree-item'>
          <div class='relative min-h-12' aria-hidden='true' data-slot='permission-logic-rail'>
            <span class={index === 0 ? 'absolute bottom-0 left-1/2 top-1/2 border-l border-primary/35' : index === node.args.length - 1 ? 'absolute bottom-1/2 left-1/2 top-0 border-l border-primary/35' : 'absolute inset-y-0 left-1/2 border-l border-primary/35'}></span>
            <span class='absolute left-1/2 top-1/2 w-1/2 border-t border-primary/35'></span>
            {#if index > 0}<span class='absolute left-1/2 top-1/2 z-10 -translate-x-1/2 -translate-y-1/2 rounded-full border bg-background px-1 py-0.5 text-[9px] font-semibold uppercase leading-none text-muted-foreground'>{node.op === 'and' ? m.permission_and() : m.permission_or()}</span>{/if}
          </div>
          <div class={`min-w-0 ${'field' in condition ? 'rounded-md border bg-background p-3' : ''}`} data-slot='permission-logic-node'>
            {#if 'field' in condition}
              <ConditionNodeEditor node={condition} {fields} depth={depth + 1} {editable} onChange={next => onChange({ ...node, args: node.args.map((item, itemIndex) => itemIndex === index ? next : item) })} />
              {#if editable}<ButtonGroup.Root class='ml-auto mt-2'><Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='ghost' size='icon-xs' aria-label={m.permission_duplicate_condition()} onclick={() => onChange({ ...node, args: [...node.args.slice(0, index + 1), clonePermissionCondition(condition), ...node.args.slice(index + 1)] })}><CopyIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_duplicate()}</Tooltip.Content></Tooltip.Root>{#if node.args.length > 1}<Tooltip.Root><Tooltip.Trigger>{#snippet child({ props })}<Button {...props} type='button' variant='ghost' size='icon-xs' aria-label={m.permission_delete_condition()} onclick={() => onChange({ ...node, args: node.args.filter((_, itemIndex) => itemIndex !== index) })}><TrashIcon class='size-3.5' /></Button>{/snippet}</Tooltip.Trigger><Tooltip.Content>{m.permission_delete()}</Tooltip.Content></Tooltip.Root>{/if}</ButtonGroup.Root>{/if}
            {:else}
              <ConditionNodeEditor node={condition} {fields} depth={depth + 1} {editable} onChange={next => onChange({ ...node, args: node.args.map((item, itemIndex) => itemIndex === index ? next : item) })} onDuplicate={() => onChange({ ...node, args: [...node.args.slice(0, index + 1), clonePermissionCondition(condition), ...node.args.slice(index + 1)] })} onRemove={node.args.length > 1 ? () => onChange({ ...node, args: node.args.filter((_, itemIndex) => itemIndex !== index) }) : undefined} />
            {/if}
          </div>
        </div>
      {/each}
    </Collapsible.Content>
  </Collapsible.Root>

  <AlertDialog.Root bind:open={confirmDelete}>
    <AlertDialog.Content><AlertDialog.Header><AlertDialog.Title>{m.permission_delete_group_title()}</AlertDialog.Title><AlertDialog.Description>{m.permission_delete_group_description({ count: node.args.length })}</AlertDialog.Description></AlertDialog.Header><AlertDialog.Footer><AlertDialog.Cancel>{m.cancel()}</AlertDialog.Cancel><AlertDialog.Action onclick={onRemove}>{m.permission_delete_group()}</AlertDialog.Action></AlertDialog.Footer></AlertDialog.Content>
  </AlertDialog.Root>
{/if}
