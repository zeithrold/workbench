import { ApiProblemError } from '$lib/api/problem.js'
import { QueryClient } from '@tanstack/svelte-query'

export const WORKBENCH_QUERY_STALE_TIME = 30_000
export const WORKBENCH_QUERY_GC_TIME = 300_000

export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (failureCount >= 1)
    return false

  return !(error instanceof ApiProblemError) || error.status >= 500
}

export function createWorkbenchQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: WORKBENCH_QUERY_STALE_TIME,
        gcTime: WORKBENCH_QUERY_GC_TIME,
        retry: shouldRetryQuery,
        refetchOnWindowFocus: true,
      },
    },
  })
}
