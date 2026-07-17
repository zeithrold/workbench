import { managementNavigation } from '$lib/entities/management/management-navigation.svelte.js'
import { session } from '$lib/entities/session/session.svelte.js'
import { render, screen } from '@testing-library/svelte'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TestHost from './management-shell-test-host.svelte'

vi.mock('$app/state', () => ({ page: { url: new URL('http://localhost/manage/tenant') } }))

describe('managementShell', () => {
  beforeEach(() => {
    session.accept({
      user: { id: 'usr_1', displayName: 'Admin', primaryEmail: 'admin@example.test' },
      activeTenant: null,
      tenants: [{ id: 'ten_1', name: 'Acme', slug: 'acme' }],
      sessionExpiresAt: '2026-07-18T00:00:00Z',
      adminScopes: [],
      localeContext: { userPreference: null, tenantDefault: null },
    })
    managementNavigation.accept({
      items: [{ id: 'MANAGEMENT_INSTANCE_OVERVIEW' }],
      tenantContextStatus: 'NOT_SELECTED',
    })
  })

  afterEach(() => {
    managementNavigation.reset()
    vi.unstubAllGlobals()
  })

  it('renders only returned management modes and blocks tenant business content without a tenant', () => {
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)

    render(TestHost)

    expect(screen.getByRole('link', { name: 'Instance' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Tenant' }).getAttribute('href')).toBe('/manage')
    expect(screen.getByText('No active tenant selected')).toBeTruthy()
    expect(screen.queryByText('Tenant business content')).toBeNull()
    expect(fetch).not.toHaveBeenCalled()
  })
})
