import { ApiProblemError } from '$lib/api/problem.js'
import { describe, expect, it } from 'vitest'
import {
  createWorkbenchQueryClient,
  shouldRetryQuery,
  WORKBENCH_QUERY_GC_TIME,
  WORKBENCH_QUERY_STALE_TIME,
} from './query-client.js'

describe('query client', () => {
  it('uses the application cache defaults', () => {
    const options = createWorkbenchQueryClient().getDefaultOptions().queries

    expect(options?.staleTime).toBe(WORKBENCH_QUERY_STALE_TIME)
    expect(options?.gcTime).toBe(WORKBENCH_QUERY_GC_TIME)
    expect(options?.refetchOnWindowFocus).toBe(true)
  })

  it('does not retry client errors', () => {
    expect(shouldRetryQuery(0, new ApiProblemError(403, {}))).toBe(false)
  })

  it('retries network and server failures at most once', () => {
    expect(shouldRetryQuery(0, new TypeError('network failed'))).toBe(true)
    expect(shouldRetryQuery(0, new ApiProblemError(500, {}))).toBe(true)
    expect(shouldRetryQuery(1, new ApiProblemError(500, {}))).toBe(false)
  })
})
