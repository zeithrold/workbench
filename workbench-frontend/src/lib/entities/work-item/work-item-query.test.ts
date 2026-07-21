import { describe, expect, it } from 'vitest'
import { DEFAULT_WORK_ITEM_QUERY, workItemInfiniteQueryOptions, workItemQueryKeys } from './work-item-query.js'

describe('work item query', () => {
  it('uses a stable key without the cursor', () => {
    const input = {
      projectId: 'project-1',
      query: DEFAULT_WORK_ITEM_QUERY,
      scope: { sprint: 'current' },
      limit: 50,
    }

    expect(workItemQueryKeys.list(input)).toEqual([
      'work-items',
      'project-1',
      DEFAULT_WORK_ITEM_QUERY,
      { sprint: 'current' },
      50,
    ])
  })

  it('isolates projects and query expressions', () => {
    const first = workItemQueryKeys.list({ projectId: 'project-1', query: DEFAULT_WORK_ITEM_QUERY })
    const otherProject = workItemQueryKeys.list({ projectId: 'project-2', query: DEFAULT_WORK_ITEM_QUERY })
    const otherQuery = workItemQueryKeys.list({ projectId: 'project-1', query: { ...DEFAULT_WORK_ITEM_QUERY, filter: {} } })

    expect(first).not.toEqual(otherProject)
    expect(first).not.toEqual(otherQuery)
  })

  it('uses the page cursor only as the next page parameter', () => {
    const options = workItemInfiniteQueryOptions({ projectId: 'project-1', query: DEFAULT_WORK_ITEM_QUERY })

    expect(options.initialPageParam).toBeUndefined()
    expect(options.getNextPageParam?.({ items: [], nextCursor: 'cursor-2' }, [], undefined, [])).toBe('cursor-2')
    expect(options.getNextPageParam?.({ items: [] }, [], 'cursor-1', [])).toBeUndefined()
  })
})
