import type { ManagementNavigationGateway } from './management-navigation-gateway.js'
import type { ManagementNavigation } from './model.js'
import { describe, expect, it, vi } from 'vitest'
import { ManagementNavigationStore } from './management-navigation.svelte.js'

describe('managementNavigationStore', () => {
  it('loads once per tenant context and exposes semantic item checks', async () => {
    const gateway: ManagementNavigationGateway = {
      current: vi.fn(async (): Promise<ManagementNavigation> => ({
        items: [{ id: 'MANAGEMENT_TENANT_SETTINGS' }],
        tenantContextStatus: 'ACTIVE',
      })),
    }
    const store = new ManagementNavigationStore(gateway)

    await store.load('ten_1')
    await store.load('ten_1')

    expect(gateway.current).toHaveBeenCalledTimes(1)
    expect(store.has('MANAGEMENT_TENANT_SETTINGS')).toBe(true)
    expect(store.has('MANAGEMENT_INSTANCE_OVERVIEW')).toBe(false)
  })

  it('refreshes when the active tenant changes', async () => {
    const gateway: ManagementNavigationGateway = {
      current: vi.fn(async (): Promise<ManagementNavigation> => ({
        items: [],
        tenantContextStatus: 'ACTIVE',
      })),
    }
    const store = new ManagementNavigationStore(gateway)

    await store.load('ten_1')
    await store.load('ten_2')

    expect(gateway.current).toHaveBeenCalledTimes(2)
  })

  it('retains an explicit retryable error', async () => {
    const gateway: ManagementNavigationGateway = {
      current: vi.fn(async () => { throw new Error('Navigation unavailable') }),
    }
    const store = new ManagementNavigationStore(gateway)

    await expect(store.load(null)).rejects.toThrow('Navigation unavailable')

    expect(store.pending).toBe(false)
    expect(store.error?.message).toBe('Navigation unavailable')
  })
})
