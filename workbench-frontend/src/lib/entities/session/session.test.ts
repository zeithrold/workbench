import type { Session } from './model.js'
import type { SessionGateway } from './session-gateway.js'
import { describe, expect, it, vi } from 'vitest'
import { SessionStore } from './session.svelte.js'

const restoredSession: Session = {
  user: { id: 'usr_1', displayName: 'Admin', primaryEmail: 'admin@example.test' },
  activeTenant: null,
  tenants: [],
  sessionExpiresAt: '2026-07-15T00:00:00Z',
  adminScopes: ['INSTANCE'],
  localeContext: { userPreference: null, tenantDefault: 'en-US' },
}

function gateway(): SessionGateway {
  return {
    current: vi.fn(async () => restoredSession),
    signIn: vi.fn(async () => restoredSession),
    signOut: vi.fn(async () => undefined),
    switchTenant: vi.fn(async () => restoredSession),
  }
}

describe('sessionStore', () => {
  it('restores and accepts authenticated sessions', async () => {
    const store = new SessionStore(gateway())
    await store.restore()
    expect(store.restored).toBe(true)
    expect(store.current).toEqual(restoredSession)

    const replacement = { ...restoredSession, user: { ...restoredSession.user, displayName: 'Owner' } }
    store.accept(replacement)
    expect(store.current?.user.displayName).toBe('Owner')
  })

  it('clears the session on sign out', async () => {
    const store = new SessionStore(gateway())
    store.accept(restoredSession)
    await store.signOut()
    expect(store.current).toBeNull()
    expect(store.pending).toBe(false)
  })
})
