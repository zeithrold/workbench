<script lang='ts'>
  import type { WorkItemDisplayField } from '$lib/entities/work-item/index.js'
  import type { DndEvent } from 'svelte-dnd-action'
  import type { WorkItemColumnDefinition } from './work-item-list-config.js'
  import * as Dialog from '$lib/components/ui/dialog/index.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Button, Input } from '$lib/shared/ui/index.js'
  import GripVerticalIcon from '@lucide/svelte/icons/grip-vertical'
  import Settings2Icon from '@lucide/svelte/icons/settings-2'
  import { alertToScreenReader, dragHandle, dragHandleZone } from 'svelte-dnd-action'
  import { fieldId, normalizeDisplayFields } from './work-item-list-config.js'

  interface ColumnItem {
    id: string
    label: string
    field: WorkItemDisplayField
    required: boolean
  }

  interface Props {
    fields: WorkItemDisplayField[]
    columns: readonly WorkItemColumnDefinition[]
    onConfirm: (fields: WorkItemDisplayField[]) => void
  }

  /* eslint-disable prefer-const -- Svelte runes require mutable bindings for reactive destructured props. */
  let { fields, columns, onConfirm }: Props = $props()
  /* eslint-enable prefer-const */
  let open = $state(false)
  let available = $state.raw<ColumnItem[]>([])
  let displayed = $state.raw<ColumnItem[]>([])
  let availableSearch = $state('')
  let displayedSearch = $state('')
  const visibleAvailable = $derived(filterItems(available, availableSearch))
  const visibleDisplayed = $derived(filterItems(displayed, displayedSearch))

  function openChanged(value: boolean) {
    open = value
    if (value)
      resetDraft()
  }

  function resetDraft() {
    const normalized = normalizeDisplayFields(fields, columns)
    const current = new Map(normalized.map(field => [fieldId(field.field), field]))
    displayed = normalized.map(field => toItem(field, columns.find(column => column.id === fieldId(field.field))!))
    available = columns.filter(column => !current.has(column.id)).map(column => toItem({ field: column.field }, column))
    availableSearch = ''
    displayedSearch = ''
  }

  function toItem(field: WorkItemDisplayField, column: WorkItemColumnDefinition): ColumnItem {
    return { id: column.id, label: column.label, field, required: Boolean(column.required) }
  }

  function filterItems(items: ColumnItem[], search: string): ColumnItem[] {
    const query = search.trim().toLocaleLowerCase()
    return query ? items.filter(item => item.label.toLocaleLowerCase().includes(query)) : items
  }

  function updateZone(zone: 'available' | 'displayed', event: CustomEvent<DndEvent<ColumnItem>>) {
    const incoming = event.detail.items
    const incomingIds = new Set(incoming.map(item => item.id))
    if (zone === 'available') {
      if (incoming.some(item => item.required))
        return
      displayed = displayed.filter(item => !incomingIds.has(item.id))
      available = mergeZone(available, incoming, availableSearch)
    }
    else {
      const required = displayed.filter(item => item.required && !incomingIds.has(item.id))
      available = available.filter(item => !incomingIds.has(item.id))
      displayed = mergeZone(displayed, [...incoming, ...required], displayedSearch)
    }
  }

  function mergeZone(current: ColumnItem[], incoming: ColumnItem[], search: string): ColumnItem[] {
    if (!search.trim())
      return incoming
    const visibleIds = new Set(filterItems(current, search).map(item => item.id))
    const retained = current.filter(item => !visibleIds.has(item.id) && !incoming.some(next => next.id === item.id))
    return [...incoming, ...retained]
  }

  function confirm() {
    const next = normalizeDisplayFields(displayed.map(item => item.field), columns)
    open = false
    onConfirm(next)
  }

  function keyboardMove(zone: 'available' | 'displayed', item: ColumnItem, event: KeyboardEvent) {
    const source = zone === 'available' ? available : displayed
    const index = source.findIndex(candidate => candidate.id === item.id)
    if (index < 0)
      return
    if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
      const target = index + (event.key === 'ArrowUp' ? -1 : 1)
      if (target < 0 || target >= source.length)
        return
      event.preventDefault()
      event.stopPropagation()
      const next = [...source]
      const moving = next[index]
      next[index] = next[target]
      next[target] = moving
      if (zone === 'available')
        available = next
      else
        displayed = next
      alertToScreenReader(`${item.label} moved to position ${target + 1}`)
      return
    }
    const movesToDisplayed = zone === 'available' && event.key === 'ArrowRight'
    const movesToAvailable = zone === 'displayed' && event.key === 'ArrowLeft' && !item.required
    if (!movesToDisplayed && !movesToAvailable)
      return
    event.preventDefault()
    event.stopPropagation()
    if (movesToDisplayed) {
      available = available.filter(candidate => candidate.id !== item.id)
      displayed = [...displayed, item]
      alertToScreenReader(`${item.label} moved to displayed columns`)
    }
    else {
      displayed = displayed.filter(candidate => candidate.id !== item.id)
      available = [...available, item]
      alertToScreenReader(`${item.label} moved to available columns`)
    }
  }
</script>

<Dialog.Root bind:open={open} onOpenChange={openChanged}>
  <Dialog.Trigger>
    {#snippet child({ props })}
      <Button {...props} variant='outline' size='sm'>
        <Settings2Icon class='size-3.5' />
        {m.work_item_list_columns()}
      </Button>
    {/snippet}
  </Dialog.Trigger>
  <Dialog.Content class='max-h-[calc(100dvh-2rem)] overflow-y-auto sm:max-w-3xl'>
    <Dialog.Header>
      <Dialog.Title>{m.work_item_columns_title()}</Dialog.Title>
      <Dialog.Description>{m.work_item_columns_description()}</Dialog.Description>
    </Dialog.Header>
    <div class='grid min-h-96 gap-4 sm:grid-cols-2'>
      <section class='flex min-h-0 flex-col gap-3 rounded-lg border p-3' aria-label={m.work_item_columns_available()}>
        <div class='font-medium'>{m.work_item_columns_available()}</div>
        <Input bind:value={availableSearch} aria-label={m.work_item_columns_search_available()} placeholder={m.search()} />
        <div
          class='min-h-64 flex-1 space-y-2 rounded-md bg-muted/30 p-2'
          use:dragHandleZone={{ items: visibleAvailable, type: 'work-item-columns', flipDurationMs: 120, delayTouchStart: 80 }}
          onconsider={event => updateZone('available', event)}
          onfinalize={event => updateZone('available', event)}
        >
          {#each visibleAvailable as item (item.id)}
            <div class='flex items-center gap-2 rounded-md border bg-background px-3 py-2 shadow-sm' data-column-id={item.id}>
              <button use:dragHandle type='button' class='grid size-10 touch-none place-items-center rounded hover:bg-muted sm:size-7' aria-label={m.work_item_columns_drag({ column: item.label })} onkeydown={event => keyboardMove('available', item, event)}>
                <GripVerticalIcon class='size-4' />
              </button>
              <span class='truncate'>{item.label}</span>
            </div>
          {/each}
        </div>
      </section>
      <section class='flex min-h-0 flex-col gap-3 rounded-lg border p-3' aria-label={m.work_item_columns_displayed()}>
        <div class='font-medium'>{m.work_item_columns_displayed()}</div>
        <Input bind:value={displayedSearch} aria-label={m.work_item_columns_search_displayed()} placeholder={m.search()} />
        <div
          class='min-h-64 flex-1 space-y-2 rounded-md bg-muted/30 p-2'
          use:dragHandleZone={{ items: visibleDisplayed, type: 'work-item-columns', flipDurationMs: 120, delayTouchStart: 80 }}
          onconsider={event => updateZone('displayed', event)}
          onfinalize={event => updateZone('displayed', event)}
        >
          {#each visibleDisplayed as item (item.id)}
            <div class='flex items-center gap-2 rounded-md border bg-background px-3 py-2 shadow-sm' data-column-id={item.id}>
              <button use:dragHandle type='button' class='grid size-10 touch-none place-items-center rounded hover:bg-muted sm:size-7' aria-label={m.work_item_columns_drag({ column: item.label })} onkeydown={event => keyboardMove('displayed', item, event)}>
                <GripVerticalIcon class='size-4' />
              </button>
              <span class='min-w-0 flex-1 truncate'>{item.label}</span>
              {#if item.required}<span class='text-xs text-muted-foreground'>{m.work_item_columns_required()}</span>{/if}
            </div>
          {/each}
        </div>
      </section>
    </div>
    <Dialog.Footer>
      <Button variant='outline' onclick={() => open = false}>{m.cancel()}</Button>
      <Button onclick={confirm}>{m.apply()}</Button>
    </Dialog.Footer>
  </Dialog.Content>
</Dialog.Root>
