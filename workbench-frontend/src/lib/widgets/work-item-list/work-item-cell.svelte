<script lang='ts'>
  import type { WorkItemFieldOption, WorkItemListItem, WorkItemTransitionOption } from '$lib/entities/work-item/index.js'
  import * as Popover from '$lib/components/ui/popover/index.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Badge, Button, Input } from '$lib/shared/ui/index.js'
  import CheckIcon from '@lucide/svelte/icons/check'
  import LoaderCircleIcon from '@lucide/svelte/icons/loader-circle'
  import { SvelteSet } from 'svelte/reactivity'

  interface Props {
    item: WorkItemListItem
    fieldId: string
    dataType?: string
    pending?: boolean
    error?: string | null
    loadOptions?: (item: WorkItemListItem, fieldId: string, query?: string) => Promise<WorkItemFieldOption[]>
    loadTransitions?: (item: WorkItemListItem) => Promise<WorkItemTransitionOption[]>
    onValueChange?: (item: WorkItemListItem, fieldId: string, value: unknown) => Promise<void> | void
    onTransition?: (item: WorkItemListItem, transitionId: string) => Promise<void> | void
    open?: boolean
  }

  let {
    item,
    fieldId,
    dataType,
    pending = false,
    error = null,
    loadOptions,
    loadTransitions,
    onValueChange,
    onTransition,
    open = $bindable(false),
  }: Props = $props()
  let loading = $state(false)
  let options = $state.raw<WorkItemFieldOption[]>([])
  let transitions = $state.raw<WorkItemTransitionOption[]>([])
  let search = $state('')
  let draft = $state('')
  let selectedIds = $state.raw<SvelteSet<string>>(new SvelteSet())
  let loadedOpen = $state(false)

  const capability = $derived(item.fieldCapabilities[fieldId])
  const propertyCode = $derived(fieldId.startsWith('property.') ? fieldId.slice('property.'.length) : null)
  const property = $derived(propertyCode ? item.properties[propertyCode] : null)
  const propertyType = $derived(property?.property.dataType ?? dataType)
  const supported = $derived(isSupportedEditor(fieldId, propertyType))
  const editable = $derived(capability?.state === 'EDITABLE' && supported && !pending)
  const disabledReason = $derived(error ?? capability?.reason ?? (!supported ? 'compact_editor_unavailable' : null))

  $effect(() => {
    if (open && !loadedOpen) {
      loadedOpen = true
      void load()
    }
    if (!open) {
      loadedOpen = false
      search = ''
    }
  })

  async function load() {
    loading = true
    try {
      if (fieldId === 'status')
        transitions = await loadTransitions?.(item) ?? []
      else if (usesOptions(fieldId, propertyType))
        options = await loadOptions?.(item, fieldId, search) ?? []
      else
        draft = scalarValue()
      if (isMultiSelect()) {
        const current = Array.isArray(property?.value) ? property.value : []
        selectedIds = new SvelteSet(current.map(value => typeof value === 'object' && value !== null && 'id' in value ? String(value.id) : String(value)))
      }
    }
    finally {
      loading = false
    }
  }

  function toggleOption(option: WorkItemFieldOption) {
    if (!isMultiSelect()) {
      void select(option)
      return
    }
    const next = new SvelteSet(selectedIds)
    if (next.has(option.id))
      next.delete(option.id)
    else
      next.add(option.id)
    selectedIds = next
  }

  function isMultiSelect(): boolean {
    return propertyType === 'multi_select' || propertyType === 'multi_user'
  }

  async function select(value: unknown) {
    try {
      await onValueChange?.(item, fieldId, value)
      open = false
    }
    catch {
    // The parent restores cached data and exposes the RFC 7807 detail on the cell.
    }
  }

  async function transition(transitionId: string) {
    try {
      await onTransition?.(item, transitionId)
      open = false
    }
    catch {
    // Keep the transition menu open so the user can retry after a failed mutation.
    }
  }

  function scalarValue(): string {
    const value = property?.value
    return value === null || value === undefined ? '' : String(value)
  }

  function applyScalar() {
    let value: unknown = draft
    if (propertyType === 'number')
      value = draft === '' ? null : Number(draft)
    else if (propertyType === 'boolean')
      value = draft === 'true'
    void select(value)
  }

  function usesOptions(id: string, type?: string): boolean {
    return ['assignee', 'priority', 'sprint'].includes(id)
      || ['single_select', 'multi_select', 'user', 'multi_user', 'project', 'issue'].includes(type ?? '')
  }

  function isSupportedEditor(id: string, type?: string): boolean {
    if (['status', 'assignee', 'priority', 'sprint'].includes(id))
      return true
    return ['single_select', 'multi_select', 'user', 'multi_user', 'project', 'issue', 'text', 'number', 'boolean', 'date', 'datetime', 'url'].includes(type ?? '')
  }

  function displayValues(): string[] {
    const value = property?.displayValue ?? property?.value
    if (Array.isArray(value))
      return value.map(item => typeof item === 'object' && item !== null && 'label' in item ? String(item.label) : String(item))
    if (value === null || value === undefined || value === '')
      return []
    if (typeof value === 'object')
      return [JSON.stringify(value)]
    return [String(value)]
  }

  function initials(name: string): string {
    return name.split(/\s+/).slice(0, 2).map(part => part[0]?.toUpperCase()).join('')
  }
</script>

{#snippet valueContent()}
  {#if pending}
    <LoaderCircleIcon class='size-3.5 animate-spin text-muted-foreground' />
  {/if}
  {#if fieldId === 'key'}
    <span class='font-mono text-xs'>{item.key}</span>
  {:else if fieldId === 'title'}
    <span class='truncate font-medium'>{item.title}</span>
  {:else if fieldId === 'issueType'}
    <Badge variant='outline'><span class='size-2 rounded-full' style:background-color={item.issueType.color ?? undefined}></span>{item.issueType.name}</Badge>
  {:else if fieldId === 'status'}
    <Badge variant='secondary'><span class='size-2 rounded-full' style:background-color={item.status.color ?? undefined}></span>{item.status.name}</Badge>
  {:else if fieldId === 'priority'}
    {#if item.priority}<span class='inline-flex items-center gap-1.5'><span class='size-2 rounded-full' style:background-color={item.priority.color ?? undefined}></span>{item.priority.name}</span>{:else}<span class='text-muted-foreground'>{m.work_item_list_no_value()}</span>{/if}
  {:else if fieldId === 'assignee'}
    {#if item.assignee}<span class='inline-flex items-center gap-2'><span class='grid size-6 rounded-full bg-muted text-[10px] font-medium place-items-center'>{initials(item.assignee.displayName)}</span>{item.assignee.displayName}</span>{:else}<span class='text-muted-foreground'>{m.work_item_list_no_value()}</span>{/if}
  {:else if fieldId === 'sprint'}
    {#if item.sprint}<span class='inline-flex items-center gap-2'><span>{item.sprint.name}</span><Badge variant='outline'>{item.sprint.status}</Badge></span>{:else}<span class='text-muted-foreground'>{m.backlog()}</span>{/if}
  {:else}
    {@const values = displayValues()}
    {#if values.length === 0}
      <span class='text-muted-foreground'>{m.work_item_list_no_value()}</span>
    {:else if propertyType === 'boolean'}
      <span class='inline-flex items-center gap-1.5'>{#if values[0] === 'true'}<CheckIcon class='size-3.5 text-primary' />{/if}{values[0] === 'true' ? m.yes() : m.no()}</span>
    {:else}
      <span class='inline-flex max-w-full items-center gap-1'>{#each values.slice(0, 2) as value (value)}<Badge variant='outline' class='max-w-36 truncate'>{value}</Badge>{/each}{#if values.length > 2}<span class='text-xs text-muted-foreground'>+{values.length - 2}</span>{/if}</span>
    {/if}
  {/if}
{/snippet}

{#if editable}
  <div role='presentation' class='contents' onclick={event => event.stopPropagation()} onkeydown={event => event.stopPropagation()}>
    <Popover.Root bind:open>
      <Popover.Trigger>
        {#snippet child({ props })}
          <button
            {...props}
            class='flex min-h-8 w-full items-center gap-1.5 rounded px-1 text-left hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring'
            type='button'
            title={error ?? undefined}
          >{@render valueContent()}</button>
        {/snippet}
      </Popover.Trigger>
      <Popover.Content class='w-72 p-2' onclick={event => event.stopPropagation()} onkeydown={event => event.stopPropagation()}>
        {#if loading}
          <div class='flex items-center gap-2 p-3 text-sm text-muted-foreground'><LoaderCircleIcon class='size-4 animate-spin' />{m.loading()}</div>
        {:else if fieldId === 'status'}
          <div class='space-y-1'>{#each transitions as option (option.id)}<button class='flex w-full items-center gap-2 rounded px-2 py-2 text-left hover:bg-muted disabled:opacity-50' type='button' disabled={!option.enabled} title={option.reason ?? undefined} onclick={() => transition(option.id)}><span class='size-2 rounded-full' style:background-color={option.targetStatus?.color ?? undefined}></span>{option.targetStatus?.name ?? option.name}</button>{/each}</div>
        {:else if usesOptions(fieldId, propertyType)}
          <Input bind:value={search} placeholder={m.search()} onkeydown={event => event.key === 'Enter' && load()} />
          <div class='mt-2 max-h-64 space-y-1 overflow-auto'>
            {#if fieldId === 'sprint'}<button class='w-full rounded px-2 py-2 text-left hover:bg-muted' type='button' onclick={() => select(null)}>{m.backlog()}</button>{/if}
            {#each options as option (option.id)}<button class='flex w-full items-center gap-2 rounded px-2 py-2 text-left hover:bg-muted' type='button' onclick={() => toggleOption(option)}>{#if isMultiSelect()}<span class='grid size-4 place-items-center rounded border'>{#if selectedIds.has(option.id)}<CheckIcon class='size-3' />{/if}</span>{:else}<span class='size-2 rounded-full' style:background-color={option.color ?? undefined}></span>{/if}<span class='min-w-0 flex-1 truncate'>{option.label}</span>{#if option.status}<Badge variant='outline'>{option.status}</Badge>{/if}</button>{/each}
          </div>
          {#if isMultiSelect()}<div class='mt-3 flex justify-end'><Button size='sm' onclick={() => select([...selectedIds])}>{m.apply()}</Button></div>{/if}
        {:else}
          {#if propertyType === 'boolean'}
            <label class='flex items-center gap-2 p-2'><input type='checkbox' checked={draft === 'true'} onchange={event => draft = String(event.currentTarget.checked)} />{property?.property.name}</label>
          {:else}
            <Input bind:value={draft} type={propertyType === 'number' ? 'number' : propertyType === 'date' ? 'date' : propertyType === 'datetime' ? 'datetime-local' : propertyType === 'url' ? 'url' : 'text'} />
          {/if}
          <div class='mt-3 flex justify-end'><Button size='sm' onclick={applyScalar}>{m.apply()}</Button></div>
        {/if}
      </Popover.Content>
    </Popover.Root>
  </div>
{:else}
  <span class='flex min-h-8 items-center gap-1.5 px-1' aria-disabled={capability?.state !== 'EDITABLE' || pending} title={disabledReason ?? undefined}>{@render valueContent()}</span>
{/if}
