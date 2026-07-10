import type { LoginCredentials, Session, Tenant } from './model.js'
import type { SessionGateway } from './session-gateway.js'
import { apiFetch } from '$lib/api/http.js'

interface TenantSummary {
  id: string
  name: string
  slug: string
}

interface UserSummary {
  id: string
  name: string
  email: string
}

interface LoginResponse {
  user: UserSummary
  activeTenant: TenantSummary | null
  eligibleTenants: TenantSummary[]
}

interface SessionResponse {
  user: UserSummary
  activeTenant: TenantSummary | null
}

function toTenant(summary: TenantSummary): Tenant {
  return {
    id: summary.id,
    name: summary.name,
    slug: summary.slug,
  }
}

function toSession(
  user: UserSummary,
  activeTenant: TenantSummary | null,
  tenants: TenantSummary[],
): Session {
  const mappedTenants = tenants.map(toTenant)
  const active = activeTenant
    ? toTenant(activeTenant)
    : mappedTenants[0] ?? { id: 'unknown', name: 'Unknown', slug: 'unknown' }

  return {
    user: {
      name: user.name,
      email: user.email,
    },
    activeTenant: active,
    tenants: mappedTenants.length > 0 ? mappedTenants : [active],
  }
}

export class ApiSessionGateway implements SessionGateway {
  async current(): Promise<Session | null> {
    const response = await apiFetch('/api/session')
    if (response.status === 401)
      return null
    if (!response.ok)
      throw new Error(`Failed to load session (${response.status})`)

    const body = await response.json() as SessionResponse
    return toSession(body.user, body.activeTenant, body.activeTenant ? [body.activeTenant] : [])
  }

  async signIn(credentials: LoginCredentials): Promise<Session> {
    const loginMethodId = credentials.loginMethodId ?? import.meta.env.PUBLIC_E2E_LOGIN_METHOD_ID
    const tenantId = credentials.tenantId ?? import.meta.env.PUBLIC_E2E_TENANT_ID
    const password = credentials.password ?? import.meta.env.PUBLIC_E2E_PASSWORD

    if (!loginMethodId || !tenantId || !password)
      throw new Error('API session sign-in requires login method, tenant, and password configuration.')

    const response = await apiFetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        method: 'PASSWORD',
        loginMethodId,
        tenantId,
        subject: credentials.email,
        password,
      }),
    })

    if (!response.ok)
      throw new Error(`Login failed (${response.status})`)

    const body = await response.json() as LoginResponse
    return toSession(body.user, body.activeTenant, body.eligibleTenants)
  }

  async signOut(): Promise<void> {
    const response = await apiFetch('/api/auth/logout', { method: 'POST' })
    if (!response.ok && response.status !== 401)
      throw new Error(`Logout failed (${response.status})`)
  }

  async switchTenant(tenantId: string): Promise<Session> {
    const response = await apiFetch('/api/session', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tenantId }),
    })

    if (!response.ok)
      throw new Error(`Tenant switch failed (${response.status})`)

    const body = await response.json() as SessionResponse
    const current = await this.current()
    const tenantSummaries = current?.tenants.map(tenant => ({
      id: tenant.id,
      name: tenant.name,
      slug: tenant.slug,
    })) ?? (body.activeTenant ? [body.activeTenant] : [])

    return toSession(body.user, body.activeTenant, tenantSummaries)
  }
}
