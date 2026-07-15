import type { Session } from '$lib/entities/session/model.js'
import type { InstanceSetupGateway } from './instance-setup-gateway.js'
import { describe, expect, it, vi } from 'vitest'
import { InstanceSetupStore } from './instance-setup.svelte.js'

const session: Session = {
  user: { id: 'usr_1', displayName: 'Admin', primaryEmail: 'admin@example.test' },
  activeTenant: null,
  tenants: [],
  sessionExpiresAt: '2026-07-15T00:00:00Z',
  adminScopes: [],
  localeContext: { userPreference: null, tenantDefault: 'en-US' },
}

describe('instanceSetupStore', () => {
  it('loads status and marks the instance initialized after setup', async () => {
    const gateway: InstanceSetupGateway = {
      status: vi.fn(async () => ({ initialized: false, setupTokenRequired: true })),
      setup: vi.fn(async () => session),
    }
    const store = new InstanceSetupStore(gateway)
    await store.load()
    expect(store.current?.setupTokenRequired).toBe(true)

    await expect(store.setup({
      displayName: 'Admin',
      email: 'admin@example.test',
      password: 'secure-password-1',
    })).resolves.toBe(session)
    expect(store.current).toEqual({ initialized: true, setupTokenRequired: true })
  })
})
