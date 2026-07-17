import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiManagementNavigationGateway } from './management-navigation-gateway.js'

afterEach(() => vi.unstubAllGlobals())

describe('apiManagementNavigationGateway', () => {
  it('loads the session navigation resource', async () => {
    const fetch = vi.fn(async () => new Response(JSON.stringify({
      items: [{ id: 'MANAGEMENT_INSTANCE_OVERVIEW' }],
      tenantContextStatus: 'NOT_SELECTED',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetch)

    await expect(new ApiManagementNavigationGateway().current()).resolves.toEqual({
      items: [{ id: 'MANAGEMENT_INSTANCE_OVERVIEW' }],
      tenantContextStatus: 'NOT_SELECTED',
    })
    expect(fetch).toHaveBeenCalledWith(
      '/api/session/navigation',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('preserves RFC 7807 failures', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      title: 'Navigation unavailable',
      detail: 'Try again later.',
    }), { status: 503, headers: { 'Content-Type': 'application/problem+json' } })))

    await expect(new ApiManagementNavigationGateway().current()).rejects.toThrow('Try again later.')
  })
})
