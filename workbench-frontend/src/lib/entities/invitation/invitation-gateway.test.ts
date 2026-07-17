import { afterEach, describe, expect, it, vi } from 'vitest'
import { invitationGateway } from './invitation-gateway.js'

afterEach(() => vi.unstubAllGlobals())

describe('invitationGateway', () => {
  it('encodes preview tokens and posts both acceptance modes', async () => {
    const fetch = vi.fn(async () => new Response(JSON.stringify({
      type: 'TENANT_MEMBER',
      tenant: { id: 'ten_1', name: 'Acme', slug: 'acme' },
      user: { id: 'usr_1', displayName: 'Ada' },
    }), { status: 200 }))
    vi.stubGlobal('fetch', fetch)

    await invitationGateway.preview('secret+/=')
    await invitationGateway.acceptNew('secret', 'Ada', 'SecurePassword123')
    await invitationGateway.acceptExisting('secret')

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      '/api/invitations/preview?token=secret%2B%2F%3D',
      expect.objectContaining({ credentials: 'include' }),
    )
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      '/api/invitations/accept-existing',
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    )
  })
})
