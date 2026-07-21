<script lang='ts'>
  import type {
    WorkItemDisplayField,
    WorkItemDisplayFieldDefinition,
    WorkItemFieldOption,
    WorkItemListItem,
    WorkItemPatch,
    WorkItemSearchPage,
    WorkItemTransitionOption,
  } from '$lib/entities/work-item/index.js'
  import type { InfiniteData } from '@tanstack/svelte-query'
  import {
    DEFAULT_WORK_ITEM_QUERY,
    listWorkItemDisplayFields,
    listWorkItemFieldOptions,
    listWorkItemTransitions,
    patchWorkItem,
    transitionWorkItem,
    workItemInfiniteQueryOptions,
    workItemQueryKeys,
  } from '$lib/entities/work-item/index.js'
  import { createInfiniteQuery, createMutation, createQuery, useQueryClient } from '@tanstack/svelte-query'
  import { SvelteMap, SvelteSet } from 'svelte/reactivity'
  import { DEFAULT_WORK_ITEM_DISPLAY_FIELDS, propertyColumn, WORK_ITEM_COLUMNS } from './work-item-list-config.js'
  import WorkItemList from './work-item-list.svelte'

  interface Props {
    projectId: string
    query?: Record<string, unknown>
    scope?: Record<string, unknown>
    limit?: number
    displayFields?: WorkItemDisplayField[]
    onDisplayFieldsChange?: (fields: WorkItemDisplayField[]) => void
    selectedIds?: string[]
    onSelectionChange?: (ids: string[]) => void
    onRowOpen?: (item: WorkItemListItem) => void
  }

  /* eslint-disable prefer-const -- Svelte runes require mutable bindings for reactive destructured props. */
  let {
    projectId,
    query = DEFAULT_WORK_ITEM_QUERY,
    scope,
    limit,
    displayFields = DEFAULT_WORK_ITEM_DISPLAY_FIELDS,
    onDisplayFieldsChange,
    selectedIds = [],
    onSelectionChange,
    onRowOpen,
  }: Props = $props()
  /* eslint-enable prefer-const */

  const result = createInfiniteQuery(() => workItemInfiniteQueryOptions({
    projectId,
    query,
    scope,
    limit,
  }))
  const queryClient = useQueryClient()
  const listInput = $derived({ projectId, query, scope, limit })
  const fieldsResult = createQuery(() => ({
    queryKey: ['work-item-display-fields', projectId] as const,
    queryFn: () => listWorkItemDisplayFields(projectId),
  }))
  const items = $derived(result.data?.pages.flatMap(page => page.items) ?? [])
  const columns = $derived(resolveColumns(displayFields, fieldsResult.data ?? []))
  let pendingCells = $state.raw<SvelteSet<string>>(new SvelteSet())
  let cellErrors = $state.raw<SvelteMap<string, string>>(new SvelteMap())
  const queues = new SvelteMap<string, Promise<WorkItemListItem>>()
  const revisions = new SvelteMap<string, number>()

  interface MutationVariables {
    item: WorkItemListItem
    fieldId: string
    patch?: WorkItemPatch
    transitionId?: string
    optimistic?: (item: WorkItemListItem) => WorkItemListItem
  }

  interface MutationContext {
    previousItem: WorkItemListItem
    cellKey: string
    revision: number
  }

  const mutation = createMutation<WorkItemListItem, Error, MutationVariables, MutationContext>(() => ({
    mutationFn: variables => enqueue(variables.item.id, () => variables.transitionId
      ? transitionWorkItem(projectId, variables.item.id, variables.transitionId)
      : patchWorkItem(projectId, variables.item.id, variables.patch ?? {})),
    onMutate: async (variables) => {
      const cellKey = `${variables.item.id}:${variables.fieldId}`
      await queryClient.cancelQueries({ queryKey: workItemQueryKeys.list(listInput) })
      const previousItem = findCachedItem(variables.item.id) ?? variables.item
      const revision = (revisions.get(variables.item.id) ?? 0) + 1
      revisions.set(variables.item.id, revision)
      pendingCells = new SvelteSet(pendingCells).add(cellKey)
      const nextErrors = new SvelteMap(cellErrors)
      nextErrors.delete(cellKey)
      cellErrors = nextErrors
      if (variables.optimistic) {
        updateCachedItem(variables.item.id, variables.optimistic)
      }
      return { previousItem, cellKey, revision }
    },
    onSuccess: (updated, _variables, context) => {
      if (revisions.get(updated.id) === context.revision)
        updateCachedItem(updated.id, () => updated)
      void queryClient.invalidateQueries({ queryKey: ['work-item', updated.id] })
    },
    onError: (error, variables, context) => {
      if (context && revisions.get(variables.item.id) === context.revision)
        updateCachedItem(variables.item.id, () => context.previousItem)
      if (context) {
        const nextErrors = new SvelteMap(cellErrors)
        nextErrors.set(context.cellKey, error.message)
        cellErrors = nextErrors
      }
    },
    onSettled: (_data, _error, _variables, context) => {
      if (context) {
        const nextPending = new SvelteSet(pendingCells)
        nextPending.delete(context.cellKey)
        pendingCells = nextPending
      }
    },
  }))

  function loadMore() {
    if (!result.hasNextPage || result.isFetchingNextPage)
      return
    void result.fetchNextPage()
  }

  function resolveColumns(
    fields: WorkItemDisplayField[],
    catalog: WorkItemDisplayFieldDefinition[],
  ) {
    const properties = new SvelteMap(catalog
      .filter(field => field.key.startsWith('property.'))
      .map(field => [field.key, propertyColumn(field.key.slice('property.'.length), field.name, field.propertyId ?? undefined, field.dataType, field.array)]))
    for (const field of fields) {
      if (typeof field.field === 'string' || !field.field.code)
        continue
      const id = `property.${field.field.code}`
      if (!properties.has(id))
        properties.set(id, propertyColumn(field.field.code, field.field.code, field.field.apiId))
    }
    return [...WORK_ITEM_COLUMNS, ...properties.values()]
  }

  function enqueue(itemId: string, task: () => Promise<WorkItemListItem>): Promise<WorkItemListItem> {
    const previous = queues.get(itemId)
    const current = (previous ? previous.catch(() => undefined) : Promise.resolve()).then(task)
    queues.set(itemId, current)
    void current.finally(() => {
      if (queues.get(itemId) === current)
        queues.delete(itemId)
    }).catch(() => undefined)
    return current
  }

  function updateCachedItem(itemId: string, update: (item: WorkItemListItem) => WorkItemListItem) {
    queryClient.setQueryData<InfiniteData<WorkItemSearchPage>>(workItemQueryKeys.list(listInput), current => current
      ? { ...current, pages: current.pages.map(page => ({ ...page, items: page.items.map(item => item.id === itemId ? update(item) : item) })) }
      : current)
  }

  function findCachedItem(itemId: string): WorkItemListItem | undefined {
    return queryClient
      .getQueryData<InfiniteData<WorkItemSearchPage>>(workItemQueryKeys.list(listInput))
      ?.pages
      .flatMap(page => page.items)
      .find(item => item.id === itemId)
  }

  function loadOptions(item: WorkItemListItem, fieldId: string, optionQuery?: string): Promise<WorkItemFieldOption[]> {
    return listWorkItemFieldOptions(projectId, item.id, fieldId, optionQuery)
  }

  function loadTransitions(item: WorkItemListItem): Promise<WorkItemTransitionOption[]> {
    return listWorkItemTransitions(projectId, item.id)
  }

  async function changeValue(item: WorkItemListItem, fieldId: string, rawValue: unknown) {
    const option = rawValue && typeof rawValue === 'object' && 'id' in rawValue ? rawValue as WorkItemFieldOption : null
    let patch: WorkItemPatch
    let optimistic: (current: WorkItemListItem) => WorkItemListItem
    if (fieldId === 'assignee' && option) {
      patch = { assigneeId: option.id }
      optimistic = current => ({ ...current, assignee: { id: option.id, displayName: option.label } })
    }
    else if (fieldId === 'priority' && option) {
      patch = { priorityId: option.id }
      optimistic = current => ({ ...current, priority: { id: option.id, code: option.description ?? option.id, name: option.label, color: option.color, icon: option.icon } })
    }
    else if (fieldId === 'sprint') {
      patch = option ? { sprintId: option.id } : { clearSprint: true }
      optimistic = current => ({ ...current, sprint: option ? { id: option.id, name: option.label, status: option.status ?? 'planned' } : null })
    }
    else if (fieldId.startsWith('property.')) {
      const code = fieldId.slice('property.'.length)
      const value = option?.id ?? rawValue
      patch = { properties: { [code]: value } }
      optimistic = current => ({ ...current, properties: { ...current.properties, [code]: { ...current.properties[code], value, displayValue: option?.label ?? value } } })
    }
    else {
      return
    }
    await mutation.mutateAsync({ item, fieldId, patch, optimistic })
  }

  async function changeStatus(item: WorkItemListItem, transitionId: string) {
    await mutation.mutateAsync({ item, fieldId: 'status', transitionId })
  }
</script>

<WorkItemList
  {items}
  {displayFields}
  {onDisplayFieldsChange}
  {selectedIds}
  {onSelectionChange}
  {onRowOpen}
  loading={result.isPending}
  error={result.error}
  onRetry={() => void result.refetch()}
  hasNextPage={result.hasNextPage}
  fetchingNextPage={result.isFetchingNextPage}
  onLoadMore={loadMore}
  {columns}
  {pendingCells}
  {cellErrors}
  {loadOptions}
  {loadTransitions}
  onValueChange={changeValue}
  onTransition={changeStatus}
/>
