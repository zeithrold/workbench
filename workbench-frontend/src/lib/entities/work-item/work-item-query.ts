import type { InfiniteData } from '@tanstack/svelte-query'
import type { WorkItemSearchInput, WorkItemSearchPage } from './model.js'
import { infiniteQueryOptions } from '@tanstack/svelte-query'
import { searchWorkItems } from './work-item-gateway.js'

export const DEFAULT_WORK_ITEM_QUERY = {
  version: 1,
  resource: 'work_item',
  sort: [],
} as const

export const workItemQueryKeys = {
  all: ['work-items'] as const,
  list(input: WorkItemSearchInput) {
    return [
      ...this.all,
      input.projectId,
      input.query,
      input.scope ?? null,
      input.limit ?? null,
    ] as const
  },
}

export function workItemInfiniteQueryOptions(input: WorkItemSearchInput) {
  return infiniteQueryOptions<WorkItemSearchPage, Error, InfiniteData<WorkItemSearchPage>, ReturnType<typeof workItemQueryKeys.list>, string | undefined>({
    queryKey: workItemQueryKeys.list(input),
    queryFn: ({ pageParam, signal }) => searchWorkItems(input, { cursor: pageParam, signal }),
    initialPageParam: undefined,
    getNextPageParam: lastPage => lastPage.nextCursor,
  })
}
