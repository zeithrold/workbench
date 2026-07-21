<script lang='ts'>
  import type { WorkItemDisplayField, WorkItemFieldOption, WorkItemListItem, WorkItemTransitionOption } from '$lib/entities/work-item/index.js'
  import type { ColumnDef, ColumnSizingState, TableState, Updater } from '@tanstack/table-core'
  import type { WorkItemColumnDefinition } from './work-item-list-config.js'
  import { m } from '$lib/paraglide/messages.js'
  import { Button, Skeleton } from '$lib/shared/ui/index.js'
  import { createTable, functionalUpdate, getCoreRowModel } from '@tanstack/table-core'
  import { untrack } from 'svelte'
  import WorkItemCell from './work-item-cell.svelte'
  import WorkItemColumnManager from './work-item-column-manager.svelte'
  import {
    DEFAULT_WORK_ITEM_DISPLAY_FIELDS,
    fieldId,
    normalizeDisplayFields,
    WORK_ITEM_COLUMNS,
  } from './work-item-list-config.js'

  interface Props {
    items?: WorkItemListItem[]
    displayFields?: WorkItemDisplayField[]
    onDisplayFieldsChange?: (fields: WorkItemDisplayField[]) => void
    selectedIds?: string[]
    onSelectionChange?: (ids: string[]) => void
    onRowOpen?: (item: WorkItemListItem) => void
    loading?: boolean
    error?: Error | null
    onRetry?: () => void
    hasNextPage?: boolean
    fetchingNextPage?: boolean
    onLoadMore?: () => void
    columns?: readonly WorkItemColumnDefinition[]
    pendingCells?: ReadonlySet<string>
    cellErrors?: ReadonlyMap<string, string>
    loadOptions?: (item: WorkItemListItem, fieldId: string, query?: string) => Promise<WorkItemFieldOption[]>
    loadTransitions?: (item: WorkItemListItem) => Promise<WorkItemTransitionOption[]>
    onValueChange?: (item: WorkItemListItem, fieldId: string, value: unknown) => Promise<void> | void
    onTransition?: (item: WorkItemListItem, transitionId: string) => Promise<void> | void
  }

  /* eslint-disable prefer-const -- Svelte runes require mutable bindings for reactive destructured props. */
  let {
    items = [],
    displayFields = DEFAULT_WORK_ITEM_DISPLAY_FIELDS,
    onDisplayFieldsChange,
    selectedIds = [],
    onSelectionChange,
    onRowOpen,
    loading = false,
    error = null,
    onRetry,
    hasNextPage = false,
    fetchingNextPage = false,
    onLoadMore,
    columns = WORK_ITEM_COLUMNS,
    pendingCells = new Set<string>(),
    cellErrors = new Map<string, string>(),
    loadOptions,
    loadTransitions,
    onValueChange,
    onTransition,
  }: Props = $props()
  /* eslint-enable prefer-const */

  const dateFormatter = new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })

  const columnDefs = $derived<ColumnDef<WorkItemListItem>[]>(columns.map(column => ({
    id: column.id,
    size: column.defaultWidth,
    minSize: column.minWidth,
    maxSize: column.maxWidth,
  })))
  const table = createTable<WorkItemListItem>({
    data: [],
    columns: [],
    getCoreRowModel: getCoreRowModel(),
    getRowId: (item: WorkItemListItem) => item.id,
    columnResizeMode: 'onEnd',
    state: {},
    onStateChange: () => undefined,
    renderFallbackValue: null,
  })
  let tableState = $state.raw<TableState>(table.initialState)
  let tableVersion = $state(0)
  let internalDisplayFields = $state.raw<WorkItemDisplayField[]>(untrack(() => displayFields))
  let lastExternalDisplayFields = $state.raw<WorkItemDisplayField[]>(untrack(() => displayFields))
  table.setOptions(previous => ({ ...previous, state: tableState }))
  const normalizedFields = $derived(normalizeDisplayFields(internalDisplayFields, columns))
  const visibleIds = $derived(normalizedFields.map(field => fieldId(field.field)).filter((id): id is string => Boolean(id)))
  const columnSizing = $derived<ColumnSizingState>({
    ...Object.fromEntries(normalizedFields.map(field => [fieldId(field.field), field.width ?? 0])),
    ...tableState.columnSizing,
  })
  const loadedIds = $derived(items.map(item => item.id))
  const allLoadedSelected = $derived(loadedIds.length > 0 && loadedIds.every(id => selectedIds.includes(id)))
  const someLoadedSelected = $derived(!allLoadedSelected && loadedIds.some(id => selectedIds.includes(id)))

  $effect(() => {
    if (displayFields !== lastExternalDisplayFields) {
      internalDisplayFields = displayFields
      lastExternalDisplayFields = displayFields
    }
  })

  $effect(() => {
    const columnVisibility = Object.fromEntries(columns.map(column => [column.id, visibleIds.includes(column.id)]))
    table.setOptions(previous => ({
      ...previous,
      data: items,
      columns: columnDefs,
      getCoreRowModel: getCoreRowModel(),
      getRowId: (item: WorkItemListItem) => item.id,
      columnResizeMode: 'onEnd',
      state: {
        ...tableState,
        columnSizing,
        columnOrder: visibleIds,
        columnVisibility,
      },
      onStateChange: (updater: Updater<TableState>) => {
        tableState = functionalUpdate(updater, tableState)
      },
      onColumnSizingChange: (updater: Updater<ColumnSizingState>) => {
        const columnSizing = functionalUpdate(updater, tableState.columnSizing)
        tableState = { ...tableState, columnSizing }
        const next = normalizedFields.map(field => ({
          ...field,
          width: columnSizing[fieldId(field.field) ?? ''] ?? field.width,
        }))
        emitFields(normalizeDisplayFields(next))
      },
    }))
    tableVersion = untrack(() => tableVersion) + 1
  })

  function emitFields(fields: WorkItemDisplayField[]) {
    internalDisplayFields = fields
    onDisplayFieldsChange?.(fields)
  }

  function toggleLoadedSelection(checked: boolean) {
    const retained = selectedIds.filter(id => !loadedIds.includes(id))
    onSelectionChange?.(checked ? [...retained, ...loadedIds] : retained)
  }

  function toggleRow(id: string, checked: boolean) {
    onSelectionChange?.(checked ? [...selectedIds, id] : selectedIds.filter(selectedId => selectedId !== id))
  }

  function stickyLeft(id: string): number | null {
    const current = normalizedFields.find(field => fieldId(field.field) === id)
    if (!current?.pinned)
      return null
    let left = onSelectionChange ? 40 : 0
    for (const field of normalizedFields) {
      const currentId = fieldId(field.field)
      if (currentId === id)
        return left
      if (field.pinned)
        left += columnSizing[currentId ?? ''] ?? field.width ?? 0
    }
    return left
  }

  function label(id: string): string {
    return columns.find(column => column.id === id)?.label ?? id
  }
</script>

<!-- eslint-disable zeithrold/no-untranslated-literal -- Dynamic inline styles contain CSS pixel units; visible labels use Paraglide. -->
<section class='min-w-0 rounded-lg border bg-background' aria-label={m.work_item_list_title()}>
  <div class='flex items-center justify-end border-b p-2'>
    <WorkItemColumnManager fields={normalizedFields} {columns} onConfirm={emitFields} />
  </div>

  {#if loading}
    <div class='space-y-2 p-4' aria-label={m.work_item_list_loading()}>
      {#each Array(6) as _, index (index)}
        <Skeleton class='h-10 w-full' />
      {/each}
    </div>
  {:else if error}
    <div class='grid min-h-48 place-items-center p-6 text-center'>
      <div class='space-y-3'>
        <p class='font-medium text-destructive'>{m.work_item_list_load_failed()}</p>
        <p class='max-w-md text-sm text-muted-foreground'>{error.message}</p>
        {#if onRetry}<Button variant='outline' onclick={onRetry}>{m.work_item_list_retry()}</Button>{/if}
      </div>
    </div>
  {:else if items.length === 0}
    <div class='grid min-h-48 place-items-center p-6 text-sm text-muted-foreground'>{m.work_item_list_empty()}</div>
  {:else}
    <div class='max-w-full overflow-auto' data-work-item-scroll-container>
      <table class='w-max min-w-full table-fixed border-collapse text-sm' data-table-version={tableVersion}>
        <thead class='bg-muted/60'>
          {#each table.getHeaderGroups() as headerGroup (headerGroup.id)}
            <tr>
              {#if onSelectionChange}
                <th class='sticky left-0 z-30 w-10 border-b bg-muted px-3 py-2'>
                  <input
                    type='checkbox'
                    aria-label={m.work_item_list_select_all()}
                    checked={allLoadedSelected}
                    indeterminate={someLoadedSelected}
                    onchange={event => toggleLoadedSelection(event.currentTarget.checked)}
                    onclick={event => event.stopPropagation()}
                  />
                </th>
              {/if}
              {#each headerGroup.headers as header (header.id)}
                {@const id = header.column.id}
                {@const left = stickyLeft(id)}
                <th
                  class='relative border-b px-3 py-2 text-left font-medium whitespace-nowrap'
                  class:sticky={left !== null}
                  class:z-20={left !== null}
                  class:bg-muted={left !== null}
                  style:width={`${header.getSize()}px`}
                  style:left={left === null ? undefined : `${left}px`}
                >
                  {label(id)}
                  <button
                    class='absolute top-0 right-0 h-full w-2 cursor-col-resize touch-none select-none hover:bg-primary/30'
                    class:bg-primary={table.getState().columnSizingInfo.isResizingColumn === id}
                    type='button'
                    aria-label={m.work_item_list_resize({ column: label(id) })}
                    onmousedown={header.getResizeHandler()}
                    ontouchstart={header.getResizeHandler()}
                    onclick={event => event.stopPropagation()}
                  ></button>
                </th>
              {/each}
            </tr>
          {/each}
        </thead>
        <tbody>
          {#each items as item (item.id)}
            <tr
              class='border-b last:border-b-0 hover:bg-muted/40'
              class:cursor-pointer={Boolean(onRowOpen)}
              tabindex={onRowOpen ? 0 : undefined}
              onclick={() => onRowOpen?.(item)}
              onkeydown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault()
                  onRowOpen?.(item)
                }
              }}
            >
              {#if onSelectionChange}
                <td class='sticky left-0 z-20 w-10 bg-background px-3 py-2 group-hover:bg-muted'>
                  <input
                    type='checkbox'
                    aria-label={m.work_item_list_select_row({ key: item.key })}
                    checked={selectedIds.includes(item.id)}
                    onchange={event => toggleRow(item.id, event.currentTarget.checked)}
                    onclick={event => event.stopPropagation()}
                  />
                </td>
              {/if}
              {#each visibleIds as id (id)}
                {@const column = table.getColumn(id)}
                {@const left = stickyLeft(id)}
                <td
                  class='truncate border-r px-3 py-2 last:border-r-0'
                  class:sticky={left !== null}
                  class:z-10={left !== null}
                  class:bg-background={left !== null}
                  style:width={`${column?.getSize() ?? columnSizing[id]}px`}
                  style:max-width={`${column?.getSize() ?? columnSizing[id]}px`}
                  style:left={left === null ? undefined : `${left}px`}
                >
                  {#if id === 'updatedAt'}
                    <span>{dateFormatter.format(new Date(item.updatedAt))}</span>
                  {:else}
                    <WorkItemCell
                      {item}
                      fieldId={id}
                      dataType={columns.find(column => column.id === id)?.dataType}
                      pending={pendingCells.has(`${item.id}:${id}`)}
                      error={cellErrors.get(`${item.id}:${id}`) ?? null}
                      {loadOptions}
                      {loadTransitions}
                      {onValueChange}
                      {onTransition}
                    />
                  {/if}
                </td>
              {/each}
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
    {#if hasNextPage || fetchingNextPage}
      <div class='flex justify-center border-t p-3'>
        <Button variant='outline' disabled={fetchingNextPage} onclick={() => onLoadMore?.()}>
          {fetchingNextPage ? m.work_item_list_loading_more() : m.work_item_list_load_more()}
        </Button>
      </div>
    {/if}
  {/if}
</section>
