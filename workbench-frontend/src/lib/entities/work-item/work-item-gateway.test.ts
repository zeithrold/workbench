import { afterEach, describe, expect, it, vi } from 'vitest'
import { searchWorkItems, WORK_ITEM_NEXT_CURSOR_HEADER } from './work-item-gateway.js'

const responseItem = {
  id: 'wi-1',
  key: 'WB-1',
  title: 'Ship the list foundation',
  projectId: 'project-1',
  issueType: { id: 'type-1', code: 'task', name: 'Task' },
  issueTypeConfigId: 'config-1',
  status: { id: 'status-1', code: 'open', name: 'Open', group: 'unstarted', terminal: false },
  priority: null,
  reporter: { id: 'user-1', displayName: 'Alex' },
  assignee: null,
  sprint: null,
  properties: {},
  createdAt: '2026-07-17T01:00:00Z',
  updatedAt: '2026-07-17T02:00:00Z',
}

afterEach(() => vi.unstubAllGlobals())

describe('work item gateway', () => {
  it('preserves the shared API boundary and parses the cursor', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([responseItem]), {
      status: 200,
      headers: { [WORK_ITEM_NEXT_CURSOR_HEADER]: 'cursor-2' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()

    const page = await searchWorkItems({
      projectId: 'project-1',
      query: { version: 1, resource: 'work_item', sort: [] },
      scope: { sprint: 'current' },
      limit: 25,
    }, { cursor: 'cursor-1', signal: controller.signal })

    expect(page).toEqual({
      items: [expect.objectContaining({ id: 'wi-1', key: 'WB-1', priority: null, assignee: null })],
      nextCursor: 'cursor-2',
    })
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/projects/project-1/work-items/search')
    expect(init.credentials).toBe('include')
    expect(init.signal).toBe(controller.signal)
    expect(new Headers(init.headers).get('X-Workbench-API-Version')).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(JSON.parse(String(init.body))).toEqual({
      query: { version: 1, resource: 'work_item', sort: [] },
      scope: { sprint: 'current' },
      limit: 25,
      cursor: 'cursor-1',
    })
  })

  it('throws the shared problem detail error for non-success responses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      title: 'Forbidden',
      detail: 'Missing project access',
    }), { status: 403 })))

    await expect(searchWorkItems({ projectId: 'project-1', query: {} }))
      .rejects
      .toEqual(expect.objectContaining({
        name: 'ApiProblemError',
        status: 403,
        message: 'Missing project access',
      }))
  })

  it('rejects malformed successful payloads at the UI boundary', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify([{ ...responseItem, id: undefined }]), {
      status: 200,
    })))

    await expect(searchWorkItems({ projectId: 'project-1', query: {} }))
      .rejects
      .toThrow('Work item response is missing id')
  })
})
