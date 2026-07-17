import { managementNavigation } from '$lib/entities/management/management-navigation.svelte.js'
import { session } from '$lib/entities/session/session.svelte.js'
import { render, screen } from '@testing-library/svelte'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import ManagePage from './+page.svelte'

describe('management selection page', () => {
  beforeEach(() => {
    managementNavigation.reset()
    session.accept({
      user: { id: 'usr_1', displayName: 'Admin', primaryEmail: 'admin@example.test' },
      activeTenant: { id: 'ten_1', name: 'Acme', slug: 'acme' },
      tenants: [{ id: 'ten_1', name: 'Acme', slug: 'acme' }],
      sessionExpiresAt: '2026-07-18T00:00:00Z',
      adminScopes: [],
      localeContext: { userPreference: null, tenantDefault: 'en-US' },
    })
  })

  afterEach(() => managementNavigation.reset())

  it('offers management entries returned by session navigation', () => {
    managementNavigation.accept({
      items: [
        { id: 'MANAGEMENT_INSTANCE_OVERVIEW' },
        { id: 'MANAGEMENT_TENANT_SETTINGS' },
      ],
      tenantContextStatus: 'ACTIVE',
    }, 'ten_1')
    render(ManagePage)

    expect(screen.getByRole('link', { name: /Instance management/ }).getAttribute('href')).toBe(
      '/manage/instance',
    )
    expect(screen.getByRole('link', { name: /Tenant management/ }).getAttribute('href')).toBe(
      '/manage/tenant',
    )
  })

  it('prompts for a tenant without presenting it as denied', () => {
    session.accept({ ...session.current!, activeTenant: null })
    managementNavigation.accept({
      items: [{ id: 'MANAGEMENT_INSTANCE_OVERVIEW' }],
      tenantContextStatus: 'NOT_SELECTED',
    })

    render(ManagePage)

    expect(screen.getByText('No active tenant selected')).toBeTruthy()
    expect(screen.getByRole('combobox', { name: 'Active tenant' })).toBeTruthy()
    expect(screen.queryByText('Access denied')).toBeNull()
    expect(screen.queryByRole('link', { name: /Tenant management/ })).toBeNull()
  })

  it('shows an explicit empty state when no management area is accessible', () => {
    managementNavigation.accept({ items: [], tenantContextStatus: 'ACTIVE' }, 'ten_1')

    render(ManagePage)

    expect(screen.getByRole('heading', { name: 'No accessible management areas' })).toBeTruthy()
  })
})
