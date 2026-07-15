import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiSessionGateway } from './api-session-gateway.js'

afterEach(() => vi.unstubAllGlobals())

const user = { id: 'usr_1', displayName: 'Admin', primaryEmail: 'admin@example.test' }

describe('apiSessionGateway', () => {
  it('returns null for an anonymous current session', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 401 })))
    await expect(new ApiSessionGateway().current()).resolves.toBeNull()
  })

  it('restores the session and memberships', async () => {
    const tenant = { id: 'ten_1', name: 'Acme', slug: 'acme' }
    const fetch = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        user,
        activeTenant: tenant,
        sessionExpiresAt: '2026-07-15T00:00:00Z',
        adminScopes: ['INSTANCE'],
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{ tenant }]), { status: 200 }))
    vi.stubGlobal('fetch', fetch)

    const session = await new ApiSessionGateway().current()
    expect(session?.activeTenant?.id).toBe('ten_1')
    expect(session?.adminScopes).toEqual(['INSTANCE'])
    expect(fetch).toHaveBeenCalledTimes(2)
  })

  it('submits explicit instance password credentials', async () => {
    let submittedBody = ''
    const fetch = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      submittedBody = init?.body as string
      return new Response(JSON.stringify({
        user,
        sessionExpiresAt: '2026-07-15T00:00:00Z',
        activeTenant: null,
        eligibleTenants: [],
      }), { status: 200 })
    })
    vi.stubGlobal('fetch', fetch)

    await new ApiSessionGateway().signIn({
      email: 'admin@example.test',
      password: 'secure-password-1',
      loginMethodId: 'lmg_1',
    })
    expect(JSON.parse(submittedBody)).toEqual({
      method: 'PASSWORD',
      loginMethodId: 'lmg_1',
      subject: 'admin@example.test',
      password: 'secure-password-1',
    })
  })
})
