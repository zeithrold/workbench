import { afterEach, describe, expect, it, vi } from 'vitest'
import { projectGateway } from './project-gateway.js'

afterEach(() => vi.unstubAllGlobals())

describe('projectGateway', () => {
  it('lists and creates tenant projects through the shared API client', async () => {
    const fetch = vi.fn(async () => new Response(JSON.stringify([]), { status: 200 }))
    vi.stubGlobal('fetch', fetch)

    await projectGateway.capabilities()
    await projectGateway.list()
    await projectGateway.create({ identifier: 'CORE', name: 'Core', description: 'Platform' })

    expect(fetch).toHaveBeenNthCalledWith(
      3,
      '/api/projects',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({ identifier: 'CORE', name: 'Core', description: 'Platform' }),
      }),
    )
    expect(fetch).toHaveBeenNthCalledWith(
      1,
      '/api/projects/capabilities',
      expect.objectContaining({ credentials: 'include' }),
    )
  })
})
