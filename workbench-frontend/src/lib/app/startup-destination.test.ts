import type { Session } from '$lib/entities/session/model.js'
import { describe, expect, it } from 'vitest'
import { startupDestination } from './startup-destination.js'

const tenantSession: Session = {
  user: { id: 'usr_1', displayName: 'Admin', primaryEmail: 'admin@example.test' },
  activeTenant: { id: 'ten_1', name: 'Acme', slug: 'acme' },
  tenants: [{ id: 'ten_1', name: 'Acme', slug: 'acme' }],
  sessionExpiresAt: '2026-07-15T00:00:00Z',
  adminScopes: [],
  localeContext: { userPreference: null, tenantDefault: 'en-US' },
}

describe('startupDestination', () => {
  it('gates an uninitialized instance to setup', () => {
    expect(startupDestination({ initialized: false, session: null, pathname: '/' })).toBe('/setup')
    expect(startupDestination({ initialized: false, session: null, pathname: '/setup' })).toBeNull()
  })

  it('gates anonymous users to login after initialization', () => {
    expect(startupDestination({ initialized: true, session: null, pathname: '/' })).toBe('/login')
    expect(startupDestination({ initialized: true, session: null, pathname: '/login' })).toBeNull()
  })

  it('keeps tenantless sessions on setup completion', () => {
    const session = { ...tenantSession, activeTenant: null }
    expect(startupDestination({ initialized: true, session, pathname: '/' })).toBe('/setup/complete')
    expect(startupDestination({ initialized: true, session, pathname: '/setup/complete' })).toBeNull()
  })

  it('routes a tenantless instance administrator to management', () => {
    const session: Session = { ...tenantSession, activeTenant: null, adminScopes: ['INSTANCE'] }
    expect(startupDestination({ initialized: true, session, pathname: '/' })).toBe('/manage/instance')
    expect(startupDestination({ initialized: true, session, pathname: '/manage/instance' })).toBeNull()
  })

  it('keeps tenant sessions in the app and leaves app routes unchanged', () => {
    expect(startupDestination({ initialized: true, session: tenantSession, pathname: '/login' })).toBe('/')
    expect(startupDestination({ initialized: true, session: tenantSession, pathname: '/' })).toBeNull()
  })
})
