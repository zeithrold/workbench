import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiInstanceSetupGateway } from './instance-setup-gateway.js'

afterEach(() => vi.unstubAllGlobals())

describe('apiInstanceSetupGateway', () => {
  it('loads setup status', async () => {
    const fetch = vi.fn(async () => new Response(JSON.stringify({
      initialized: false,
      setupTokenRequired: true,
    }), { status: 200 }))
    vi.stubGlobal('fetch', fetch)

    await expect(new ApiInstanceSetupGateway().status()).resolves.toEqual({
      initialized: false,
      setupTokenRequired: true,
    })
    expect(fetch).toHaveBeenCalledWith(
      '/api/instance/setup-status',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('creates a tenantless authenticated session', async () => {
    let submittedBody = ''
    const fetch = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      submittedBody = init?.body as string
      return new Response(JSON.stringify({
        user: { id: 'usr_1', displayName: 'Admin', primaryEmail: 'admin@example.test' },
        session: {
          user: { id: 'usr_1', displayName: 'Admin', primaryEmail: 'admin@example.test' },
          sessionExpiresAt: '2026-07-15T00:00:00Z',
          activeTenant: null,
          eligibleTenants: [],
        },
      }), { status: 201 })
    })
    vi.stubGlobal('fetch', fetch)

    const session = await new ApiInstanceSetupGateway().setup({
      displayName: 'Admin',
      email: 'admin@example.test',
      password: 'secure-password-1',
      setupToken: 'token',
    })
    expect(session.activeTenant).toBeNull()
    expect(JSON.parse(submittedBody)).toEqual({
      displayName: 'Admin',
      email: 'admin@example.test',
      password: 'secure-password-1',
      setupToken: 'token',
    })
  })
})
