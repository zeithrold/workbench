import { describe, expect, it } from 'vitest'
import { sessionFromCurrent, sessionFromLogin } from './api-model.js'

const user = { id: 'usr_1', displayName: 'Admin', primaryEmail: 'admin@example.test' }
const tenant = { id: 'ten_1', name: 'Acme', slug: 'acme' }

describe('session API mapping', () => {
  it('keeps instance-only login tenantless', () => {
    const session = sessionFromLogin({
      user,
      sessionExpiresAt: '2026-07-15T00:00:00Z',
      activeTenant: null,
      eligibleTenants: [],
    })
    expect(session.activeTenant).toBeNull()
    expect(session.tenants).toEqual([])
    expect(session.user.displayName).toBe('Admin')
  })

  it('deduplicates active and eligible tenants', () => {
    const session = sessionFromLogin({
      user,
      sessionExpiresAt: '2026-07-15T00:00:00Z',
      activeTenant: tenant,
      eligibleTenants: [tenant],
    })
    expect(session.tenants).toHaveLength(1)
  })

  it('restores admin scopes and memberships from the current session', () => {
    const session = sessionFromCurrent({
      user,
      sessionExpiresAt: '2026-07-15T00:00:00Z',
      activeTenant: tenant,
      adminScopes: ['INSTANCE'],
    }, [{ tenant }])
    expect(session.adminScopes).toEqual(['INSTANCE'])
    expect(session.activeTenant?.id).toBe('ten_1')
  })
})
